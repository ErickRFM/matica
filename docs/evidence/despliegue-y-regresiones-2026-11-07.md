# Despliegue, regresiones de la refactorización y cuentas — 2026-11-07

## Qué se ejecutó hoy (números leídos de los XML, no del código de salida)

| Comando | Resultado |
| --- | --- |
| `./gradlew :shared:presentation:jvmTest :shared:presentation:testAndroidHostTest` | **210 y 210, 0 fallos** en cada destino |
| `./gradlew :backend:api:test --rerun-tasks` | **35 pruebas, 0 fallos, 0 errores** |
| `./gradlew :apps:androidApp:testDebugUnitTest` | **12 pruebas, 0 fallos** |
| `./gradlew jvmTest testAndroidHostTest` (barrida de todos los módulos) | 0 fallos: design-system 40/40, exercises 31/31, learning-engine 71/71, math-engine 135/135, math-remote 11 jvm y 10 host |
| `./gradlew :apps:androidApp:assembleDebug :apps:androidApp:assembleDebugAndroidTest` | BUILD SUCCESSFUL |
| `am instrument` por clase en `emulator-5554` | **NO EJECUTADA** — causa abajo |

Las 68 pruebas instrumentadas siguen existiendo (14 clases, 68 métodos `@Test` contados en el
árbol), pero hoy no se corrieron: ver **Bloqueado**.

## El despliegue de Render ya no es el de ayer

Sonda por `curl` contra `https://matica-api.onrender.com`, servicio en frío al primer toque
(`000` por agotar los 20 s) y caliente después:

| Ruta | Ayer | Hoy |
| --- | --- | --- |
| `GET /healthz` | 200 | **200** `{"status":"ok"}` |
| `GET /readyz` | 200 | **200** `{"status":"ready"}` |
| `GET /v1/topics` | 200 | **200** (catálogo de 15 temas) |
| `GET /v1/auth/login` | 404 | **405** |
| `GET /v1/auth/register` | — | **405** |
| `POST /v1/auth/login` (correo inexistente) | 404 | **503** `{"code":"auth_unavailable"}` |
| `GET /v1/me`, `GET /v1/attempts`, `GET /v1/groups` | — | **503** `{"code":"auth_unavailable"}` |
| `POST /v1/symbolic` | — | **503** `{"code":"auth_unavailable"}` |

Dos correcciones a la evidencia del 2026-11-06, en orden:

1. **El build desplegado ya incluye las rutas de cuentas.** Un `GET` sobre una ruta `POST` devuelve
   405, no 404: el router las conoce. La nota de ayer («el proceso desplegado es anterior a esta
   tanda») quedó superada.
2. **La base de datos de Render sí está conectada y migrada.** `/readyz` responde `ready`, y ese
   `ready` sale de `databaseReady()` → `Database.ping()`. Con `MATICA_MIGRATE_ON_START=true` el
   arranque aplica Flyway antes de aceptar tráfico, así que `users`, `attempts`, `study_groups`,
   `group_members` y `device_tokens` existen en esa base.

Lo único que falta es el secreto: `AuthService` se construye sólo cuando `users != null` **y**
`jwt != null` (`Services.kt:101`), y `jwt` es `config.jwtKey()` — que devuelve `null` si
`MATICA_JWT_SECRET` está ausente o vacío (`BackendConfig.kt:26-33`). Con `/readyz` en `ready`, la
base queda descartada como causa. Un secreto presente pero corto no produciría este estado: aborta
el arranque con `require`, y el servicio está respondiendo. Los 503 de cuentas son, por tanto,
`MATICA_JWT_SECRET` sin configurar. La barrida de 15 minutos por correo tampoco se ha agotado con
estas sondas: `recordFailure` vive en el límite de intentos, no en la rama `auth_unavailable`, que
devuelve antes de tocar el contador.

## Regresiones del árbol que se volvieron a corregir hoy

La refactorización de UI de la sesión concurrente (23:42) volvió a dejar tres comportamientos atrás.
Dos de ellos estaban ya corregidos y se reaplicaron; el tercero es de espaciado y sí llegó a medirse.

1. **`Esta lección no se pudo generar` ofrecía «Reintentar».** `MaticaViewState.Empty` usa
   `RETRY_LABEL` por defecto (`MaticaViewState.kt:26`), así que el botón deshacía la entrada —
   `onRetry = { navigator.goBack() }` — pero se llamaba «Reintentar». `MaticaSemanticsTest`
   exige el nombre de lo que hace. Vuelve `retryLabel = "Volver a la lista"` en
   [MaticaLessonScreens.kt](../../shared/presentation/src/commonMain/kotlin/mx/matica/presentation/app/MaticaLessonScreens.kt).
2. **«Empezar de nuevo» borraba la pizarra sin preguntar.** El `MaticaConfirmDialog` que protege
   una acción no deshacible desapareció del player; `MaticaConfirmGuardTest` lo pide. Se reaplicó
   como `overlay` de `MaticaScreen` (no dentro del `content`, que vive dentro del
   `verticalScroll`), con el estado `confirmarBorrado` y el borrado en `onConfirm`.
3. **Fila de lección sin hueco entre título y ecuación** (0 dp medidos, aparte del artefacto de
   medida): `Arrangement.spacedBy(MaticaSpacing.xs)` en la `Column` de la lista.
4. Lo de ayer se mantiene: el motivo corto del rechazo va en el campo (`rejection = verdictReason(verdict)`)
   y el veredicto completo en la tarjeta; la tecla de limpiado se llama `AC` para no chocar con la
   letra `C` de las opciones.

## Lo que enseñaron las mediciones de layout

`boundsInWindow` **se recorta**: un nodo enteramente bajo el pliegue reporta `Rect(0,0,0,0)`. Dos
consecuencias medidas hoy:

- `MaticaAppUiTest.desdeHoySePuedeAbrirElPerfilLocal` fallaba porque el `performClick()` sobre
  «Mi perfil» se emitía en el vacío. La sonda temporal (`MaticaProfileProbeTest`, ya borrada) lo
  mostró: `SONDA_NODO bounds=Rect.fromLTRB(0.0, 0.0, 0.0, 0.0)` y `SONDA_RUTA=Home`. Corregido con
  `performScrollTo()` antes del toque. Medido en el aparato: `OK (1 test)`. El mismo patrón —clic
  sin desplazar— reapareció en la prueba de cierre de lección; ver «El emulador volvió» más abajo.
- `MaticaLayoutMetricsTest` medía **0 dp** el hueco de la fila de lección y **−393 dp** el de las
  opciones del diagnóstico: el primer número era verdad y el segundo era el artefacto. Las dos
  pruebas ahora leen `positionInWindow` + `size`, que no se recortan, y desplazan una sola vez.

## El emulador volvió: lo que se midió y lo que se rompió de verdad

Erick mató el proceso colgado y el AVD arrancó en frío. Con `emulator-5554` de nuevo, las cuatro
hipótesis que quedaron abiertas se resolvieron con números, y dos de ellas eran defectos de
producción, no de prueba.

**Regresión 1 — `MaticaNavigatorRestoreTest`.** La sonda volcó los nodos clickeables del mapa: los
medallones reportaban `desc=null` y el nodo con la descripción no estaba en la lista de clickeables.
Es decir, `RouteChip` tenía el nombre en un `Column` que no se podía pulsar y la acción en un
`Surface` sin nombre: TalkBack anunciaba «botón, no escrito» y tocar el rótulo —la mitad baja del
chip, la que más se ve— no hacía nada. No era el halo ni el grafo vacío. **Arreglado en producción**:
el chip completo es ahora el botón y sobre él se fusionan rol y descripción
([RouteMapView.kt:310](../../shared/presentation/src/commonMain/kotlin/mx/matica/presentation/math/RouteMapView.kt)).
Medido después: `OK (1 test)` y `MaticaWindowInsetsTest OK (4 tests)` — ese test pulsaba el rótulo y
sólo funcionaba porque el `mergeDescendants` heredaba el texto, no la acción.

**Regresión 2 — `MaticaAppUiTest.laRespuestaVerificadaEnPantallaCierraLaLeccion`.** La sonda dio el
par exacto: antes de desplazar, «Verificar por sustitución» estaba en `y=1665` con
`boundsInWindow=Rect(0,0,0,0)`; tras `performScrollTo()` el mismo nodo devolvió
`Rect(88, 1511, 992, 1643)` y el toque cerró la lección (`¡EXCELENTE!` montado en `y=185`). El clic
del test se inyectaba en las coordenadas de antes del auto-scroll que provoca `performTextInput`.
**Arreglado en el test** con `performScrollTo()` antes del toque — la aserción no se movió.

**Descubierto de paso.** Con el trío de escritura fijado fuera del scroll, la tarjeta del veredicto
se quedaba debajo del pliegue: el estudiante escribía la siguiente línea sin haber leído si la
anterior sirvió. La tarjeta de respuesta corta (paso) vive ahora en la barra fija, encima del campo
([MaticaLessonScreens.kt:189](../../shared/presentation/src/commonMain/kotlin/mx/matica/presentation/app/MaticaLessonScreens.kt));
la de «Respuesta final» sigue dentro de su propia tarjeta. Medido: `elReproductorMuestraElVeredictoQueDioElMotor`
pasó de «Required value was null» a verde y `MaticaLayoutMetricsTest OK (10 tests)`.

**Inventario de destinos.** `MaticaRoute.all` pasó de 18 a 21 con el plan maestro de didáctica y la
sonda no conocía `error-spotting`, `word-problem` ni `balance-lab`. Los tres textos nuevos se tomaron
volcando el árbol de semántica en el aparato («Detecta el Error», «La tarifa del taxi», «Laboratorio
Táctil»; 11, 9 y 15 textos respectivamente, todos por encima del mínimo de 4). Además el test ahora
exige que las marcas cubran `MaticaRoute.all` como conjuntos, así que un destino nuevo sin marca no
puede colarse otra vez. Medido: `OK (4 tests)`.

## Bloqueo actual: dos sesiones en el mismo aparato

A partir de aquí los resultados dejaron de ser reproducibles y la causa está medida, no supuesta:

- El APK de instrumentación instalado en el teléfono pesaba **1 429 136 B** mientras el construido en
  esta sesión pesa **1 413 777 B**. La otra sesión instala los suyos encima de los míos.
- Con esa mezcla, la traza del fallo apuntaba a la línea 276 de una versión del test que ya no existe
  en este árbol, y cuatro clases seguidas devolvieron `shortMsg=Process crashed`.

Ninguna cifra de esta sección se puede volver a sacar mientras dos sesiones compartan
`emulator-5554`. Lo comprobado en JVM con el árbol actual, sin aparato:
`./gradlew jvmTest --rerun` → **523 pruebas, 0 fallos, 0 errores, 0 omitidas** (72 archivos de
resultados).

**Repetición parcial sobre `main` ya con la PR #15 fusionada** (`8ecaf28`), con la paridad de APK
verificada en la misma orden (`local=1413777 instalado=1413777`):

- `MaticaNavigatorRestoreTest` **OK (1)** y `MaticaWindowInsetsTest` **OK (4)** — el chip del mapa
  sigue verde sobre el grafo nuevo.
- `MaticaSemanticsTest` **3 con 1 fallo** y `MaticaLayoutMetricsTest` **10 con 1 fallo**, ambos por
  encima del contenido de la PR #15, no por el cambio de esta sección: la cobertura curricular del
  100% hace que la lección 99 del test de semántica ya no sea «la que no genera nadie», y la prueba
  de la flecha «Regresa» mide un glifo que este árbol no ha tocado. `MaticaAppUiTest` cerró **13 con
  1 fallo** señalando la línea 276 de una copia del test que no es la instalada, así que ése sigue
  siendo el fallo no imputable al árbol. `MaticaScreenInventoryTest` devolvió
  `shortMsg=Process crashed` sin veredicto.
- Las dos correcciones que la sesión concurrente había vuelto a tirar siguen intactas tras la
  fusión: `retryLabel = "Volver a la lista"` ([MaticaLessonScreens.kt:122](../../shared/presentation/src/commonMain/kotlin/mx/matica/presentation/app/MaticaLessonScreens.kt))
  y el `MaticaConfirmDialog` de «Empezar de nuevo» (`:155`), ambas también presentes en `main`.

## Sin cambios respecto a ayer

- **Firma de release**: el `signingConfig` sigue cableado y sin keystore en esta máquina.
- **FCM**: sin `google-services.json` ni cuenta de servicio; no se ha enviado ningún mensaje.
- **Extremo a extremo** (intento → SQLite → worker → Postgres): la cola y el contrato están probados
  por separado; el recorrido junto sigue esperando el secreto JWT.
