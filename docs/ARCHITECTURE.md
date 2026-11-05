# Arquitectura Mática

Última actualización: 2026-11-05 (Bloque 1 + auditoría de reutilización, fusionados en `main` con la PR #1 tras el CI en verde; el contraste Kotlin↔SymPy se ejecutó contra el servicio real. El Bloque 2 viaja en las PRs #3 y #4, sin fusionar. Esta rama abre el Bloque 3 con `:design-system`, partiendo de `main` y sin depender de esas dos PRs).

## Principio rector

Orden de prioridad fijado por el prompt maestro, y usado para resolver conflictos de diseño:

**corrección matemática → aprendizaje real → funcionamiento completo → experiencia visual → gamificación → expansión.**

## Módulos existentes

| Módulo | Tipo | Responsabilidad |
|---|---|---|
| `:apps:androidApp` | `com.android.application` + Compose | Shell Android: Activity, navegación, temas, wiring de DI. Sin lógica de dominio. |
| `:shared:math-engine` | KMP (`jvm` + `android`) | AST, parser, evaluación, racional exacto, equivalencia, transformaciones justificadas, análisis de error por paso, pistas, contrato `matica.symbolic.v1` y puerto `SymbolicGateway`. **Sin dependencias de red.** |
| `:shared:math-remote` | KMP (`jvm` + `android`) | Adaptador Ktor del puerto: `symbolicHttpClient` + `KtorSymbolicGateway`. Aquí viven las dependencias de red; el motor no las ve. |
| `:design-system` | KMP (`jvm` + `android`) con Compose | Paleta cruda, contraste WCAG 2.2 medible, escala tipográfica, espaciado de 4 dp, radios y los dos `ColorScheme` + colores semánticos. No conoce pantallas ni dominio. |
| `tools/symbolic-service` | Python + FastAPI + SymPy | Servicio auxiliar aislado. Solo calcula lo que Kotlin no demuestra offline, sobre un AST tipado; nunca recibe texto del estudiante. |

Previsto y todavía **no creado** (se crea con código funcional, nunca vacío): `shared/core/{common,models,networking,database}`, `shared/learning-engine`, `shared/exercises`, `shared/gamification`, `shared/sync`, `shared/presentation`, `features/{onboarding,authentication,home,learning-path,lessons,practice,rewards,profile,classrooms}`, `backend/api`.

## Reglas de dependencias

1. `features/*` → `shared/presentation` → `shared/*` (dominio) → nada de Android.
2. Un módulo de dominio **no** importa `android.*` ni Compose. Esto es lo que hace posible iOS sin reescribir lógica; hoy se garantiza con `jvmTest` puros en el motor.
3. Las UI solo consumen `StateFlow` de ViewModel + intentos; no acceden a repositorios directamente.
4. El backend es la única fuente de verdad de progreso, recompensas y dominio verificado. El cliente puede mostrar estados optimistas, pero se reconcilian.

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

## Sistema visual (B3-01)

- `:design-system` es un módulo KMP (`jvm` + `android`) con Compose. La combinación
  `com.android.kotlin.multiplatform.library` + `org.jetbrains.compose` se validó compilando
  `compileKotlinJvm` y `compileAndroidMain` antes de escribir una sola línea de tokens, y la app
  lo consume en el APK sin duplicar clases de Compose.
- Cuatro capas, en este orden de abajo hacia arriba: `MaticaPalette` (color crudo),
  `MaticaSemanticColors` (roles propios que Material 3 no modela: acierto, refuerzo, ruta,
  ecuación), `MaticaLightColorScheme` / `MaticaDarkColorScheme` (roles Material 3) y `MaticaTheme`
  (el único punto de entrada de las pantallas).
- **El contraste es una medición, no una intención.** `Contrast` implementa WCAG 2.2 (luminancia
  relativa y razón) en `commonMain`, y `ThemeContrast.kt` lista los 17 pares texto-sobre-fondo
  que el tema expone. `ThemeTokensTest` exige AA (4.5:1) sobre los 17, en `jvmTest` y en
  `testAndroidHostTest`. Las cifras están ancladas contra una implementación independiente
  escrita en Python sobre los mismos hex: si una de las dos se mueve, la prueba falla.
- Consecuencia real de medir: el violeta del diseño (`#9075EF`) **no** admite texto blanco
  (3.51:1). Por eso el rol `secondary` del tema claro usa tinta oscura sobre tinte claro, y la
  prueba `elVioletaDelDisenoNoAguantaTextoBlanco` deja documentado el motivo en el propio test.
- `MaticaTheme` pasa siempre `colorScheme` explícito: el valor por defecto de Material 3 en
  Android activa Material You y sustituiría la paleta medida por colores dinámicos del sistema.
- Procedencia de cada token, sin fingir precisión que no tuvimos: los colores crudos y el
  comportamiento general salen del render del Figma (`docs/evidence/figma-inspeccion-2026-11-05.md`);
  los radios, la escala tipográfica y el espaciado son derivación propia, porque el archivo se
  abrió en modo lectura anónimo y Dev Mode exige sesión. El tema oscuro no existe en el diseño.
- La familia tipográfica es `FontFamily.Default`: el repo no trae binarios de fuente y bajarlas
  rompería el build sin red. Cambiarla es una decisión de diseño, no de arquitectura.

## Backend Ktor

- REST versionado (`/v1/...`), contratos con kotlinx-serialization.
- Auth: tokens de Supabase verificados **en el servidor** contra el issuer; autorización por rol y recurso antes de tocar datos.
- Entidades del prompt: `users, profiles, courses, worlds, skills, skill_prerequisites, lessons, exercise_templates, exercise_instances, attempts, mastery_states, review_schedules, rewards, streaks, classrooms, enrollments, assignments, audit_events`.
- Rate limiting, validación de entrada, idempotencia por `operationId`, registro de auditoría y manejo uniforme de errores.
- Protección de datos de menores: recolección mínima, sin identificadores sensibles en logs.

## Observabilidad y calidad

- Logs estructurados sin datos personales.
- Pruebas: `kotlin-test` para reglas y motor, Turbine para flujos de estado, pruebas Compose para UI, `ktor-server-test-host` para la API, integración PostgreSQL real cuando el daemon de Docker o una instancia remota esté disponible.
- **Instrumentación Compose (`MaticaThemeRenderTest`)**: tres casos que capturan el dibujo con `captureToImage()` y comparan el píxel renderizado contra `colorScheme.surface` y `colorScheme.background`. No entran en CI: el runner de GitHub no tiene dispositivo y un paso que no instala el APK de prueba devuelve `BUILD SUCCESSFUL` con `tests="0"`. Se ejecutan contra el POCO X7 Pro (Android 16) o, cuando HyperOS bloquea la instalación del APK de prueba, contra el emulador. La evidencia se lee por XML de resultados, nunca por el código de salida.
- CI: `.github/workflows/ci.yml` corre `:shared:math-engine:jvmTest`, `:shared:math-remote:jvmTest`, `:design-system:jvmTest`, los `testAndroidHostTest` de los tres módulos y `:apps:androidApp:assembleDebug` en `ubuntu-latest`, y publica el APK debug como artefacto. El contraste Kotlin↔SymPy **no** entra en CI: necesita el servicio Python levantado, y un verde que dependa de un motor no desplegado sería una señal falsa. Los jobs de `lint` y `backend:test` se añaden cuando exista código que cubrir; crear un paso vacío daría una señal de verde sin significado. Sin fusión a `main` con CI en rojo.

## Pendientes de decisión justificada

- **Álgebra simbólica propia**: se conserva el motor Kotlin para lineal, grado 2 con raíz racional, dominio y análisis de error por paso. Lo que no demuestra (`Indeterminate`/`Blocked`) se delega; no se duplica un CAS dentro del cliente. El detalle de qué se adopta de cada repositorio está en `docs/DEPENDENCIES.md`.
- **iOS**: aún no se declaran targets `iosArm64`/`iosSimulatorArm64`. Su compilación y link requieren macOS/Xcode; prometer paridad sin ese entorno sería una afirmación sin evidencia. La preparación se hace manteniendo el dominio libre de APIs de Android.
- **IA adaptativa**: el repaso espaciado arranca con reglas explícitas y medibles; no se introduce un modelo complejo antes de medir que mejora el aprendizaje.
- **Clasificaciones sociales**: deshabilitadas en MVP por tratarse de usuarios menores de edad.
