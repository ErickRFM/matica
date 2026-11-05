# Arquitectura Mática

Última actualización: 2026-11-05 (Bloque 1 + auditoría fusionados en `main` con la PR #1 tras el CI en verde. El Bloque 2 viaja en dos PRs todavía sin fusionar: #3 (motor de aprendizaje) y #4 (plantillas de ejercicio); la #4 contiene los commits de la #3 porque nació apilada sobre ella).

## Principio rector

Orden de prioridad fijado por el prompt maestro, y usado para resolver conflictos de diseño:

**corrección matemática → aprendizaje real → funcionamiento completo → experiencia visual → gamificación → expansión.**

## Módulos existentes

| Módulo | Tipo | Responsabilidad |
|---|---|---|
| `:apps:androidApp` | `com.android.application` + Compose | Shell Android: Activity, navegación, temas, wiring de DI. Sin lógica de dominio. |
| `:shared:math-engine` | KMP (`jvm` + `android`) | AST, parser, evaluación, racional exacto, equivalencia, transformaciones justificadas, análisis de error por paso, pistas, contrato `matica.symbolic.v1` y puerto `SymbolicGateway`. **Sin dependencias de red.** |
| `:shared:math-remote` | KMP (`jvm` + `android`) | Adaptador Ktor del puerto: `symbolicHttpClient` + `KtorSymbolicGateway`. Aquí viven las dependencias de red; el motor no las ve. |
| `:shared:learning-engine` | KMP (`jvm` + `android`) | Grafo de habilidades y prerrequisitos, nivel de dominio medible, escalonera de repaso espaciado y plan de estudio. Consume los veredictos de `:shared:math-engine`; **no calcula matemática**. |
| `:shared:exercises` | KMP (`jvm` + `android`) | Plantillas parametrizadas y generador determinista por semilla. Escribe la ecuación desde su solución y solo emite lo que `MathEngine` relee, resuelve y verifica por sustitución. |
| `tools/symbolic-service` | Python + FastAPI + SymPy | Servicio auxiliar aislado. Solo calcula lo que Kotlin no demuestra offline, sobre un AST tipado; nunca recibe texto del estudiante. |

Previsto y todavía **no creado** (se crea con código funcional, nunca vacío): `shared/core/{common,models,networking,database}`, `shared/gamification`, `shared/sync`, `shared/presentation`, `design-system`, `features/{onboarding,authentication,home,learning-path,lessons,practice,rewards,profile,classrooms}`, `backend/api`.

## Reglas de dependencias

1. `features/*` → `shared/presentation` → `shared/*` (dominio) → nada de Android.
2. Un módulo de dominio **no** importa `android.*` ni Compose. Esto es lo que hace posible iOS sin reescribir lógica; hoy se garantiza con `jvmTest` puros en el motor.
3. Las UI solo consumen `StateFlow` de ViewModel + intentos; no acceden a repositorios directamente.
4. El backend es la única fuente de verdad de progreso, recompensas y dominio verificado. El cliente puede mostrar estados optimistas, pero se reconcilian.
5. `:shared:learning-engine` depende solo de `:shared:math-engine` y de `kotlinx-datetime`; `:shared:exercises` solo de `:shared:math-engine`. Ninguno ve Ktor ni `android.*`: deciden con veredictos ya demostrados, fechas y AST propios, no con cálculos inventados.
6. La última palabra matemática siempre la tiene `MathEngine`: ni el generador de ejercicios ni el motor de aprendizaje definen si una igualdad es correcta. Ambos fallan y reportan el motivo cuando el motor no coincide.

## Modelo de datos y persistencia

- **PostgreSQL (Supabase)**: fuente de verdad. Migraciones versionadas en `backend/` (SQL ordenado, irreversible por convención, aplicadas explícitamente).
- **SQLDelight + SQLite** en el dispositivo: contenido descargado, intentos locales, cola de operaciones pendientes y horarios de repaso.
- La cola de sincronización guarda `operationId` único (UUID) por intento para que un reintento no duplique `attempts` en el servidor.
- Clave de aislamiento local: toda tabla local incluye `accountId`; cambiar de sesión no mezcla datos.

## Sincronización offline

Ciclo explícito, sin magia:

1. La lección escribe el intento en SQLite (estado `PENDING`) antes de mostrar retroalimentación.
2. `WorkManager` (Android) drena la cola cuando hay red; el orden es por `submittedAt`.
3. El servidor valida el intento (motor propio en Ktor), calcula XP/dominio y responde la versión autoritativa.
4. El cliente aplica la respuesta y marca `SYNCED`; si falla, conserva `PENDING` con contador de intentos y backoff.
5. Conflicto de racha/XP: gana el cálculo del servidor; el cliente sólo muestra.

Una lección interrumpida por cierre de app se reanuda desde el último intento persistido.

## Motor matemático (Bloque 1)

- Entrada → `Lexer` → `Parser` (descenso recursivo, admite multiplicación implícita `2x`, `2(x+3)`) → `Expr`/`Equation` tipados.
- Números como `Rational(BigInt, BigInt)`; ningún cálculo intermedio usa `Double` salvo conversión explícita y etiquetada para mostrar.
- Equivalencia: normalización canónica (expandir, recoger semejantes, reducir fracciones) y comparación estructural; **no** se aceptan muestreos aleatorios como demostración.
- Transformaciones: reglas con precondición y justificación (`add_to_both_sides`, `divide_both_sides_by` con requisito de divisor no nulo, `distribute`, `combine_like`, `simplify_fraction`).
- Análisis de error: comparar paso anterior y paso actual determina la regla faltante o mal aplicada y produce una pista sin revelar la respuesta.
- Dominio: los denominadores de la ecuación generan restricciones; una solución que viola una restricción se reporta como raíz extraña, no como solución.

## Cálculo ampliado con SymPy (contrato `matica.symbolic.v1`)

Regla de una sola fachada: la app y el backend consumen **únicamente** `MathEngine`. El motor offline
decide primero; el cálculo ampliado solo se consulta cuando offline admite no poder demostrar
(`StepVerdict.Indeterminate` o `SolveOutcome.Blocked`). Así es imposible que dos motores discutan
delante del estudiante: la respuesta remera acompaña al veredicto offline, nunca lo reemplaza.

- `MathEngine.checkStepExtended(...)` devuelve `StepAssessment(offline, remote?)`; `remote` es `null` cuando no hizo falta o no hay gateway configurado.
- El puerto `SymbolicGateway` está declarado en `:shared:math-engine`; el adaptador Ktor vive en `:shared:math-remote`. Borrar el módulo remoto no cambia una sola conclusión matemática.
- Viaje tipado en AST (`SymbolicNode`), con `contractVersion` obligatoria, tope de 256 nodos y 32 niveles. Las constantes son cadenas exactas (`-1/2`), nunca flotantes JSON.
- Prohibido por diseño: el servicio no tiene ningún endpoint que acepte texto matemático. No existe `sympify`, `parse_expr` ni `eval` sobre la entrada; el AST se construye nodo a nodo desde una lista cerrada de clases SymPy. Los tests `test_service.py` incluyen `__import__('os')...` y un `kind` desconocido como casos rechazados.
- Presupuestos: 8 s por operación en el servicio, 10 s de timeout en el cliente. Un 504 o un fallo de red se reporta como `TransportFailure`, no como veredicto.
- Límite asumido: el contrato Kotlin solo representa racionales exactos. Cuando SymPy devuelve `sqrt(2)`, el adaptador responde `NotSupported` en lugar de aproximar; una aproximación sería una mentira matemática.
- Contraste ejecutado: `EquivalenciaKotlinSymPyTest` confronta 11 pasos y 7 ecuaciones con el servicio real y deja el informe en `docs/evidence/equivalencia-kotlin-sympy-2026-11-05.txt`. Ahí quedaron registrados los desacuerdos legítimos (SymPy no opina sobre identidades y contradicciones de una sola variable) y el sobrepropósito que el contraste obligó a corregir: `together(L - R)` colapsaba `(x²−4)/(x−2)` y tapaba el agujero `x ≠ 2`.

## Motor de aprendizaje (Bloque 2)

`SkillId` es el contrato estable: 15 habilidades con `id` y etiqueta en español, declaradas en el motor
matemático porque las emiten las reglas de transformación (`RuleId.skills`) y los errores de paso.

- **Grafo de prerrequisitos** (`SkillGraph` + `DefaultCurriculum`): DAG validado al construirse. Un autorrequisito
  o un ciclo se rechazan con el camino exacto (`Cycle(path)`), nunca se limpian en silencio. El orden topológico es
  determinista (Kahn con desempate por el orden de la enumeración), así que dos construcciones del mismo plan dan la
  misma secuencia. `availableSkills(mastered)` es la frontera: nada se propone con un prerrequisito sin dominar, y
  `pathTo` explica cuántos faltan y cuáles.
- **Dominio medible** (`MasteryRules`): nivel `NOT_INTRODUCED → DEVELOPING → PROFICIENT → FLUENT` calculado sobre la
  ventana de los últimos 8 intentos. Cada cambio lleva un motivo contable ("5 sin pista, 1 con pista, 2 incorrectas"),
  y bajar de nivel usa las mismas dos evidencias incorrectas recientes que exigiría subir. No hay puntaje oculto.
- **Repaso espaciado explicable** (`ReviewLadder` + `ReviewRules`): escalonera fija de 1, 3, 7, 14 y 30 días. Se aplica
  el intervalo que la etapa actual ya ganó; un fallo devuelve a la etapa 0 y reprograma al día siguiente; un recuerdo
  con pista repite etapa; intentar dos veces el mismo día no avanza. El motivo de cada decisión se devuelve junto al
  estado.
- **`atRisk` es una señal, no una sanción**: un vencido que supera su propio intervalo aparece en el plan y baja la
  confianza, pero el nivel solo cambia con evidencia real.
- **Evidencia sin inventar nada** (`EvidenceFromVerdict`): de `StepVerdict.Valid` sale una evidencia por habilidad de
  la regla aplicada; de `Invalid`, la habilidad asociada al error concreto; de `Indeterminate` y `SyntaxProblem`,
  ninguna. Un paso que el motor no pudo demostrar no puede contar como dominio.
- **`LearningEngine`** es la fachada de esta capa: perfiles inmutables, `record`/`recordAll` y `plan(profile, today)`
  que pone los repasos vencidos antes que el trabajo nuevo.

## Plantillas de ejercicio (B2-03)

Una plantilla no elige números al azar y reza: construye la ecuación **desde** la solución pedida, con
AST tipado, y después el motor la comprueba.

- `ParameterRange` declara el dominio de cada parámetro y `validateRanges` devuelve motivos legibles; un
  parámetro ausente o fuera de rango se reporta, no se calla ni se lanza una excepción opaca.
- Notación de libro de texto: `ExerciseAst` omite el coeficiente 1 (nada de `1x`), omite el sumando
  constante nulo (nada de `x + 0`) y pone el signo en el operador del término, que es como el parser lo
  analiza y como el estudiante lo lee.
- Tres plantillas del MVP, cada una apuntando a una habilidad del grafo: `ax + b = c` (equilibrio),
  `a(x + b) = c` (distribuir) y `(x + b)/d = a` con `d ≥ 2` (fracciones). Casos degenerados se rechazan
  con nombre propio: `a = 0` anula la incógnita, `b = 0` deja un paréntesis vacío.
- `ExerciseGenerator` es determinista en `(plantilla, semilla)`, así que el backend puede rehacer el
  intento exacto que el dispositivo guardó. Usa un solo `Random` por sorteo: con uno por parámetro, dos
  parámetros del mismo rango salieran gemelos.
- Antes de emitir, el generador exige cuatro cosas al motor: relectura del texto, cero restricciones de
  dominio, solución única igual a la guardada y sustitución satisfecha. Si la plantilla mintiera sobre su
  respuesta, el resultado es `Rejected` con motivo — nunca un ejercicio con una clave falsa.

## Backend Ktor

- REST versionado (`/v1/...`), contratos con kotlinx-serialization.
- Auth: tokens de Supabase verificados **en el servidor** contra el issuer; autorización por rol y recurso antes de tocar datos.
- Entidades del prompt: `users, profiles, courses, worlds, skills, skill_prerequisites, lessons, exercise_templates, exercise_instances, attempts, mastery_states, review_schedules, rewards, streaks, classrooms, enrollments, assignments, audit_events`.
- Rate limiting, validación de entrada, idempotencia por `operationId`, registro de auditoría y manejo uniforme de errores.
- Protección de datos de menores: recolección mínima, sin identificadores sensibles en logs.

## Observabilidad y calidad

- Logs estructurados sin datos personales.
- Pruebas: `kotlin-test` para reglas y motor, Turbine para flujos de estado, pruebas Compose para UI, `ktor-server-test-host` para la API, integración PostgreSQL real cuando el daemon de Docker o una instancia remota esté disponible.
- CI: `.github/workflows/ci.yml` corre `jvmTest` y `testAndroidHostTest` de `:shared:math-engine`, `:shared:math-remote`, `:shared:learning-engine` y `:shared:exercises`, más `:apps:androidApp:assembleDebug` en `ubuntu-latest`, y publica el APK debug como artefacto. El contraste Kotlin↔SymPy **no** entra en CI: necesita el servicio Python levantado, y un verde que dependa de un motor no desplegado sería una señal falsa. Los jobs de `lint` y `backend:test` se añaden cuando exista código que cubrir; crear un paso vacío daría una señal de verde sin significado. Sin fusión a `main` con CI en rojo. Consecuencia operativa: `ci.yml` evalúa su filtro de ramas solo en `opened`, `synchronize` y `reopened`, así que **toda PR se abre contra `main`** — una PR apilada sobre otra rama de trabajo no genera ningún check y no tendría evidencia que consultar.

## Pendientes de decisión justificada

- **Álgebra simbólica propia**: se conserva el motor Kotlin para lineal, grado 2 con raíz racional, dominio y análisis de error por paso. Lo que no demuestra (`Indeterminate`/`Blocked`) se delega; no se duplica un CAS dentro del cliente. El detalle de qué se adopta de cada repositorio está en `docs/DEPENDENCIES.md`.
- **iOS**: aún no se declaran targets `iosArm64`/`iosSimulatorArm64`. Su compilación y link requieren macOS/Xcode; prometer paridad sin ese entorno sería una afirmación sin evidencia. La preparación se hace manteniendo el dominio libre de APIs de Android.
- **IA adaptativa**: el repaso espaciado arranca con reglas explícitas y medibles; no se introduce un modelo complejo antes de medir que mejora el aprendizaje.
- **Clasificaciones sociales**: deshabilitadas en MVP por tratarse de usuarios menores de edad.
