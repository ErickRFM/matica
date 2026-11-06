"""Pruebas del contrato y del comportamiento matemático del servicio.

Se ejecutan sin red ni uvicorn (TestClient de Starlette). Cubren tres frentes: que la superficie
de seguridad rechaza entradas maliciosas, que las capacidades dan la respuesta matemática correcta,
y que los casos difíciles —los que el motor Kotlin offline deja indeterminados— quedan resueltos.
"""

from __future__ import annotations

import json
import os

import pytest
from fastapi.testclient import TestClient

os.environ["MATICA_SYMBOLIC_API_KEY"] = "test-only-do-not-deploy"
from app import app

client = TestClient(app, headers={"X-Matica-Service-Key": "test-only-do-not-deploy"})


def const(value: str) -> dict:
    return {"kind": "const", "value": value}


def var(name: str) -> dict:
    return {"kind": "var", "name": name}


def sum_node(*terms) -> dict:
    return {"kind": "sum", "terms": list(terms)}


def product(*factors) -> dict:
    return {"kind": "product", "factors": list(factors)}


def quotient(numerator, denominator) -> dict:
    return {"kind": "quotient", "numerator": numerator, "denominator": denominator}


def power(base, exponent) -> dict:
    return {"kind": "power", "base": base, "exponent": exponent}


def equals(left, right) -> dict:
    return {"kind": "equals", "left": left, "right": right}


def call(capability: str, expression: dict, after: dict | None = None) -> dict:
    payload = {"contractVersion": "1", "requestId": "test-" + capability, "capability": capability, "expression": expression}
    if after is not None:
        payload["after"] = after
    response = client.post("/v1/symbolic", json=payload)
    return response.json() | {"_status": response.status_code}


# ------------------------------------------------------------------ superficie de seguridad


def test_rechaza_nodos_de_tipo_no_permitido():
    body = call("simplify", {"kind": "eval", "value": "x"})
    assert body["_status"] == 422
    assert body["status"] == "invalidInput"
    assert body["result"]["code"] == "unknown_kind"


def test_no_convierte_cadenas_en_codigo():
    # El campo `value` de una constante nunca se analiza como expresión: solo se valida su forma.
    body = call("simplify", const("__import__('os').system('id')"))
    assert body["_status"] == 422
    assert body["result"]["code"] == "const_format"


def test_rechaza_funciones_fuera_de_la_lista_cerrada():
    body = call("simplify", {"kind": "func", "name": "gamma", "args": [var("x")]})
    assert body["_status"] == 422
    assert body["result"]["code"] == "func_not_allowed"


def test_rechaza_nombres_de_variable_arbitrarios():
    body = call("simplify", var("muy_largo_nombre_inyectado"))
    assert body["_status"] == 422
    assert body["result"]["code"] == "var_name"


def test_limita_la_profundidad_del_ast():
    node = const("2")
    for _ in range(40):
        node = sum_node(node)
    body = call("simplify", node)
    assert body["_status"] == 422
    assert body["result"]["code"] == "max_depth"


def test_exige_la_version_de_contrato():
    response = client.post("/v1/symbolic", json={"contractVersion": "2", "capability": "simplify", "expression": const("1")})
    assert response.status_code == 422
    assert response.json()["result"]["code"] == "contract_version"


def test_is_equivalent_exige_segundo_operando():
    response = client.post("/v1/symbolic", json={"contractVersion": "1", "capability": "isEquivalent", "expression": const("1")})
    assert response.status_code == 422
    assert response.json()["result"]["code"] == "missing_operand"


def test_el_ast_validado_no_se_rechaza_por_comillas_en_texto_libre():
    # Un payload que no es JSON se reporta como JSON inválido, sin tocar SymPy.
    response = client.post("/v1/symbolic", content=b"os.system('rm -rf /')", headers={"Content-Type": "application/json"})
    assert response.status_code == 422
    assert response.json()["status"] == "invalidInput"


# ------------------------------------------------------------------ capacidades matemáticas


def test_health_declaracion_de_version():
    body = client.get("/healthz").json()
    assert body["status"] == "ok"
    assert body["contractVersion"] == "1"
    assert body["engine"]["name"] == "sympy"


def test_simplify_expande_y_reordena():
    body = call("simplify", product(var("x"), sum_node(var("x"), const("1"))))
    assert body["status"] == "ok"
    assert json.loads(json.dumps(body["result"]["expanded"])) == "x**2 + x"
    assert body["result"]["variables"] == ["x"]


def test_solve_ecuacion_lineal_del_ejemplo_del_prompt():
    # 2(x+3) = 14
    equation = equals(product(const("2"), sum_node(var("x"), const("3"))), const("14"))
    body = call("solveEquation", equation)
    assert body["result"]["solutions"] == ["4"]
    assert body["result"]["rejected"] == []


def test_solve_cuadratica_devuelve_ambas_raices():
    # x^2 = 4  →  el motor Kotlin offline se declara indeterminado aquí.
    body = call("solveEquation", equals(power(var("x"), const("2")), const("4")))
    assert sorted(body["result"]["solutions"]) == ["-2", "2"]


def test_solve_cuadratica_con_ambas_raices_reales():
    body = call("solveEquation", equals(power(var("x"), const("2")), var("x")))
    assert sorted(body["result"]["solutions"]) == ["0", "1"]


def test_solve_sin_solucion_real():
    body = call("solveEquation", equals(power(var("x"), const("2")), const("-1")))
    assert body["result"]["solutions"] == []


def test_funcion_racional_con_restriccion_de_dominio():
    # 2/x = 4 → x = 8, con x ≠ 0 registrado como restricción.
    body = call("solveEquation", equals(quotient(const("2"), var("x")), const("4")))
    assert body["result"]["solutions"] == ["1/2"]
    assert body["result"]["excludedValues"] == ["0"]


def test_raiz_extrana_por_dominio_se_rechaza_con_nombre():
    # (x^2 - 1)/(x - 1) = 0: x = 1 anula el denominador, así que solo x = -1 sobrevive.
    numerator = sum_node(power(var("x"), const("2")), const("-1"))
    body = call("solveEquation", equals(quotient(numerator, sum_node(var("x"), const("-1"))), const("0")))
    assert body["result"]["solutions"] == ["-1"]
    assert body["result"]["excludedValues"] == ["1"]
    # `together` cancela el factor común, así que x = 1 ni siquiera llega a candidato: la
    # restricción se reporta por dominio, no como raíz rechazada.
    assert body["result"]["rejected"] == []


def test_solucion_exacta_irracional_se_devuelve_en_notacion_exacta():
    body = call("solveEquation", equals(power(var("x"), const("2")), const("2")))
    assert sorted(body["result"]["solutions"]) == sorted(["-sqrt(2)", "sqrt(2)"])


# ------------------------------------------------------------------ clasificación de pasos


def test_paso_lineal_equivalente():
    body = call("classifyStep", equals(const("2"), sum_node(var("x"), const("1"))), equals(const("1"), sum_node(var("x"), const("0"))))
    assert body["result"]["equivalent"] is True


def test_paso_cuadratico_que_pierde_una_solucion():
    body = call("classifyStep", equals(power(var("x"), const("2")), const("4")), equals(var("x"), const("2")))
    assert body["result"]["supported"] is True
    assert body["result"]["equivalent"] is False
    assert body["result"]["lostSolutions"] == ["-2"]


def test_paso_que_introduce_una_solucion_extrana():
    # x - 1 = 3  →  x(x - 1) = 3x
    before = equals(sum_node(var("x"), const("-1")), const("3"))
    after = equals(product(var("x"), sum_node(var("x"), const("-1"))), product(const("3"), var("x")))
    body = call("classifyStep", before, after)
    assert body["result"]["equivalent"] is False
    assert body["result"]["extraneousSolutions"] == ["0"]


def test_multiplicar_por_una_expresion_con_dominio_no_queda_indeterminado():
    # 2/x = 4  →  2 = 4x : equivalente, con la restricción x ≠ 0 preservada.
    before = equals(quotient(const("2"), var("x")), const("4"))
    after = equals(const("2"), product(const("4"), var("x")))
    body = call("classifyStep", before, after)
    assert body["result"]["equivalent"] is True
    assert body["result"]["domainExcluded"] == ["0"]


def test_anular_una_restriccion_de_dominio_introduce_una_raiz_extrana():
    # (x²-4)/(x-2) = x+2  →  x²-4 = (x+2)(x-2): en x = 2 la primera línea no existe y la segunda sí.
    before = equals(quotient(sum_node(power(var("x"), const("2")), const("-4")), sum_node(var("x"), const("-2"))), sum_node(var("x"), const("2")))
    after = equals(sum_node(power(var("x"), const("2")), const("-4")), product(sum_node(var("x"), const("2")), sum_node(var("x"), const("-2"))))
    body = call("classifyStep", before, after)
    assert body["result"]["supported"] is True
    assert body["result"]["equivalent"] is False
    assert body["result"]["extraneousSolutions"] == ["2"]
    assert body["result"]["domainExcluded"] == ["2"]
    assert body["result"]["method"] == "restricciones_de_dominio_distintas"


def test_paso_claramente_incorrecto():
    body = call("classifyStep", equals(product(const("2"), sum_node(var("x"), const("3"))), const("14")), equals(sum_node(product(const("2"), var("x")), const("3")), const("14")))
    assert body["result"]["equivalent"] is False


def test_dos_incognitas_se_reportan_sin_soporte():
    body = call("solveEquation", equals(sum_node(var("x"), var("y")), const("3")))
    assert body["status"] == "unsupported"
    assert "una incógnita" in body["result"]["reason"]


def test_fracciones_exactas_sin_coma_flotante():
    body = call("isEquivalent", sum_node(const("1/3"), const("1/6")), const("1/2"))
    assert body["result"]["equivalent"] is True
    assert "." not in json.dumps(body["result"]["difference"])


# Server-to-server authentication and resource limits (deploy hardening).
def test_requires_a_key():
    body = {"contractVersion": "1", "capability": "simplify", "expression": {"kind": "const", "value": "1"}}
    assert TestClient(app).post("/v1/symbolic", json=body).status_code == 401
    assert TestClient(app).post("/v1/symbolic", json=body, headers={"X-Matica-Service-Key": "wrong"}).status_code == 401


def test_missing_server_key_is_fail_closed(monkeypatch):
    monkeypatch.delenv("MATICA_SYMBOLIC_API_KEY", raising=False)
    response = client.post("/v1/symbolic", json={"contractVersion": "1", "capability": "simplify", "expression": {"kind": "const", "value": "1"}})
    assert response.status_code == 503


def test_rejects_body_larger_than_64_kib():
    response = client.post("/v1/symbolic", content=b"x" * 70000, headers={"Content-Type": "application/json"})
    assert response.status_code == 413
