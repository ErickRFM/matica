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
  `performScrollTo()` antes del toque. **Ésta es la única medición instrumentada verde de hoy**:
  `OK (1 test)`.
- `MaticaLayoutMetricsTest` medía **0 dp** el hueco de la fila de lección y **−393 dp** el de las
  opciones del diagnóstico: el primer número era verdad y el segundo era el artefacto. Las dos
  pruebas ahora leen `positionInWindow` + `size`, que no se recortan, y desplazan una sola vez.

## Bloqueado: el emulador

`emulator-5554` quedó `offline` y no volvió. La recuperación que sí se intentó: `adb kill-server` +
`adb start-server`, `adb reconnect offline` y un lazo de 30 sondeos de 10 s. `sys.boot_completed`
nunca respondió. El proceso `qemu-system-x86_64.exe` (PID 37144) sigue vivo con 32 MB de memoria
real — es el `system_server` del AVD el que murió bajo la instrumentación sostenida, igual que el
2026-11-06.

No se mató el proceso ni se reinició el AVD en frío: otra sesión está escribiendo en el mismo árbol
de trabajo y podría estar instrumentándolo. **Mientras el aparato no vuelva, quedan sin medir en
el teléfono los puntos 1 y 2 de las regresiones y las dos pruebas de layout reescritas.** Compilan y
el APK de instrumentación se construyó; eso es todo lo que se puede afirmar hoy.

También queda sin diagnosticar, con la hipótesis anotada y sin verificar:

- `MaticaAppUiTest.laRespuestaVerificadaEnPantallaCierraLaLeccion`: el nodo «¡EXCELENTE!» existe pero
  `assertIsDisplayed()` falla. `LessonCompleteScreen` es otra ruta
  ([MaticaLessonScreens.kt:502](../../shared/presentation/src/commonMain/kotlin/mx/matica/presentation/app/MaticaLessonScreens.kt)),
  así que lo más parecido es el mismo nodo bajo el pliegue; habría que confirmar con la sonda, no
  suponerlo.
- `MaticaNavigatorRestoreTest`: no encuentra el nodo de «Operaciones con enteros». **Hipótesis
  descartada por lectura**: `buildRouteNodes` sigue poniendo `label = skill.labelEs`
  ([RouteMapView.kt:119](../../shared/presentation/src/commonMain/kotlin/mx/matica/presentation/math/RouteMapView.kt))
  y el `contentDescription` del chip se arma con esa etiqueta; el `enabled = node.unlocked` de la
  línea 352 no bloquea el primer nudo, porque `integer-operations` no tiene prerrequisitos. Las dos
  rutas que el test pulsa («Explorar la ruta sin diagnóstico», «Abrir mi ruta») también siguen en
  producción. Queda en pie el candidato que sí requiere el aparato: el halo
  (`rememberInfiniteTransition`) interfiriendo con `waitForIdle()`, o un grafo vacío que mande la
  pantalla al estado `Empty` antes de dibujar chips.
- El commit de las 10:00 (`0a867fc`, plan maestro de didáctica) añade rutas nuevas
  (`error-spotting`, `word-problem`, `balance-lab`) y componentes nuevos; puede haber cambiado el
  orden de la cabecera de «Mi ruta» por debajo de este test. No lo medí.

## Sin cambios respecto a ayer

- **Firma de release**: el `signingConfig` sigue cableado y sin keystore en esta máquina.
- **FCM**: sin `google-services.json` ni cuenta de servicio; no se ha enviado ningún mensaje.
- **Extremo a extremo** (intento → SQLite → worker → Postgres): la cola y el contrato están probados
  por separado; el recorrido junto sigue esperando el secreto JWT.
