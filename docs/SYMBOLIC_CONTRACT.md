# Contrato `matica.symbolic.v1` (Kotlin ↔ Python)

Versión: **1** (`SYMBOLIC_CONTRACT_VERSION = "1"`). Cualquier cambio que rompa el significado de un
campo sube la versión; el servicio rechaza todo lo que no sea exactamente su versión y el adaptador
Kotlin rechaza una respuesta de otra versión como `TransportFailure`. Los dos lados se niegan, nadie
adivina.

## Regla de seguridad central

**El viaje nunca contiene texto matemático.** El servicio no recibe la cadena escrita por el
estudiante ni ninguna otra cadena interpretable: recibe un AST construido nodo a nodo, y solo sabe
materializar objetos SymPy de una lista cerrada de `kind`. No existe endpoint que acepte una
expresión para parsearla, y en el código no hay `sympify`, `parse_expr` ni `eval` sobre la entrada.

## Nodos permitidos

| `kind` | Campos | Notas |
|---|---|---|
| `const` | `value: string` | Racional exacto: `4`, `-1/2`. Nunca notación científica ni coma flotante. |
| `var` | `name: string` | `^[a-z](?:[a-z0-9_]{0,7})$` (idéntico en Kotlin y en Python). |
| `sum` | `terms: node[]` | Sumandos; un `MINUS` del AST interno viaja como `neg`. |
| `neg` | `operand: node` | Negativo explícito del estudiante. |
| `product` | `factors: node[]` | Orden conservado. |
| `quotient` | `numerator, denominator` | `25%` se codifica como `quotient(25, 100)`. |
| `power` | `base, exponent` | |
| `equals` | `left, right` | Raíz obligatoria para `solveEquation` y `classifyStep`. |
| `func` | `name`, `args` | Lista cerrada: `sqrt, sin, cos, tan, ln, log, exp, abs`. |

Presupuestos: **256 nodos**, **32 niveles**, **8 s** de cálculo en el servicio, **10 s** de timeout
en el cliente. Un AST que no cabe se declara `SymbolicEncoding.Unsupported` en Kotlin y ni siquiera
sale a la red.

## Capacidades

| `capability` | Entrada | Salida relevante |
|---|---|---|
| `simplify` | `expression` | `expression`, `expanded`, `variables` |
| `isEquivalent` | `expression` + `after` | `equivalent`, `difference`, `domainExcluded` |
| `solveEquation` | `expression` (raíz `equals`) | `supported`, `variable`, `solutions`, `rejected`, `excludedValues`, `verification` |
| `classifyStep` | `expression` + `after` (ambos `equals`) | `equivalent`, `lostSolutions`, `extraneousSolutions`, `domainExcluded`, `method` |

Estados del sobre: `ok`, `unsupported` (no juzga), `invalidInput` (violación del contrato, HTTP 422),
`timeout` (HTTP 504). `unsupported` **no** es un error del estudiante: significa que el servicio
tampoco puede demostrar nada.

## Intercambio real verificado

Petición `classifyStep` para `2x + 3 = 7 → 2x - 4 = 0`:

```json
{
  "contractVersion": "1",
  "requestId": "doc-1",
  "capability": "classifyStep",
  "expression": {"kind": "equals",
    "left": {"kind": "sum", "terms": [
      {"kind": "product", "factors": [{"kind": "const", "value": "2"}, {"kind": "var", "name": "x"}]},
      {"kind": "const", "value": "3"}]},
    "right": {"kind": "const", "value": "7"}},
  "after": {"kind": "equals",
    "left": {"kind": "sum", "terms": [
      {"kind": "product", "factors": [{"kind": "const", "value": "2"}, {"kind": "var", "name": "x"}]},
      {"kind": "neg", "operand": {"kind": "const", "value": "4"}}]},
    "right": {"kind": "const", "value": "0"}}
}
```

Respuesta:

```json
{"contractVersion":"1","requestId":"doc-1","capability":"classifyStep","status":"ok",
 "result":{"supported":true,"equivalent":true,"lostSolutions":[],"extraneousSolutions":[],
           "domainExcluded":[],"method":"forma_canonica_identica"},
 "warnings":[],"engine":{"name":"sympy","version":"1.14.0"}}
```

Rechazo de un `kind` fuera de la lista (HTTP 422):

```json
{"contractVersion":"1","requestId":"","capability":"","status":"invalidInput",
 "result":{"code":"unknown_kind","message":"Tipo de nodo no permitido por el contrato: 'sympify'."},
 "warnings":[],"engine":{"name":"sympy","version":"1.14.0"}}
```

Códigos de rechazo producidos por el servicio: `contract_version`, `unknown_capability`,
`missing_expression`, `missing_operand`, `node_not_object`, `unknown_kind`, `const_type`,
`const_format`, `var_name`, `func_not_allowed`, `func_args`, `sum_terms`, `product_factors`,
`max_depth`, `max_nodes`, `not_an_equation`.

## Mapeo al puerto Kotlin

`SymbolicGateway` (`:shared:math-engine`) define dos operaciones; `KtorSymbolicGateway`
(`:shared:math-remote`) las implementa:

| Respuesta del servicio | Valor del puerto |
|---|---|
| `equivalent: true` | `RemoteStep.Equivalent(method, domainExcluded)` |
| `equivalent: false` | `RemoteStep.NotEquivalent(lost, extraneous, domainExcluded, method)` |
| `status: unsupported` | `RemoteStep.NotSupported(reason)` |
| `invalidInput`, `timeout`, red caída, versión distinta | `RemoteStep.TransportFailure(reason)` |
| `solveEquation` con `supported: true` | `RemoteSolutions.Resolved(variable, solutions, rejected, excludedValues)` |

Un valor que no sea racional exacto (`sqrt(2)`, `1 + sqrt(3)`) se traduce a `NotSupported`: el
contrato Kotlin no representa irracionales y aproximarlos sería inventar precisión.

## Límites conocidos (medidos, no supuestos)

- SymPy no opina de líneas que se quedan sin incógnita (`3x + 2 = 3x + 5 → 2 = 5`): `unsupported`.
- Conjuntos solución no enumerables (identidades con hueco, intervalos): `unsupported`.
- Raíces irracionales: el servicio las calcula, el contrato no las representa.
- El cálculo huérfano tras un 504 sigue corriendo en su hilo; aislarlo de verdad exige un worker
  supervisado, no este endpoint.


## Despliegue independiente en Render (B4 provisional)

El servicio SymPy es un **auxiliar interno** y no sustituye Ktor ni PostgreSQL.
Se arranca desde la raíz del repositorio con:

```sh
pip install -r tools/symbolic-service/requirements.txt
cd tools/symbolic-service
uvicorn app:app --host 0.0.0.0 --port "${PORT:-10000}"
```

Configuración requerida en Render (secreto, NUNCA en git ni en la APK):
`MATICA_SYMBOLIC_API_KEY` de alta entropía. Sin el secreto el endpoint retorna 503.
La petición `POST /v1/symbolic` exige `X-Matica-Service-Key`; la ausencia o error da 401.
`GET /healthz` no requiere secreto y solo comprueba la salud/versión.
Cuerpos mayores de 64 KiB son rechazados (413). El servicio sigue siendo una POC:
los timeouts de SymPy usan un hilo que no se mata físicamente; mantener detrás de Ktor,
no permitir acceso directo desde Android, aplicar límites de red y no tratarlo
como motor público de cálculo sin protección adicional.
