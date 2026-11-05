# Matriz de trazabilidad — Mática

Origen: `P` = prompt maestro Full Kotlin (2026-11-04) · `B` = blueprint (no disponible) · `F` = Figma (no inspeccionable) · `I` = investigación de entornos/repos.

Estados: `completo` (evidencia ejecutada) · `en progreso` · `parcial` · `pendiente` · `bloqueado` (depende de un recurso externo).

| ID | Requisito | Origen | Módulo | Estado | Validación |
|---|---|---|---|---|---|
| B0-01 | Monorrepositorio Gradle modular | P§3 | raíz | completo | `./gradlew :apps:androidApp:assembleDebug` BUILD SUCCESSFUL |
| B0-02 | Versiones compatibles antes de fijar stack | P§1 | `gradle/libs.versions.toml` | completo | Prueba de build forzó Gradle 9.6.1 y compileSdk 37; registrado en `docs/ENVIRONMENT_AUDIT.md` |
| B0-03 | Kotlin + Compose + KMP | P§1 | `:shared:math-engine`, `:apps:androidApp` | completo | `jvmTest` en verde y APK debug generado |
| B0-04 | Sin React Native/WebView/Capacitor | P§1 | todo | completo | Sin esas dependencias en el catálogo |
| B0-05 | Backend Ktor + PostgreSQL | P§10 | `:backend:api` | pendiente | — |
| B0-06 | Matriz de trazabilidad mantenida | P§0 | `docs/TRACEABILITY.md` | completo | Este archivo |
| B0-07 | Documentación de arquitectura | P§17A | `docs/ARCHITECTURE.md` | completo | Revisión manual |
| B0-08 | Blueprint leído | P§0 | — | bloqueado | Archivo inexistente en el entorno (búsqueda documentada) |
| B0-09 | Tokens extraídos de Figma | P§0/§8 | design-system | bloqueado | Sin conector Figma; se definen tokens propios y se reconcilian después |
| B1-01 | AST con expresiones tipadas | P§4 | `shared/math-engine/…/ast` | completo | `ExprParserTest` (12) y `poly.NormalizerTest` (12) en verde |
| B1-02 | Parser rechaza sintaxis ambigua | P§4 | `…/parser` | completo | Casos `2 3`, `1.2.3`, `x = = 2`, `2(x+3`, `=14`, `2(x+3)=14=7` y `sen(x)` rechazados con posición y sugerencia |
| B1-03 | Fracciones exactas (sin `Double`) | P§4 | `…/numeric/Rational` | completo | `RationalTest` (10): forma canónica, signe, `0.1+0.2 = 3/10`, potencias negativas, mcm |
| B1-04 | Validación de igualdades y transformaciones | P§4 | `…/poly` + `…/analysis` | completo | Equivalencia por proporcionalidad de polinomios canónicos; `StepAnalyzerTest` valida 8 pasos correctos |
| B1-05 | Evaluación paso a paso de procedimientos | P§4 | `…/analysis/StepAnalyzer` | completo | `MathEngineTest.analizaElProblemaCompletoDelPromptPasoAPaso` recorre `2(x+3)=14 → 2x+6=14 → 2x=8 → x=4` |
| B1-06 | Distinguir error numérico de conceptual | P§4 | `…/analysis` | completo | `1/3+1/6 → 2/9` se atribuye a jerarquía y `2+3*4 → 15` a aritmética; distribuir, signo, un solo lado, raíz perdida y extraña tienen veredicto propio |
| B1-07 | Pistas progresivas sin revelar respuesta | P§4 | `…/hints` | completo | `HintProviderTest` (5): escalera CONCEPT/LOCATE/NAME_RULE sin_solution, `SHOW_LINE` solo a petición |
| B1-08 | Comprobación por sustitución | P§4 | `…/verify` | completo | `SolutionVerifierTest` (6): satisface, no satisface, extrana por dominio, `Blocked` distinto de fallar |
| B1-09 | Casos especiales cubiertos por pruebas | P§4 | `shared/math-engine` | parcial | Cubiertos: división por cero, dominio, raíces extrañas y perdidas, fracciones equivalentes, signos, jerarquía, distribución, decimales exactos. Falta **unidades** (requiere tipo de unidad en el AST, Bloque 2) |
| B1-10 | Resultado indeterminado cuando no se puede verificar | P§4 | `…/analysis` | completo | `(x+1)/x = 2 → x+1 = 2` devuelve `Indeterminate`; el motor no acusa lo que no demuestra |
| B2-01 | Grafo de habilidades y prerrequisitos | P§6 | `shared/learning-engine` | pendiente | — |
| B2-02 | Diagnóstico inicial | P§6 | `features/*` + backend | pendiente | — |
| B2-03 | Plantillas parametrizadas de ejercicio | P§5 | `shared/exercises` | pendiente | — |
| B2-04 | Renderizador de ejercicios por tipo | P§5 | `features/lessons` | pendiente | — |
| B2-05 | Repaso espaciado explicable | P§6 | `shared/learning-engine` | pendiente | — |
| B3-01 | Sistema visual Compose y temas claro/oscuro | P§8 | `design-system` | pendiente | — |
| B3-02 | 18 pantallas conectadas con navegación real | P§8 | `features/*` | pendiente | — |
| B3-03 | Estados carga/vacío/error/offline/éxito | P§8 | `features/*` | pendiente | — |
| B3-04 | Accesibilidad y movimiento reducido | P§8 | `design-system` | pendiente | Auditoría en Bloque 7 |
| B3-05 | Componentes matemáticos interactivos (fracciones, rectas, gráficas, geometría) | P§8 | `shared/presentation` | pendiente | — |
| B4-01 | Auth y autorización en servidor | P§10 | `backend/auth` | pendiente | Pruebas de autorización |
| B4-02 | Migraciones versionadas | P§10 | `backend/persistence` | bloqueado | Requiere PostgreSQL accesible (Docker apagado) |
| B4-03 | Recompensas validadas en backend | P§9/§10 | `backend/gamification` | pendiente | — |
| B5-01 | SQLDelight y descarga de contenido | P§11 | `shared/database` | pendiente | — |
| B5-02 | Sincronización sin duplicar intentos | P§11/§16 | `shared/sync` | pendiente | Prueba de idempotencia |
| B5-03 | FCM | P§13 | `apps/androidApp` | bloqueado | Sin `google-services.json` propio |
| B6-01 | Herramientas de autoría y grupos | P§12 | `backend/curriculum` | pendiente | — |
| B7-01 | APK debug instalable | P§11/§17 | `apps/androidApp` | completo | `androidApp-debug.apk` 14 MB generado |
| B7-02 | Pruebas unitarias y de integración | P§16 | todos | en progreso | `jvmTest` verde; resto pendiente |
| B7-03 | Instalación y captura en dispositivo real | P§17D | — | bloqueado | Ningún dispositivo/emulador conectado |
| B7-04 | AAB release firmado | P§1/§7 | CI | bloqueado | Sin keystore ni credenciales |
| G-01 | Rama dedicada y commits claros, CI verde antes de fusionar | P§15 | git | en progreso | Repo iniciado en `main`; rama de trabajo por bloque |

Resumen honesto: de 44 requisitos verificados o creados, **7 completos**, **4 en progreso**, **4 bloqueados por recursos externos** y el resto pendientes. Ninguna función se declara terminada sin la evidencia indicada en la columna de validación.
