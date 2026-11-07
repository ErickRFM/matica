# Arquitectura Mática

Última actualización: 2026-11-05 (Bloque 1 fusionado con la PR #1 y Bloque 2 con las PRs #3 y #4, ambas con el CI en verde antes del merge; el contraste Kotlin↔SymPy se ejecutó contra el servicio real. Esta rama abre el Bloque 3 con `:design-system` sobre un `main` que ya incluye el motor de aprendizaje y las plantillas de ejercicio).

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
| `:design-system` | KMP (`jvm` + `android`) con Compose | Paleta cruda, contraste WCAG 2.2 medible, escala tipográfica, espaciado de 4 dp, radios y los dos `ColorScheme` + colores semánticos. No conoce pantallas ni dominio. |
| `:shared:presentation` | KMP (`jvm` + `android`) con Compose | Las 18 rutas y sus pantallas, el navegador con pila serializable, el estado vivo de la sesión (`MaticaSession`), el renderizador de ejercicios y los componentes matemáticos interactivos. Consume las fachadas; **no decide matemática ni dominio**. |
| `tools/symbolic-service` | Python + FastAPI + SymPy | Servicio auxiliar aislado. Solo calcula lo que Kotlin no demuestra offline, sobre un AST tipado; nunca recibe texto del estudiante. |
| `:backend:api` | Ktor + PostgreSQL (JDBC) | Cuentas propias (Argon2id + JWT), intentos idempotentes, aula con códigos de invitación, tokens de dispositivo y proxy SymPy. Migraciones Flyway al arrancar. Sólo se compila con `-Pmatica.backendOnly=true`. |

`apps/androidApp` aporta además la capa de datos del teléfono: cliente Ktor, token en preferencias privadas, cola SQLDelight y `SyncWorker` sobre WorkManager.

Previsto y todavía **no creado** (se crea con código funcional, nunca vacío): `shared/core/{common,models,networking,database}`, `shared/gamification`, `shared/sync`, `features/{onboarding,authentication,home,learning-path,lessons,practice,rewards,profile,classrooms}`. La cola y el cliente viven hoy dentro de `:apps:androidApp`; extraerlos a `shared/*` es mecánico cuando exista un segundo cliente.

## Desviación documentada: una sola capa de presentación

El plano inicial repartía la UI en `features/*` (una pantalla por módulo) sobre `androidx.navigation`. En la práctica se ha agrupado todo en `:shared:presentation` con una pila propia (`MaticaNavigator` + `MaticaRoute`), y la decisión tiene un motivo verificable:

- **Navegación demostrable sin dispositivo.** `androidx.navigation` sólo existe en Android, así que «back desde la hoja de pistas regresa al reproductor» o «la pila sobrevive a la muerte del proceso» no podían entrar en `commonTest`. Con la pila serializada a `List<String>` las 18 rutas y sus transiciones se prueban en JVM y en el host Android.
- **El mismo estado corre en prueba y en producción.** `MaticaSession` es una clase con `mutableStateOf`, no un `ViewModel`: las pruebas invocan exactamente las mismas funciones que los botones de las pantallas, así que una prueba verde significa que el botón hace algo. El plan original (`StateFlow` de ViewModel + intentos) no se ha adoptado: con backend ya presente, la sesión sigue siendo la unidad de estado y un ViewModel sólo añadiría una capa que hoy no resuelve ningún caso real. Se replanteará cuando exista descarga de contenido con caché y varias fuentes de datos que coordinar.
- **Coste real.** `androidx.navigation.compose` y Koin siguen declarados en `:apps:androidApp` para el deep-link y el wiring futuro; `MainActivity` sólo monta el host, el `BackHandler` y nada de lógica.

Cuando aparezca código que justifique separar módulos (equpos distintos, compilación incremental cara), la división es mecánica: cada carpeta de `presentation/app` ya es un `feature` candidato.


## Reglas de dependencias

1. `features/*` → `shared/presentation` → `shared/*` (dominio) → nada de Android.
2. Un módulo de dominio **no** importa `android.*` ni Compose. Esto es lo que hace posible iOS sin reescribir lógica; hoy se garantiza con `jvmTest` puros en el motor.
3. Las pantallas sólo hablan con `MaticaSession` (estado + acciones) y con la fachada
   `MaticaAccountGateway`; la implementación Android (Ktor, SQLite, WorkManager) se inyecta desde
   `MainActivity` y nunca se importa desde `commonMain`.
4. El backend es la única fuente de verdad de progreso, recompensas y dominio verificado. El cliente puede mostrar estados optimistas, pero se reconcilian.
5. `:shared:learning-engine` depende solo de `:shared:math-engine` y de `kotlinx-datetime`; `:shared:exercises` solo de `:shared:math-engine`. Ninguno ve Ktor ni `android.*`: deciden con veredictos ya demostrados, fechas y AST propios, no con cálculos inventados.
6. La última palabra matemática siempre la tiene `MathEngine`: ni el generador de ejercicios ni el motor de aprendizaje definen si una igualdad es correcta. Ambos fallan y reportan el motivo cuando el motor no coincide.

## Modelo de datos y persistencia

- **PostgreSQL propio en Render**: fuente de verdad de cuentas, intentos y grupos. No se usa
  Supabase ni claves `anon`/`service_role`: la identidad la emite el backend y la base sólo acepta
  conexiones desde el servicio, así que el aislamiento es por `user_id` en cada consulta y no por
  políticas de fila de un tercero.
- **Migraciones Flyway** en `backend/api/src/main/resources/db/migration`, aplicadas al arrancar
  (`MATICA_MIGRATE_ON_START=false` sólo para una emergencia). Son SQL ordenado y aditivo.
- **SQLDelight + SQLite** en el dispositivo: cola de intentos pendientes (`pending_attempts`).
  El currículo sigue empaquetado en la app; no hay descarga de contenido todavía.
- La cola guarda el `id` UUID que genera el dominio antes de escribir: el servidor deduplica por ese
  id (`ON CONFLICT DO NOTHING`), así que reintentar nunca duplica evidencia.
- Clave de aislamiento local: la cola lleva `user_id`; cambiar de cuenta no mezcla ni atribuye
  intentos de otra persona.

## Sincronización offline

Ciclo explícito, implementado en esta tanda:

1. Con sesión, el paso se escribe en `pending_attempts` **antes** de continuar; sin cuenta no hay
   cola y el progreso vive sólo en el teléfono.
2. `WorkManager` drena la cola cuando hay red (`SyncWorker`, lotes de 25, orden de llegada).
3. El servidor valida formato, habilidad, veredicto y fecha, y guarda el intento por su UUID:
   201 creado, 200 duplicado.
4. 2xx o 4xx permanente (dato inválido, id ajeno) borran la fila; 5xx o red dejan la fila y piden
   `retry` con retroceso exponencial; 401 cierra la sesión y conserva la cola para el próximo login.
5. XP, racha y dominio se siguen calculando en el teléfono: el servidor todavía **no** calcula ni
   devuelve la versión autoritativa, y por eso no se presenta como tal.

Una lección interrumpida por cierre de app se reanuda desde el último intento persistido.

## Motor matemático (Bloque 1)

- Entrada → `Lexer` → `Parser` (descenso recursivo, admite multiplicación implícita `2x`, `2(x+3)`) → `Expr`/`Equation` tipados.
- Números como `Rational(BigInt, BigInt)`; ningún cálculo intermedio usa `Double` salvo conversión explícita y etiquetada para mostrar.
- Equivalencia: normalización canónica (expandir, recoger semejantes, reducir fracciones) y comparación estructural; **no** se aceptan muestreos aleatorios como demostración.
- Transformaciones: reglas con precondición y justificación (`add_to_both_sides`, `divide_both_sides_by` con requisito de divisor no nulo, `distribute`, `combine_like`, `simplify_fraction`).
- Análisis de error: comparar paso anterior y paso actual determina la regla faltante o mal aplicada y produce una pista sin revelar la respuesta.
- Dominio: los denominadores de la ecuación generan restricciones; una solución que viola una restricción se reporta como raíz extraña, no como solución.
- Unidades (`math/units`): `Expr.Quantity` envuelve al **término completo**, así `1/2 m` es medio metro y no `1/(2 m)`. La tabla es cerrada (24 símbolos, cuatro familias) y el factor a la unidad base es `Rational`, nunca `Double`. `DimensionalCheck` corre antes del evaluador **y** antes del normalizador, porque ambos reducen todo a números: sin ese paso, `3 km + 2 s` daría `3002` y saldría una equivalencia falsa con aire de haber sido verificada. Un número o una incógnita sin unidad se leen en la unidad base de la familia presente — es la convención que deja verificar `3 km + x = 7 km → x = 4000` por sustitución.
- Fuera de la tabla a propósito: unidades con desplazamiento (°C no es multiplicativa), día y año (colisionan con incógnitas escritas como `d`/`a`), y unidades elevadas o compuestas (`m²`, `km/h`), que se rechazan con mensaje en lugar de devolver un número con la familia equivocada.

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

### Convenciones de la capa de pantalla (`:shared:presentation`)

Reglas que nacieron de defectos medidos en el dispositivo el 2026-11-06, no de preferencias.
Cada una tiene su prueba o su captura en `docs/evidence/ui-espaciado-animacion-navegacion-2026-11-06.md`;
las de marca, arranque y login están en `docs/evidence/splash-login-2026-11-06.md`.

- **Un componente del sistema aplica siempre el `modifier` que le pasa el llamador, y lo aplica
  primero.** `MaticaButton` construía su propio ancho desde cero e ignoraba el `modifier`, así que el
  `weight(1f)` de las filas de opciones se perdía: los cuatro botones se repartían el ancho por su
  contenido y la última etiqueta se escribía una letra por renglón a 392,72 dp. La cadena correcta es
  `modifier.then(widthModifier).heightIn(...)...`; posponer el `modifier` del llamador vuelve a
  producir el mismo fallo.
- **Dentro de una `MaticaCard` la variante es `OUTLINE`, nunca `NEUTRAL`.** El relleno del neutro en
  claro es `BlueMist`, y `BlueMist` es también el `surfaceVariant` con el que `MaticaCard` se pinta:
  el botón queda sin fondo ni borde sobre su propia tarjeta. `OUTLINE` existe precisamente para ese
  fondo («borde sobre tarjeta»). Es una colisión de tokens, no un mal uso del color: si se añade otra
  variante cuyo fondo coincida con `surfaceVariant`, el mismo defecto reaparece.
- **Las rutas modales se dibujan como hoja opaca anclada abajo, no encima de la pantalla base.**
  `HintSheet` ocupa el 78 % inferior sobre una `Surface` con el radio del sistema; la franja de arriba
  sigue siendo scrim tocable. Pintar la ruta transparente sobre la base superpone las dos cabeceras en
  la misma coordenada y deja ver el contenido de abajo a través de los huecos de las tarjetas.
- **Una fila de cuatro etiquetas no cabe a 392 dp: se parte en dos filas de dos.** Cada pastilla
  necesita ~103 dp (etiqueta + 24 de relleno por lado) y el teléfono deja ~90 por celda. Lo mismo con
  tres opciones largas («2.º de secundaria» pide ~150 dp contra ~116 disponibles). La altura máxima por
  etiqueta (≤ 56 dp) la sostiene `MaticaLayoutMetricsTest`.
- **El párrafo va en `bodyLarge` y el titular en `titleMedium`.** `strategyEs` medía dos líneas con
  peso de titular por encima de la propia ecuación; el peso tipográfico se usa para jerarquía, no para
  énfasis decorativo.
- **Toda ruta alcanzable tiene que tener una entrada en la UI.** `MaticaRoute.Profile` se probaba
  montando la ruta a mano y convivía verde con la ausencia total de acceso, lo que hacía inalcanzable
  el reinicio del perfil local. `MaticaScreenInventoryTest` prueba destinos; la accesibilidad del
  destino la prueba un camino recorrido desde Hoy (`desdeHoySePuedeAbrirElPerfilLocal`).
- **La aparición de las barras del sistema la decide el tema efectivo de la app, no el del sistema.**
  `enableEdgeToEdge()` sin argumentos deja `SystemBarStyle.auto`, que sigue el ajuste del sistema: con
  el móvil en claro y Mática en «Oscuro» los iconos se pintaban para fondo claro sobre `#0E1626` y
  desaparecían. `MaticaTheme` llama a `MaticaSystemBarAppearance(darkTheme)` — `expect`/`actual` como
  `platformMotionScale`, con el `actual` de JVM vacío — y escribe los dos flags del
  `WindowInsetsControllerCompat` desde un `SideEffect`. Por eso `:design-system` tiene una dependencia
  de `androidx.core` **sólo** en `androidMain`: si alguien vuelve a dejar el control en `auto`, la
  prueba que lo mide es `MaticaWindowInsetsTest.elTemaOscuroDeLaAppTambienCambiaLaAparicionDeLasBarrasDelSistema`.
- **La marca se dibuja, no se importa.** `MaticaMark` es un `Canvas` con la «M» como ruta y un punto de
  llegada; su geometría vive en `maticaMarkSegments`/`maticaMarkPoint` (funciones puras) y el trazo
  progresivo se prueba sin pantalla. El splash y el encabezado del login montan el mismo componente,
  así que un cambio de paleta o de grosor no deja dos versiones de la marca desincronizadas. La única
  pieza fuera de Compose es el icono de lanzador (`mipmap-anydpi-v26` + vectores), que el sistema
  exige como recurso: sus colores repiten `#426BEB`/`#0E1626` a mano y hay que tocarlos si cambian los
  tokens.
- **El splash no finge una carga: es una capa opaca sobre la app ya compuesta.** No hay petición que
  esperar, así que sólo aparece en arranque frío (`savedInstanceState == null`), la app se compone
  debajo desde el primer frame y termina sola en ~1,1 s. El fondo se mezcla con `lerp` y no con
  `copy(alpha)`: un color translúcido deja ver la pantalla de abajo y el arranque se lee como una capa
  sucia. Un único `Animatable` alimenta trazo, mota, rótulo y barra (`splashPhases`), lo que evita
  relojes paralelos al cambiar la escala de movimiento; `rememberSaveable` guarda que ya terminó, no
  que empezó, así que rotar o cambiar de tema no lo repite.
- **El login tiene dos cuerpos según el APK traiga servidor.** Con `MATICA_API_BASE_URL` la pantalla es
  un formulario real (correo, contraseña, alta de cuenta) y la píldora de estado dice si hay sesión; sin
  URL la app lo declara («Cuentas no disponibles en esta versión») y ofrece «Practicar sin cuenta» en
  lugar de dibujar dos campos muertos. El título es `heading`, `elLoginDeclaraQueEstaCompilacionNoTieneCuentas`
  fija el encabezado semántico, la declaración y la ausencia de campos, y `MaticaSplashUiTest` fija que
  el splash anuncie progreso y termine sin entrada del usuario. El aula aplica la misma regla: sin
  sesión dice si falta red o falta cuenta, y nunca muestra un código que nadie va a validar.

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
- **Instrumentación Compose**: diez clases (`MaticaThemeRenderTest`, `MaticaAppUiTest`, `MaticaSplashUiTest`, `MaticaScreenInventoryTest`, `MaticaNavigatorRestoreTest`, `MaticaStateViewsTest`, `MaticaMotionTest`, `MaticaMathComponentsTest`, `MaticaWindowInsetsTest`, `MaticaLayoutMetricsTest`) con 45 casos ejecutados el 2026-11-06 sobre `companero_v12_qa_20261009(AVD) - 11` (`tests="45" failures="0" errors="0"`), incluidas las tres capturas con `captureToImage()` que comparan el píxel renderizado contra `colorScheme.surface` y `colorScheme.background`, las tres pruebas que leen `ViewCompat.getRootWindowInsets` de la ventana real, las cuatro que comparan rectángulos en píxeles contra los tokens de `MaticaLayout` y las tres nuevas de la tanda de marca (login declarado y splash montado). No entran en CI: el runner de GitHub no tiene dispositivo y un paso que no instala el APK de prueba devuelve `BUILD SUCCESSFUL` con `tests="0"`. Se ejecutan a mano y su evidencia se lee por XML de resultados, nunca por el código de salida. Cinco lecciones de método quedaron fijadas aquí: **la rotación no puede probarse montando el contenido desde la prueba** (el `setContent` del test no forma parte del `savedInstanceState`; hay que recrear `MainActivity`, el host real que llama a `rememberMaticaNavigator`), **un `assertIsDisplayed` sobre un elemento con `contentDescription` combinada da falso negativo** (se consulta `substring` o se lee el nodo por su texto interno), **una ventana ancha no se simula con `width`/`requiredWidth` dentro de la ventana de un teléfono** (ambos se recortan contra el máximo heredado y `boundsInWindow` recorta además al área visible; la medida honesta es girar la actividad y verificar primero que el área de composición supera de verdad el tope), **para medir el rectángulo de un botón hay que pedir el nodo clicable** (`hasText(opción)` solo devuelve el nodo de texto, cuyo bounds no coincide con el del botón; la selección honesta es `hasText(opción) and hasClickAction()`) y **una aserción nueva no vale nada si pasa con el defecto puesto**: las seis pruebas de la caminata se corrieron con su fix revertido — la hoja transparente devolvió «El título «Pistas» arranca en y=185.0 dentro de una zona de 2063.0 px», la variante dentro de tarjeta devolvió `tests="9" failures="1"` y la pareja final (grado en `TEXT` + chips de la cabecera reactivos) devolvió `tests="11" failures="2"` con los píxeles y los contadores en el mensaje — y sólo se dio por buena una aserción después de verla fallar por el motivo escrito.
- **Cómo entra un número escrito por el estudiante**: `parseNumber` (`shared/presentation/app/MaticaScreenSupport.kt`) evalúa la expresión constante con `MathEngine` en lugar de mirar la forma del AST. El motor conserva la forma tal como se escribió — `-9` es `Neg(Const(9))` y `3/4` es un cociente, no `Const(-9)` ni `Const(3/4)` — así que aceptar sólo `Expr.Const` devolvía `null` para los mismos formatos que la propia pantalla promete aceptar, y el marcador de la recta numérica volvía a la solución sin avisar (las flechas parecían muertas). La puerta sigue siendo estrecha a propósito: variables, ecuaciones, magnitudes con unidad y divisiones por cero siguen dando `null`; lo que cambió es que un número negativo o una fracción ya son un número.
- **Las plantillas que la ruta acepta se derivan del currículo**: `MaticaRoute.KNOWN_TEMPLATES` (`shared/presentation/route/MaticaRoute.kt`) se construye con `ExerciseTemplates.all.mapTo(LinkedHashSet()) { it.id }`. La lista escrita a mano funcionó hasta que el currículo añadió `integer-ops`: la plantilla tenía plan, se dibujaba en las lecciones y sin embargo `practice/integer-ops/<semilla>` se rechazaba, así que ni un enlace profundo ni «Practicar sin grupo» podían abrirla. El invariante `lasPlantillasConocidasSonLasDelCatalogo` en `MaticaRouteTest` es lo que impide que la separación vuelva a ocurrir en silencio; la dirección de dependencia sigue siendo la misma (`presentation` ya consumía `:shared:exercises` para el plan de render).
- **El `content` de `MaticaStateView` es sólo para `Success`**: las otras variantes pintan su mensaje y punto, así que cualquier UI que deba verse siempre va como **hermana** del contenedor, no dentro de su `content`. `LoginScreen` y `ClassroomScreen` lo violaban y sus campos quedaban invisibles; hoy las dos pantallas resuelven el caso sin cuenta con su propio estado y las acciones fuera, y `losServiciosBloqueadosDicenQueFaltaUnaCuenta` es el test que impide que vuelva un formulario que no se compone.
- **El ritmo de un apartado lo da la agrupación, no el padding suelto**: `MaticaSection` (`shared/presentation/components/MaticaChips.kt`) pega el título a lo que titula con `sm` (8 dp) y el contenedor de la pantalla separa apartados con `lg` (16 dp); la cabecera individual quedó privada y sin relleno vertical porque con 8 dp por lado el título quedaba más lejos de su propia tarjeta que una tarjeta de la siguiente. `MaticaLayoutMetricsTest` mide los dos huecos en píxeles del dispositivo, y `MaticaStateRhythmTest` fija el ritmo único de los cuatro cuerpos de estado.
- **El cuerpo de las pantallas se topea, no se estira**: `MaticaLayout.maxContentWidth` (640 dp) con `MaticaScreen` centrando el cuerpo; en un teléfono no se nota porque no lo alcanza, y en una ventana ancha evita que un renglón cruce toda la pantalla y el ojo pierda el inicio del siguiente. Los objetivos táctiles y los marcos de glifo salen del mismo token (`touchTarget` 48 dp, `glyphFrame` 24 dp) en lugar de números sueltos por componente.
- CI: `.github/workflows/ci.yml` corre `jvmTest` y `testAndroidHostTest` de `:shared:math-engine`, `:shared:math-remote`, `:shared:learning-engine`, `:shared:exercises` y `:design-system`, después `:shared:presentation:compileKotlinJvm`, `:shared:presentation:jvmTest` y `:shared:presentation:testAndroidHostTest`, más `:apps:androidApp:assembleDebug` en `ubuntu-latest`, y publica el APK debug como artefacto. El job se llama `Verificación Full Kotlin` y ese nombre es estable porque la protección de `main` lo exige; añadir módulos agrega pasos, no renombra el check. Las pruebas de instrumentación Compose **no** entran en CI: necesitan un dispositivo o emulador, y correrían contra el hardware de quien programa; se ejecutan a mano y su estado se declara en la matriz. El contraste Kotlin↔SymPy **no** entra en CI: necesita el servicio Python levantado, y un verde que dependa de un motor no desplegado sería una señal falsa. Los jobs de `lint` y `backend:test` se añaden cuando exista código que cubrir; crear un paso vacío daría una señal de verde sin significado. Sin fusión a `main` con CI en rojo. Consecuencia operativa: el filtro de ramas se evalúa en `opened`, `synchronize`, `reopened`, `push` a `main` y `workflow_dispatch`, así que **toda PR se abre contra `main`** — una PR apilada sobre otra rama de trabajo no genera ningún check y no tendría evidencia que consultar.

## Pendientes de decisión justificada

- **Álgebra simbólica propia**: se conserva el motor Kotlin para lineal, grado 2 con raíz racional, dominio y análisis de error por paso. Lo que no demuestra (`Indeterminate`/`Blocked`) se delega; no se duplica un CAS dentro del cliente. El detalle de qué se adopta de cada repositorio está en `docs/DEPENDENCIES.md`.
- **iOS**: aún no se declaran targets `iosArm64`/`iosSimulatorArm64`. Su compilación y link requieren macOS/Xcode; prometer paridad sin ese entorno sería una afirmación sin evidencia. La preparación se hace manteniendo el dominio libre de APIs de Android.
- **IA adaptativa**: el repaso espaciado arranca con reglas explícitas y medibles; no se introduce un modelo complejo antes de medir que mejora el aprendizaje.
- **Clasificaciones sociales**: deshabilitadas en MVP por tratarse de usuarios menores de edad.
