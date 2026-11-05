# Arquitectura Mática

Última actualización: 2026-11-04 (Bloque 0 completado, Bloque 1 en curso).

## Principio rector

Orden de prioridad fijado por el prompt maestro, y usado para resolver conflictos de diseño:

**corrección matemática → aprendizaje real → funcionamiento completo → experiencia visual → gamificación → expansión.**

## Módulos existentes

| Módulo | Tipo | Responsabilidad |
|---|---|---|
| `:apps:androidApp` | `com.android.application` + Compose | Shell Android: Activity, navegación, temas, wiring de DI. Sin lógica de dominio. |
| `:shared:math-engine` | KMP (`jvm` + `android`) | AST, parser, evaluación, racional exacto, equivalencia, transformaciones justificadas, análisis de error por paso, pistas. |

Previsto y todavía **no creado** (se crea con código funcional, nunca vacío): `shared/core/{common,models,networking,database}`, `shared/learning-engine`, `shared/exercises`, `shared/gamification`, `shared/sync`, `shared/presentation`, `design-system`, `features/{onboarding,authentication,home,learning-path,lessons,practice,rewards,profile,classrooms}`, `backend/api`.

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

## Backend Ktor

- REST versionado (`/v1/...`), contratos con kotlinx-serialization.
- Auth: tokens de Supabase verificados **en el servidor** contra el issuer; autorización por rol y recurso antes de tocar datos.
- Entidades del prompt: `users, profiles, courses, worlds, skills, skill_prerequisites, lessons, exercise_templates, exercise_instances, attempts, mastery_states, review_schedules, rewards, streaks, classrooms, enrollments, assignments, audit_events`.
- Rate limiting, validación de entrada, idempotencia por `operationId`, registro de auditoría y manejo uniforme de errores.
- Protección de datos de menores: recolección mínima, sin identificadores sensibles en logs.

## Observabilidad y calidad

- Logs estructurados sin datos personales.
- Pruebas: `kotlin-test` para reglas y motor, Turbine para flujos de estado, pruebas Compose para UI, `ktor-server-test-host` para la API, integración PostgreSQL real cuando el daemon de Docker o una instancia remota esté disponible.
- CI: GitHub Actions con jobs de `lint`, `test`, `assembleDebug` y `backend:test`; sin fusión a `main` con CI en rojo.

## Pendientes de decisión justificada

- **iOS**: aún no se declaran targets `iosArm64`/`iosSimulatorArm64`. Su compilación y link requieren macOS/Xcode; prometer paridad sin ese entorno sería una afirmación sin evidencia. La preparación se hace manteniendo el dominio libre de APIs de Android.
- **IA adaptativa**: el repaso espaciado arranca con reglas explícitas y medibles; no se introduce un modelo complejo antes de medir que mejora el aprendizaje.
- **Clasificaciones sociales**: deshabilitadas en MVP por tratarse de usuarios menores de edad.
