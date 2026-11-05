"""Validación del contrato `matica.symbolic.v1` y conversión a objetos SymPy.

Regla de seguridad central: este módulo NUNCA recibe texto matemático del estudiante y nunca usa
`sympify`, `parse_expr` ni `eval`. Construye cada objeto SymPy nodo por nodo desde una lista
cerrada de tipos permitidos, así que una entrada maliciosa no puede ejecutar código.
"""

from __future__ import annotations

import re
from typing import Any

import sympy
from sympy import Add, Eq, Function, Mul, Pow, Rational, Symbol
from sympy.core.numbers import Integer

CONTRACT_VERSION = "1"
MAX_DEPTH = 32
MAX_NODES = 256
MAX_VALUE_DIGITS = 12

VARIABLE_PATTERN = re.compile(r"^[a-z](?:[a-z0-9_]{0,7})$")
CONST_PATTERN = re.compile(r"^-?(?:\d{1,12}(?:/\d{1,12})?|0?\.\d{1,10})$")

# Lista cerrada: ampliarla es una decisión de contrato, no un detalle de implementación.
ALLOWED_FUNCTIONS = {
    "sqrt": sympy.sqrt,
    "sin": sympy.sin,
    "cos": sympy.cos,
    "tan": sympy.tan,
    "ln": sympy.ln,
    "log": sympy.log,
    "exp": sympy.exp,
    "abs": sympy.Abs,
}

NODE_KINDS = {"const", "var", "sum", "neg", "product", "quotient", "power", "equals", "func"}


class ContractError(Exception):
    """Entrada rechazada por el contrato. Lleva un código estable para el cliente."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


def _fail(code: str, message: str) -> None:
    raise ContractError(code, message)


def _check_dict(node: Any, depth: int) -> dict:
    if not isinstance(node, dict):
        _fail("node_not_object", "Cada nodo del AST debe ser un objeto JSON.")
    if depth > MAX_DEPTH:
        _fail("max_depth", f"El AST supera {MAX_DEPTH} niveles de profundidad.")
    return node


def _const_to_rational(text: str) -> Rational:
    if not CONST_PATTERN.match(text):
        _fail("const_format", "El valor de una constante no cumple el formato del contrato.")
    if "/" in text:
        numerator, denominator = text.split("/")
        return Rational(Integer(int(numerator)), Integer(int(denominator)))
    if "." in text:
        return Rational(sympy.nsimplify(Rational(text), rational=True))
    return Rational(Integer(int(text)))


def to_sympy(node: Any, *, depth: int = 0, budget: list[int] | None = None) -> Any:
    """Convierte un AST validado del contrato en una expresión SymPy."""
    if budget is None:
        budget = [0]
    budget[0] += 1
    if budget[0] > MAX_NODES:
        _fail("max_nodes", f"El AST supera {MAX_NODES} nodos.")
    # La profundidad se mide aquí y no solo en la raíz: un AST alto agotaría la pila de SymPy.
    if depth > MAX_DEPTH:
        _fail("max_depth", f"El AST supera {MAX_DEPTH} niveles de profundidad.")

    kind = node.get("kind") if isinstance(node, dict) else None
    if kind not in NODE_KINDS:
        _fail("unknown_kind", f"Tipo de nodo no permitido por el contrato: {kind!r}.")

    if kind == "const":
        value = node.get("value")
        if not isinstance(value, str):
            _fail("const_type", "Una constante debe viajar como cadena para conservar exactitud.")
        return _const_to_rational(value)

    if kind == "var":
        name = node.get("name")
        if not isinstance(name, str) or not VARIABLE_PATTERN.match(name):
            _fail("var_name", "Nombre de variable inválido: se exige identificador corto en minúsculas.")
        return Symbol(name, real=True)

    if kind == "sum":
        terms = node.get("terms")
        if not isinstance(terms, list) or not terms:
            _fail("sum_terms", "Una suma requiere una lista no vacía de términos.")
        return Add(*[to_sympy(term, depth=depth + 1, budget=budget) for term in terms])

    if kind == "product":
        factors = node.get("factors")
        if not isinstance(factors, list) or not factors:
            _fail("product_factors", "Un producto requiere una lista no vacía de factores.")
        return Mul(*[to_sympy(factor, depth=depth + 1, budget=budget) for factor in factors])

    if kind == "neg":
        return -to_sympy(node.get("operand"), depth=depth + 1, budget=budget)

    if kind == "quotient":
        numerator = to_sympy(node.get("numerator"), depth=depth + 1, budget=budget)
        denominator = to_sympy(node.get("denominator"), depth=depth + 1, budget=budget)
        return Mul(numerator, Pow(denominator, -1))

    if kind == "power":
        base = to_sympy(node.get("base"), depth=depth + 1, budget=budget)
        exponent = to_sympy(node.get("exponent"), depth=depth + 1, budget=budget)
        return Pow(base, exponent, evaluate=True)

    if kind == "equals":
        left = to_sympy(node.get("left"), depth=depth + 1, budget=budget)
        right = to_sympy(node.get("right"), depth=depth + 1, budget=budget)
        return Eq(left, right, evaluate=False)

    if kind == "func":
        name = node.get("name")
        if name not in ALLOWED_FUNCTIONS:
            _fail("func_not_allowed", f"La función {name!r} no está en la lista cerrada del contrato.")
        args = node.get("args")
        if not isinstance(args, list) or len(args) != 1:
            _fail("func_args", "Cada función del contrato recibe exactamente un argumento.")
        return Function(name)(to_sympy(args[0], depth=depth + 1, budget=budget))

    _fail("unknown_kind", f"Tipo de nodo no permitido: {kind!r}.")  # pragma: no cover


def validate_request(payload: Any) -> dict:
    """Valida el sobre de la petición y devuelve un diccionario normalizado."""
    body = _check_dict(payload, 0)
    if body.get("contractVersion") != CONTRACT_VERSION:
        _fail("contract_version", f"Este servicio atiende únicamente la versión {CONTRACT_VERSION} del contrato.")

    capability = body.get("capability")
    allowed_capabilities = {"simplify", "isEquivalent", "solveEquation", "classifyStep"}
    if capability not in allowed_capabilities:
        _fail("unknown_capability", f"Capacidad no reconocida: {capability!r}.")

    expression = body.get("expression")
    if expression is None:
        _fail("missing_expression", "Toda petición necesita un AST en 'expression'.")

    target = _check_dict(expression, 0)
    to_sympy(target)  # Se valida el AST completo antes de resolver: nada se procesa a medias.

    normalized = {"contractVersion": CONTRACT_VERSION, "capability": capability, "expression": target}
    request_id = body.get("requestId")
    if isinstance(request_id, str) and request_id:
        normalized["requestId"] = request_id[:64]

    # `isEquivalent` y `classifyStep` comparan dos operandos: se exigen los dos para que el
    # servicio nunca adivíe cuál es el lado faltante.
    if capability in {"isEquivalent", "classifyStep"}:
        raw_after = body.get("after")
        if raw_after is None:
            _fail("missing_operand", f"La capacidad {capability} compara dos operandos: falta 'after'.")
        after = _check_dict(raw_after, 0)
        to_sympy(after)
        normalized["after"] = after

    return normalized
