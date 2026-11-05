# Auditoría de reutilización — qué es propio y qué viene de fuera

Fecha de la auditoría: 2026-11-05. Los metadatos de repositorios (licencia, última actividad) se
consultaron el mismo día con la API de GitHub; lo demás se verificó compilando o ejecutando en este
entorno, no leyendo README ajenos.

Conclusión operativa: **no se construye un CAS desde cero en el cliente y no se migra el motor a
Python.** El motor Kotlin cubre lo que puede demostrar offline y delega el resto mediante un
contrato tipado.

## 1. Propio (sin dependencia externa de cálculo)

| Capacidad | Dónde | Por qué es nuestra |
|---|---|---|
| AST que conserva la escritura del estudiante | `shared/math-engine/…/ast/Expr.kt` | Un CAS colapsa `a - b` en `a + (-b)`; el análisis de errores necesita ver el signo explícito. |
| Parser y validación sintáctica con posición y sugerencia | `…/parser/ExprParser.kt` | El mensaje debe ser pedagógico y en español, no el error de un parser genérico. |
| Racional exacto | `…/numeric/Rational.kt` sobre `bignum` | Equivalencia simbólica exacta, sin `Double`. |
| Forma canónica y proporcionalidad | `…/poly/Normalizer.kt`, `Polynomial.kt` | Regla de decisión del motor: dos ecuaciones son equivalentes si `L − R` es proporcional por factor no nulo. |
| Restricciones de dominio | `…/domain/DomainRestrictions.kt` | Raíz extraña vs solución perdida, con sustitución de verificación. |
| Análisis de paso y atribución de error típico | `…/analysis/StepAnalyzer.kt` | Es el producto: nombra `SIGN_ERROR`, `DISTRIBUTION_ERROR`, `LOST_SOLUTION`… Ningún repositorio evaluado hace esto. |
| Pistas graduadas, verificación por sustitución, resolutor hasta grado 2 con raíz racional | `…/hints`, `…/verify`, `…/solve` | Integradas con el veredicto anterior. |
| Contrato `matica.symbolic.v1` y codec AST | `…/contract/SymbolicContract.kt` | La superficie de delegación la define el dominio, no el servicio. |

## 2. Dependencias adoptadas

| Dependencia | Versión | Usada por | Ventaja concreta |
|---|---|---|---|
| `com.ionspin.kotlin:bignum` | 0.3.10 | `:shared:math-engine` | `BigInteger` multiplataforma real: `Rational` funciona igual en `jvm` y en el target Android, y permitirá iOS sin reescribir aritmética. |
| `kotlinx-serialization-json` | 1.12.0 | motor y remoto | Serializa el AST con `classDiscriminator = "kind"`; el contrato es código generado, no JSON escrito a mano. |
| `kotlinx-coroutines-core` | 1.11.0 | `:shared:math-remote` | La fachada expone `checkStepExtended`/`solveExtended` como `suspend` sin bloquear la UI. |
| `ktor-client-core` + `content-negotiation` + `serialization-kotlinx-json` | 3.6.1 | `:shared:math-remote` | Mismo stack HTTP que el backend Ktor previsto: un solo modo de configurar clientes en el proyecto. |
| `ktor-client-okhttp` | 3.6.1 | pruebas `jvmTest` | Motor real para ejecutar el contraste contra el servicio. |
| `ktor-client-mock` | 3.6.1 | `commonTest` | 10 pruebas del adaptador sin red ni Python: el mapeo contrato → puerto se prueba offline. |
| `sympy` / `fastapi` / `uvicorn` | 1.14.0 / 0.162.0 / 0.54.0 | `tools/symbolic-service` | CAS maduro (licencia BSD) para racionalizar, resolver y comparar conjuntos solución sobre ℝ. Cero coste de mantenimiento de álgebra avanzada para nosotros. |
| `kotlin-test` + JUnit, `pytest` + `httpx` | — | pruebas | Verificación en los dos lenguajes, cada uno con su herramienta nativa. |

## 3. Evaluadas y descartadas (con evidencia)

- **kmath 0.6.0** (`SciProgCentre/kmath`). La sonda de esta auditoría resolvió y compiló
  `kmath-core` en `jvm` y en el target Android, pese a que el artefacto no publica variante
  `androidJvm`; es decir, es compatible, pero **no aporta nada que el motor no haga ya**: su
  superficie es campos numéricos, estadística y álgebra lineal. Los módulos simbólicos del repo
  (`kmath-kotlingrad`, `kmath-symja`) envuelven Symja, una librería Java para JVM, lo que rompería
  el objetivo KMP/iOS; además la API de GitHub no reporta una licencia SPDX detectable para ese
  repositorio. Decisión: **no adoptada**; la entrada del catálogo se retiró para no tentarla de nuevo.
- **SymPy Gamma** (`sympy/sympy_gamma`, último push 2024-04, licencia «Other»). Stack abandonado y
  sin licencia clara: no se importa código. Sí se reutilizan sus ideas de producto: tópicos de
  cálculo separados (`solve`, `simplify`, `derivative`) y la idea de "step by step" como capacidad
  aparte, que aquí es `classifyStep`.
- **Numbas** (`numbas/Numbas` y `numbas/editor`, Apache-2.0, activos en 2026-10). El motor de
  preguntas es JavaScript y la autoría Django; incorporarlo obligaría a un runtime JS o una WebView,
  prohibidos por el prompt maestro. Se reutilizan sus **mecanismos**, no su código: esquema de
  pregunta versionado (`matica.question.v1` propuesto), dos fases de respuesta (intentar / pedir
  ayuda), errores tipados con desplazamiento en la entrada, `ambiguity` para avisar de una
  interpretación dudosa, y `seedrandom` + `random_integer_partition` para plantillas reproducibles
  con enteros acotados.
- **`numbas/moodle`**: GPL-3.0 y fork de Moodle. Descartado por licencia y por alcance.
- **LissaJS** (`numbas/LissaJS`): sin licencia detectada y último push en 2018. Justo el motor de
  parsing/simplificación algebraica que ya existe en Kotlin; descartado.

## 4. Incompatibilidades y trampas del toolchain real

- AGP 9.5 trae Kotlin integrado: aplicar `org.jetbrains.kotlin.android` además rompe la configuración.
- El target Android de KMP necesita `com.android.kotlin.multiplatform.library`, y las pruebas JVM del
  componente Android se ejecutan en la tarea `testAndroidHostTest` (no `testDebugUnitTest`).
- `/tmp` no es visible para el Python de Windows: los ficheros de sonda viven dentro del repo y están
  ignorados.
- `python3` no existe en esta máquina; el intérprete es `python` y el entorno aislado es
  `.venv-symbolic`.
- El contrato no representa irracionales: `sympy` devuelve `sqrt(2)` y el adaptador lo declara
  `NotSupported`. Ampliar esto requiere decidir cómo se muestra y se valida una raíz irracional en la
  app, no solo añadir un campo al JSON.

## 5. Cómo volver a evaluar una dependencia

La regla que se usó aquí: sonda real antes de opinión. Añadir el artefacto a un módulo de prueba,
`./gradlew :shared:math-engine:dependencies`, compilar para `jvm` y para el target Android, y solo
después decidir. Una recomendación leída en un README no cuenta como evidencia de compatibilidad.
