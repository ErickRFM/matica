# Cuentas, aula y sincronización — 2026-11-06

## Qué se implementó

- **Base de datos**: migración Flyway `V1__init.sql` con `users`, `attempts`, `study_groups`,
  `group_members` y `device_tokens`; pool HikariCP; repositorios JDBC sin ORM. Flyway corre al
  arrancar la API.
- **Cuentas propias**: Argon2id (BouncyCastle, m=64 MiB, t=3, p=1) y JWT HS256 con vencimiento a 30
  días; límite de 10 fallos por correo en 15 minutos; respuesta de tiempo constante cuando el correo
  no existe.
- **Endpoints**: registro, login, `me`, intentos idempotentes por UUID, grupos con código de
  invitación de 6 caracteres, tokens de dispositivo y proxy SymPy autenticado (nunca desde el APK).
- **Android**: SQLDelight (`pending_attempts`) como cola por usuario, `WorkManager` para vaciarla,
  cliente Ktor/OkHttp, token en preferencias privadas, aula real (unirse/crear/listar) y sección
  «Cuenta» en Ajustes. Sin URL de servidor, la UI lo declara y no muestra un formulario inútil.

## Ejecutado (leído de los XML, no del código de salida)

| Comando | Resultado |
| --- | --- |
| `./gradlew -Pmatica.backendOnly=true :backend:api:test` | **35 pruebas, 0 fallos, 0 errores** |
| `./gradlew :apps:androidApp:testDebugUnitTest` | **12 pruebas, 0 fallos** (7 de `MaticaApiClientTest`, 5 de `PendingAttemptsTest`) |
| `./gradlew :shared:presentation:jvmTest :shared:presentation:testAndroidHostTest` | **143 y 143, 0 fallos** en cada destino |
| `am instrument` por clase en `emulator-5554` (14 clases) | **68 pruebas, 0 fallos** — detalle abajo |
| `./gradlew :apps:androidApp:assembleDebugAndroidTest` | BUILD SUCCESSFUL |
| `./gradlew :apps:androidApp:assembleDebug -Pmatica.api.baseUrl=https://matica-api.onrender.com` | BUILD SUCCESSFUL, APK 17,7 MB |

### Instrumentación: 68 pruebas por clase

Con el emulador libre de procesos ajenos, cada clase corrió en su propio proceso de
instrumentation y su resultado se leyó del texto de `am instrument`:

| Clase | Pruebas |
| --- | --- |
| `MaticaAppUiTest` | 13 |
| `MaticaLayoutMetricsTest` | 10 |
| `MaticaMathComponentsTest` | 11 |
| `MaticaStateViewsTest` | 5 |
| `MaticaScreenInventoryTest` | 4 |
| `MaticaWindowInsetsTest` | 4 |
| `MaticaKeyboardWalkTest` | 4 |
| `MaticaConfirmGuardTest` | 3 |
| `MaticaSemanticsTest` | 3 |
| `MaticaMotionTest` | 3 |
| `MaticaThemeRenderTest` | 3 |
| `MaticaSplashUiTest` | 2 |
| `MaticaRevealTest` | 2 |
| `MaticaNavigatorRestoreTest` | 1 |
| **Total** | **68, 0 fallos** |

`DatabaseIntegrationTest` no usa dobles: arranca `EmbeddedPostgres` (binario real de PostgreSQL),
aplica la migración y ejecuta el SQL de los cuatro repositorios, incluida la idempotencia de
`attempts` y el conflicto de códigos de grupo. En el APK, `PendingAttemptsTest` corre las consultas
SQLDelight sobre SQLite en memoria y `MaticaApiClientTest` fija el JSON que el cliente envía y
entiende (login, 409 con mensaje del servidor, 500 sin cuerpo, intento con `usedHint`, lista de
grupos, alta de token de dispositivo con su `platform` y build sin URL que no toca la red).

## Defectos encontrados y corregidos en esta tanda

1. `sendAttempt` decodificaba la respuesta 201 como `String`, así que un intento aceptado se
   reportaba como formato desconocido y el worker lo habría reintentado para siempre. La prueba
   `attemptSendsHintFlagAndIdempotentId` falló y el cliente pasó a decodificar
   `ApiAttemptAccepted(id, status)`.
2. Gradle 9 exige `junit-platform-launcher` en el runtime de pruebas: sin él el worker de test ni
   arranca (`Failed to load JUnit Platform`). Añadido al catálogo y al módulo.
3. `ClassroomScreen` componía su tarjeta dentro del `content` de `MaticaStateView`, que sólo se
   invoca en `Success`; con `Offline`/`Error` fijo el formulario nunca existía. Se reescribió como
   hermana del estado, con el estado de sesión como primer control.
4. `registerDevice` mandaba `{"token":…}` sin `platform`: kotlinx omite las propiedades iguales a su
   valor por defecto, así que el campo declarado como `= "android"` nunca viajaba. Lo detectó
   `deviceRegistrationSendsTokenAndPlatform` al exigir el JSON completo; el DTO del cliente dejó de
   tener valor por defecto y el payload se escribe explícito.
5. El paso de pruebas de datos de la app se añadió a CI (`testDebugUnitTest` antes de compilar las
   instrumentadas), de modo que el contrato HTTP y la cola SQLDelight se ejecuten en cada PR sin
   emulador.

## NO EJECUTADO (y por qué)

- **Login real desde el teléfono contra Render**: `POST https://matica-api.onrender.com/v1/auth/login`
  devuelve **404** porque el proceso desplegado es anterior a esta tanda (se comprobó con `curl` el
  2026-11-06; `/healthz`, `/readyz` y `/v1/topics` sí responden 200). El APK con la URL configurada
  abre el formulario real; la llamada falla en el servidor hasta fusionar y redesplegar.
  **Superado el 2026-11-07**: el despliegue ya trae estas rutas (`GET /v1/auth/login` → 405) y su
  base está conectada (`/readyz` → `ready`), pero el login sigue caído por otra razón — 503
  `auth_unavailable` por falta de `MATICA_JWT_SECRET`. Medición y corrección en
  `docs/evidence/despliegue-y-regresiones-2026-11-07.md`.
- **Sincronización completa de extremo a extremo** (intento → SQLite → worker → Postgres): la cola y
  el contrato están probados por separado; el recorrido junto depende del despliegue.
- **Corrida única de `connectedDebugAndroidTest`**: el agregado de AGP quedó **inestable en este
  equipo**, no rojo por pruebas. Dos causas, ambas ajenas a la app:
  1. **Scripts de la tanda anterior seguían vivos.** `build/tanda_ui09.sh` (lanzado tres veces desde
     la sesión previa, algunos ya huérfanos) reinstalaba los APK y lanzaba `am instrument` por clase
     en bucle. Cada nueva instrumentación mata la anterior: de ahí los `Process crashed`, los
     `INSTRUMENTATION_FAILED` y los conteos fantasma (70/20/5) que se anotaron en la sesión.
  2. **El servidor de sistema del emulador murió una vez**: `service check package` y
     `service check activity` respondían `not found` y ningún APK arrancaba. Se recuperó con
     `adb reboot` + reinicio del servidor adb; `sys.boot_completed=1` y ambos servicios `found`.
  Una corrida de AGP posterior abortó con `Test run failed to complete. No test results` porque un
  `deletePackageX` desinstaló el paquete a los ~5 s de arrancar. **La medición que cuenta es la
  tabla de 68/68 por clase**, cada una en su proceso y sin otro `adb` en vuelo. Lo que queda como
  deuda es la comodidad del comando único, no la cobertura.
- **Migraciones sobre el PostgreSQL de Render**: ejecutadas contra PostgreSQL efímero local; sobre la
  base del despliegue no se ha corrido nada.
- **Firma de release**: el `signingConfig` está cableado, pero no existe keystore en esta máquina.
- **FCM**: no hay `google-services.json` ni cuenta de servicio; no se ha enviado ningún mensaje.
