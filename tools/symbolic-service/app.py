"""Servicio auxiliar de cálculo simbólico para Mática.

Papel acotado: resuelve lo que el motor Kotlin offline honestamente no puede demostrar
(funciones racionales, cuadráticas y de grado superior, funciones trascendentes). No sustituye
al motor, no califica al estudiante por sí solo y no conoce cuentas de usuario.

Arranque local:  uvicorn app:app --port 8099
"""

from __future__ import annotations

import asyncio
import time

import sympy
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from contract import CONTRACT_VERSION, ContractError, to_sympy, validate_request

app = FastAPI(title="Mática symbolic service", version=CONTRACT_VERSION, docs_url=None, redoc_url=None)

TIMEOUT_SECONDS = 8.0

# Contadores en memoria: suficiente para una POC; en producción esto vive en métricas del gateway.
_counters = {"ok": 0, "invalid": 0, "unsupported": 0, "timeout": 0}


def _envelope(request_id: str, capability: str, status: str, result: dict | None = None, warnings: list[str] | None = None) -> dict:
    return {
        "contractVersion": CONTRACT_VERSION,
        "requestId": request_id,
        "capability": capability,
        "status": status,
        "result": result,
        "warnings": warnings or [],
        "engine": {"name": "sympy", "version": sympy.__version__},
    }


def _render(value) -> str | None:
    """Cadena exacta de un número real. `None` si no es un número real exacto comparable."""
    number = sympy.nsimplify(value)
    if not number.is_real:
        return None
    return str(sympy.simplify(number))


def _variable_name(expression) -> list[str]:
    return sorted(str(symbol) for symbol in getattr(expression, "free_symbols", set()))


def _blocked_values(expression, variable) -> list:
    """Objetos que anulan el denominador de una expresión."""
    _, denominator = sympy.fraction(sympy.together(expression))
    if denominator == sympy.Integer(1):
        return []
    blocked = sympy.solveset(sympy.Eq(denominator, 0), variable, domain=sympy.S.Reals)
    if isinstance(blocked, (sympy.ConditionSet, sympy.Complement)) or blocked is sympy.S.Reals:
        return []
    return list(blocked)


def _line_exclusion(equality) -> tuple[sympy.Symbol | None, dict[str, object]]:
    """
    Restricción de dominio de una línea, leída de sus dos lados antes de cualquier cancelación.

    La forma canónica pierde los agujeros: `together((x²-4)/(x-2) - (x+2))` puede colapsar a 0 y
    con eso borrar la condición x ≠ 2. La restricción se toma de la igualdad escrita, no de `L - R`.
    """
    names = sorted(set(_variable_name(equality.lhs) + _variable_name(equality.rhs)))
    if len(names) != 1:
        return None, {}
    variable = sympy.Symbol(names[0], real=True)
    blocked = _blocked_values(equality.lhs, variable) + _blocked_values(equality.rhs, variable)
    tokens: dict[str, object] = {}
    for value in blocked:
        token = _render(value)
        if token is not None:
            tokens[token] = value
    return variable, tokens


def _satisfies(equality, variable, token: str) -> bool:
    """Sustitución exacta de un valor que el propio servicio calculó; nunca de texto del usuario."""
    try:
        value = sympy.Rational(token)
    except (TypeError, ValueError, ZeroDivisionError):
        return False
    return sympy.simplify(equality.lhs.subs(variable, value) - equality.rhs.subs(variable, value)) == 0


def _solve_values(expression, variable) -> list | None:
    """Raíces reales como objetos SymPy. `None` = conjunto no enumerable (no es "cero raíces")."""
    solutions = sympy.solveset(sympy.Eq(sympy.together(expression), 0), variable, domain=sympy.S.Reals)
    if solutions is sympy.S.EmptySet:
        return []
    if isinstance(solutions, (sympy.ConditionSet, sympy.Complement, sympy.Interval)) or solutions is sympy.S.Reals:
        return None
    if not getattr(solutions, "is_FiniteSet", False):
        return None
    values = list(solutions)
    return None if any(_render(value) is None for value in values) else values


def _simplify(payload: dict) -> dict:
    expression = to_sympy(payload["expression"])
    return {
        "expression": str(sympy.simplify(expression)),
        "expanded": str(sympy.expand(expression)),
        "variables": _variable_name(expression),
    }


def _is_equivalent(payload: dict) -> dict:
    left = to_sympy(payload["expression"])
    right = to_sympy(payload["after"])

    warnings: list[str] = []
    difference = sympy.cancel(left - right)
    equivalent = difference == 0
    if not equivalent and right != 0:
        ratio = sympy.cancel(left / right)
        if ratio.is_number and ratio != 0:
            equivalent = True
            warnings.append("las expresiones difieren por un factor constante no nulo")
    return {
        "equivalent": bool(equivalent),
        "difference": str(difference),
        "domainExcluded": sorted(_line_exclusion(sympy.Eq(left, right, evaluate=False))[1]),
        "warnings": warnings,
    }


def _solve_equation(payload: dict) -> dict:
    equality = to_sympy(payload["expression"])
    if not isinstance(equality, sympy.Equality):
        raise ContractError("not_an_equation", "solveEquation exige un AST raíz de tipo 'equals'.")

    expression = sympy.together(equality.lhs - equality.rhs)
    variables = _variable_name(expression)
    if len(variables) != 1:
        return {"supported": False, "reason": f"Se requiere exactamente una incógnita; se recibieron {len(variables)}.", "variables": variables}

    variable = sympy.Symbol(variables[0], real=True)
    excluded = sorted(_line_exclusion(equality)[1])
    candidates = _solve_values(expression, variable)
    if candidates is None:
        return {"supported": False, "reason": "El conjunto solución no es enumerable sobre los reales.", "excludedValues": excluded}

    verified: list[str] = []
    rejected: list[dict] = []
    difference = equality.lhs - equality.rhs
    for value in candidates:
        rendered = _render(value)
        if rendered in excluded:
            rejected.append({"value": rendered, "reason": "viola una restricción de dominio"})
            continue
        residue = sympy.simplify(difference.subs(variable, value))
        if residue == 0:
            verified.append(rendered)
        else:
            rejected.append({"value": rendered, "reason": "no satisface la ecuación original"})

    return {
        "supported": True,
        "variable": variables[0],
        "solutions": verified,
        "rejected": rejected,
        "excludedValues": excluded,
        "verification": "sustitucion_en_la_ecuacion_original",
    }


def _classify_step(payload: dict) -> dict:
    before = to_sympy(payload["expression"])
    after = to_sympy(payload["after"])
    if not (isinstance(before, sympy.Equality) and isinstance(after, sympy.Equality)):
        raise ContractError("not_an_equation", "classifyStep compara dos igualdades.")

    before_difference = sympy.together(before.lhs - before.rhs)
    after_difference = sympy.together(after.lhs - after.rhs)
    variables = _variable_name(before_difference) + _variable_name(after_difference)
    if len(set(variables)) != 1:
        return {"supported": False, "reason": "La comparación de pasos requiere una sola incógnita en las dos líneas."}

    variable = sympy.Symbol(variables[0], real=True)
    before_variable, before_blocked = _line_exclusion(before)
    after_variable, after_blocked = _line_exclusion(after)

    # Dominios distintos: hay que juzgarlos antes de la forma canónica, que precisamente pierde
    # los agujeros al cancelar. Un valor admitido por una línea y prohibido por la otra solo cuenta
    # como raíz perdida o extraña si de verdad satisface a esa línea.
    if set(before_blocked) != set(after_blocked):
        lost = sorted(token for token in set(after_blocked) - set(before_blocked) if before_variable is not None and _satisfies(before, before_variable, token))
        extraneous = sorted(token for token in set(before_blocked) - set(after_blocked) if after_variable is not None and _satisfies(after, after_variable, token))
        return {
            "supported": True,
            "equivalent": not lost and not extraneous,
            "lostSolutions": lost,
            "extraneousSolutions": extraneous,
            "domainExcluded": sorted(before_blocked),
            "method": "restricciones_de_dominio_distintas",
        }

    excluded = sorted(before_blocked)
    if sympy.cancel(before_difference - after_difference) == 0:
        return {"supported": True, "equivalent": True, "lostSolutions": [], "extraneousSolutions": [], "domainExcluded": excluded, "method": "forma_canonica_identica"}

    before_values = _solve_values(before_difference, variable)
    after_values = _solve_values(after_difference, variable)
    if before_values is None or after_values is None:
        return {
            "supported": False,
            "reason": "Alguna línea no tiene un conjunto solución enumerable sobre los reales.",
            "excludedValues": excluded,
        }

    before_set = {token for token in map(_render, before_values) if token not in excluded}
    after_set = {token for token in map(_render, after_values) if token not in excluded}
    lost = sorted(before_set - after_set)
    extraneous = sorted(after_set - before_set)

    return {
        "supported": True,
        "equivalent": not lost and not extraneous,
        "lostSolutions": lost,
        "extraneousSolutions": extraneous,
        "domainExcluded": excluded,
        "method": "conjuntos_de_solucion_sobre_reales",
    }


_HANDLERS = {
    "simplify": _simplify,
    "isEquivalent": _is_equivalent,
    "solveEquation": _solve_equation,
    "classifyStep": _classify_step,
}


@app.get("/healthz")
async def healthz() -> dict:
    return {"status": "ok", "contractVersion": CONTRACT_VERSION, "engine": {"name": "sympy", "version": sympy.__version__}, "counters": dict(_counters)}


@app.post("/v1/symbolic")
async def symbolic(request: Request) -> JSONResponse:
    try:
        payload = validate_request(await request.json())
    except (ValueError, TypeError, ContractError) as error:
        _counters["invalid"] += 1
        code = error.code if isinstance(error, ContractError) else "invalid_json"
        return JSONResponse(_envelope("", "", "invalidInput", {"code": code, "message": str(error)}), status_code=422)

    request_id = payload.get("requestId", "")
    capability = payload["capability"]
    started = time.monotonic()
    try:
        result = await asyncio.wait_for(run_in_threadpool(_HANDLERS[capability], payload), TIMEOUT_SECONDS)
    except asyncio.TimeoutError:
        # El cálculo sigue corriendo en el hilo: el contrato devuelve 504 y la operación huérfana se
        # agota con el proceso. Aislarla de verdad exige un worker supervisado, no este endpoint.
        _counters["timeout"] += 1
        return JSONResponse(_envelope(request_id, capability, "timeout", {"reason": f"La operación superó {TIMEOUT_SECONDS}s."}), status_code=504)
    except ContractError as error:
        _counters["invalid"] += 1
        return JSONResponse(_envelope(request_id, capability, "invalidInput", {"code": error.code, "message": str(error)}), status_code=422)
    except (sympy.SympifyError, RecursionError, NotImplementedError, TypeError, ValueError, ZeroDivisionError) as error:
        _counters["unsupported"] += 1
        return JSONResponse(_envelope(request_id, capability, "unsupported", {"reason": f"Sin soporte para esta expresión ({type(error).__name__})."}))

    status = "unsupported" if result.get("supported") is False else "ok"
    _counters["ok" if status == "ok" else "unsupported"] += 1
    body = _envelope(request_id, capability, status, result)
    if time.monotonic() - started > TIMEOUT_SECONDS / 2:
        body["warnings"].append("cerca del presupuesto de tiempo")
    return JSONResponse(body)


@app.exception_handler(Exception)
async def unexpected(request: Request, error: Exception) -> JSONResponse:
    # Sin trazas ni fragmentos del AST en la respuesta: el registro no debe contener datos del estudiante.
    _counters["unsupported"] += 1
    return JSONResponse(_envelope("", "", "unsupported", {"reason": f"Fallo interno de {type(error).__name__}"}), status_code=500)
