# Mática — conexión, cuentas y sincronización (B0-05, B4, B5)

## Estado verificado

- **Ejecutado el 2026-11-06 en local**: `./gradlew -Pmatica.backendOnly=true :backend:api:test`
  termina con **35 pruebas, 0 fallos, 0 errores** (leído de los XML de `build/test-results/test/`).
  Entre ellas, `DatabaseIntegrationTest` levanta un PostgreSQL real y efímero (`EmbeddedPostgres` de
  Zonky, sin Docker), aplica la migración Flyway y ejercita los cuatro repositorios JDBC: usuarios y
  correo duplicado, intentos idempotentes y listado por usuario, grupos con código repetido, alta de
  miembro y conteo, y tokens de dispositivo con reemplazo.
- **El servicio desplegado sigue siendo el anterior**: `https://matica-api.onrender.com` responde
  200 en `/healthz`, `/readyz` (con `SELECT 1` real) y `/v1/topics`, pero `POST /v1/auth/login`
  devuelve **404** porque ese proceso no incluye esta tanda. Hasta fusionar y redesplegar, la app
  muestra el rechazo del servidor en lugar de una sesión: **el login real contra Render está NO
  EJECUTADO**.
- **No se usó Supabase ni RLS.** La identidad la emite este backend y la base sólo acepta conexiones
  desde él (`DATABASE_URL` interna de Render); no hay claves anónimas ni `service_role` en ninguna
  parte. El aislamiento entre estudiantes es por `user_id` en cada consulta y por autorización en la
  ruta, no por políticas de fila de un proveedor externo.
- El Docker construye sólo la API con `-Pmatica.backendOnly=true`; comparte `SkillId.kt` con el motor
  y no necesita Android SDK.
- Un Render existente (`matica-symbolic-staging`) es **auxiliar SymPy**. No es este backend.

## Esquema y migraciones

`backend/api/src/main/resources/db/migration/V1__init.sql` crea:

| Tabla | Para qué | Claves y reglas |
| --- | --- | --- |
| `users` | Cuenta del estudiante | `email_normalized` único; `password_hash` Argon2id |
| `attempts` | Evidencia de un paso | `id` UUID del cliente; `(user_id, occurred_at desc)` indexado; `verdict` cerrado a `valid/invalid/indeterminate/syntax` |
| `study_groups` | Aula del docente | `invite_code` único de 6 caracteres sin letras ambiguas |
| `group_members` | Membresía | PK `(group_id, user_id)`; rol `owner/student` |
| `device_tokens` | Avisos FCM | PK `(user_id, token)`, plataforma `android` |

Flyway aplica `classpath:db/migration` **al arrancar** (`MATICA_MIGRATE_ON_START` sólo se pone en
`false` para un arranque de emergencia). Si la base no está disponible, el proceso sigue vivo para
`/healthz` y `/v1/topics`, pero las rutas de cuenta responden **503** con `database_unavailable`.

## Endpoints

| Método y ruta | Auth | Qué hace |
| --- | --- | --- |
| `GET /healthz` | — | El proceso responde |
| `GET /readyz` | — | `SELECT 1` real contra PostgreSQL |
| `GET /v1/topics` | — | Catálogo de 15 habilidades por concepto |
| `POST /v1/auth/register` | — | Crea cuenta (201). Correo repetido → 409 |
| `POST /v1/auth/login` | — | Devuelve JWT (HS256, 30 días). Fallo → 401; exceso → 429 |
| `GET /v1/me` | Bearer | Cuenta de la sesión |
| `POST /v1/attempts` | Bearer | Intento idempotente por `id` UUID: 201 creado, 200 duplicado, 409 id de otra cuenta |
| `GET /v1/attempts?since=&limit=` | Bearer | Intentos del usuario, más nuevos primero |
| `POST /v1/groups` | Bearer | Crea grupo y devuelve el código sólo al dueño |
| `POST /v1/groups/join` | Bearer | Une por código; 404 si no existe |
| `GET /v1/groups` | Bearer | Grupos del usuario con su rol y conteo |
| `GET /v1/groups/{id}` | Bearer | Detalle y miembros (alias o correo enmascarado) |
| `POST /v1/devices` | Bearer | Registra token FCM (204) |
| `POST /v1/symbolic` | Bearer | Proxy al servicio SymPy; nunca se abre desde el APK |

Límites: paso de 1 a 500 caracteres, ejercicio de 1 a 80, `occurredAt` entre −90 días y +5 minutos,
cuerpo simbólico de 64 KiB. Un JWT inválido o vencido devuelve 401 con mensaje legible.

## Variables por destino

| Dónde | Variable | Valor |
| --- | --- | --- |
| Render API (secreto) | `DATABASE_URL` | URL interna PostgreSQL de una base dedicada a Mática |
| Render API (secreto) | `MATICA_JWT_SECRET` | Mínimo 32 bytes; `openssl rand -base64 48` |
| Render API (secreto) | `MATICA_SYMBOLIC_URL` | Base del servicio SymPy, p. ej. `https://matica-symbolic-staging.onrender.com` |
| Render API (secreto) | `MATICA_SYMBOLIC_API_KEY` | La misma clave que ya usa ese servicio |
| Render API (secreto) | `MATICA_FCM_SERVICE_ACCOUNT` | JSON completo de la cuenta de servicio (una línea) |
| Render API (opcional) | `MATICA_MIGRATE_ON_START` | `false` desactiva Flyway al arrancar |
| Render API (automática) | `PORT` | Render la asigna; localmente 10000 |
| Android (pública, build) | `MATICA_API_BASE_URL` | URL HTTPS del servicio, sin ruta de endpoint |
| Gradle (alternativa) | `matica.api.baseUrl` | La misma URL con `-Pmatica.api.baseUrl=...` |

**Ningún secreto viaja en el APK.** La app sólo conoce la URL pública. El token de sesión se guarda
en preferencias privadas (`allowBackup="false"`), no en la copia de seguridad ni en el repositorio.
Si el APK se compila sin URL, la pantalla de cuenta lo dice en vez de ofrecer un formulario que
fallaría.

## Arranque y validación

```bash
./gradlew -Pmatica.backendOnly=true :backend:api:test
./gradlew -Pmatica.backendOnly=true :backend:api:run       # sin DATABASE_URL: /readyz 503
curl -i http://localhost:10000/healthz
curl -i http://localhost:10000/v1/topics
curl -i http://localhost:10000/readyz
```

Con Render: asigna las variables de la tabla, confirma `/readyz=200` y luego:

```bash
curl -i -X POST https://<servicio>/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"tu@correo.com","password":"minimo8caracteres","displayName":"Docente"}'
```

La respuesta trae `token`; úsalo como `Authorization: Bearer …` en `/v1/attempts` y `/v1/groups`.
Un `404` en esa ruta significa que el proceso desplegado todavía no tiene esta tanda.

## Proxy SymPy

La app nunca llama al servicio simbólico. El backend reenvía el cuerpo JSON tal cual a
`${MATICA_SYMBOLIC_URL}/v1/symbolic` con `X-Matica-Service-Key`, exige HTTPS (o `127.0.0.1` en
desarrollo), limita el cuerpo a 64 KiB y rechaza capacidades fuera del contrato. Sin
`MATICA_SYMBOLIC_URL`/`MATICA_SYMBOLIC_API_KEY`, la ruta devuelve **503** con un motivo claro.

## Política de interfaz

Los temas se nombran por su concepto, nunca por etapa escolar. El aula comparte alias o correo
enmascarado, no el correo completo de otros estudiantes.

## Pendiente sin fingir

- **Desplegar esta tanda** (merge → CI verde → redespliegue de Render) y repetir registro/login
  desde el APK.
- **Notificación FCM real**: el emisor del servidor está probado en su configuración, pero sin
  `google-services.json` en la app y sin una cuenta de servicio cargada no se ha enviado ningún
  mensaje.
- **Borrado de cuenta** y exportación de datos: el esquema lo permite, la ruta no existe.
- **Recompensas en servidor** (B4-03): siguen calculándose sólo en el teléfono.
