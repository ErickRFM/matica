# Firma de release y FCM — lo que hace Erick

El repositorio ya lee las propiedades de firma y ya tiene el emisor FCM del servidor. Lo que falta
son dos credenciales que **sólo tú puedes crear** y que nunca deben entrar a Git.

## 1. Keystore de release

El `build.gradle.kts` de `:apps:androidApp` firma el release únicamente si existen las propiedades
`matica.release.storeFile` y `matica.release.keyAlias`. Sin ellas el build sigue en verde y produce
un APK de release **sin firmar**: no se rompe CI ni la máquina de nadie.

### Crear el archivo (una sola vez, se guarda para siempre)

En Git Bash:

```bash
keytool -genkeypair -v \
  -keystore "$HOME/matica-release.jks" \
  -alias matica \
  -keyalg RSA -keysize 4096 -validity 10000
```

Te pedirá una contraseña de almacén, otra de la clave (puede ser la misma) y tus datos. Guarda el
`.jks` y las contraseñas fuera del repositorio y con copia de seguridad: **si se pierden, la app
publicada no se puede actualizar** (Google Play exige la misma firma para siempre).

### Registrar las propiedades

Edita `C:\Users\erik5\.gradle\gradle.properties` (no existe en el repo) y añade:

```properties
matica.release.storeFile=C:/Users/erik5/matica-release.jks
matica.release.storePassword=LA_CONTRASENA_DEL_ALMACEN
matica.release.keyAlias=matica
matica.release.keyPassword=LA_CONTRASENA_DE_LA_CLAVE
```

Usa barras normales (`/`) en la ruta aunque estés en Windows.

### Compilar y comprobar

```bash
./gradlew :apps:androidApp:assembleRelease -Pmatica.api.baseUrl=https://matica-api.onrender.com
"$LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/apksigner.bat" verify --print-certs \
  apps/androidApp/build/outputs/apk/release/androidApp-release.apk
```

`apksigner` debe listar tu huella SHA-256. Para un AAB de Play, `:apps:androidApp:bundleRelease`.

Sin `-Pmatica.api.baseUrl` (o `MATICA_API_BASE_URL`), el release se compila sin cuentas: la app lo
avisa en Ajustes y en el login. La URL pública no es un secreto, pero se pasa al compilar para no
hornear entornos distintos en el mismo código.

## 2. Firebase Cloud Messaging

El servidor ya sabe enviar avisos con `MATICA_FCM_SERVICE_ACCOUNT` (JWT RS256 → token OAuth →
`messages:send`). El endpoint `POST /v1/devices` registra el token del teléfono y el aula avisa al
dueño cuando alguien se une. Lo que falta es el lado Firebase y el cliente Android.

### Lo que haces en Firebase (una vez)

1. <https://console.firebase.google.com> → **Agregar proyecto** (p. ej. `matica`).
2. **Agregar app → Android**, paquete `mx.matica` (el release). Si quieres probar en debug, agrega
   también `mx.matica.debug`.
3. Descarga `google-services.json` y colócalo en `apps/androidApp/google-services.json`
   (ya está en `.gitignore`; no lo subas).
4. **Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada**. Se descarga
   un JSON. Pégalo **completo, en una sola línea** en Render como `MATICA_FCM_SERVICE_ACCOUNT`
   (Dashboard → tu servicio → Environment). No lo pongas en el APK ni en el repo.

### Lo que falta en la app (cuando el JSON exista)

El cliente Android todavía no pide su token a Firebase. Los tres pasos, en ese orden:

1. Añadir el plugin `com.google.gms.google-services` y `com.google.firebase:firebase-messaging`
   al catálogo y a `:apps:androidApp`, de forma que sin `google-services.json` el build siga
   compilando (el plugin falla si el archivo no está).
2. Un `FirebaseMessagingService` que llame a `MaticaGraph.from(this).account.registerDeviceToken(token)`.
3. Ese método ya existe: hace `POST /v1/devices` con el Bearer guardado y responde 204 en el servidor;
   sin sesión devuelve `false` sin llamar a la red.

Hasta que eso exista, el aviso del aula simplemente no se envía: el servidor no falla ni bloquea la
unión al grupo (`notifyOwner` corre aparte y atrapa sus errores).

## 3. Variables de Render, completas

| Variable | De dónde sale | Secreto |
| --- | --- | --- |
| `DATABASE_URL` | Render → PostgreSQL interno del servicio | Sí |
| `MATICA_JWT_SECRET` | `openssl rand -base64 48` | Sí |
| `MATICA_SYMBOLIC_URL` | URL del servicio SymPy existente | No, pero va en Render |
| `MATICA_SYMBOLIC_API_KEY` | La clave que ya usa ese servicio | Sí |
| `MATICA_FCM_SERVICE_ACCOUNT` | JSON de la cuenta de servicio de Firebase | Sí |
| `MATICA_MIGRATE_ON_START` | `true` por defecto; `false` sólo en emergencia | No |

`render.yaml` ya declara las cinco primeras como `sync: false`, así que Render las pide al crear el
servicio y no las guarda en Git.

### 3.1 La única que falta para que el login responda (medido 2026-11-07)

El proceso desplegado ya trae las rutas de cuentas y su base está conectada: `GET /v1/auth/login`
responde **405** (la ruta existe; el 404 de la sesión anterior quedó superado) y `/readyz` responde
**`ready`**, que es el ping real contra PostgreSQL. Aun así `POST /v1/auth/login`, `/v1/me`,
`/v1/attempts`, `/v1/groups` y `/v1/symbolic` devuelven **503 `auth_unavailable`**, porque
`AuthService` sólo se construye cuando hay usuarios **y** clave JWT
(`backend/api/src/main/kotlin/mx/matica/backend/Services.kt:101`), y `jwtKey()` devuelve `null` si
`MATICA_JWT_SECRET` está ausente o vacía (`BackendConfig.kt:26-33`).

Es decir: **una variable, y el login deja de estar bloqueado.**

```powershell
# Windows, PowerShell: 48 bytes aleatorios en base64. No reutilizar un ejemplo público.
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

Cualquier generador vale (`openssl rand -base64 48` en Git Bash, 64 caracteres), siempre que el valor
que pegues mida **al menos 32 caracteres**: el `require` del arranque cuenta los bytes UTF‑8 de la
cadena tal como está en el entorno, no los bytes del secreto decodificado
(`BackendConfig.kt:26-33`), así que un valor truncado aborta el proceso en vez de funcionar a medias
y se ve en el log.

Dónde se pega: **Render Dashboard → tu servicio `matica-api-staging` → Environment Variables →
Add**, con el nombre exacto `MATICA_JWT_SECRET` y el valor en una sola línea. No va en `render.yaml`
(esa clave ya está marcada `sync: false` precisamente para que el valor no viaje al repositorio) y no
se sube en el commit. Tras guardar, Render reinicia el proceso y conviene verificar:

```bash
curl -s https://matica-api.onrender.com/readyz        # -> {"status":"ready"}
curl -s -X POST -H 'Content-Type: application/json' \
  -d '{"email":"no-existe-sonda@matica.invalid","password":"Nunca-Una-Contrasena-Real-1"}' \
  https://matica-api.onrender.com/v1/auth/login
# antes: {"code":"auth_unavailable",...}   después: {"code":"invalid_credentials",...}
```

El cambio de `auth_unavailable` a `invalid_credentials` es la señal de que la cuenta ya funciona sin
necesidad de registrar un correo real en producción. Si se prefiere no tocar la base de Render, el
orden inverso también vale: una base de staging y su `DATABASE_URL`, y dejar ésta para el final.

Si además se rota el secreto, los tokens viejos dejan de validar de inmediato: la app guarda el JWT
en preferencias privadas y, al recibir 401, cierra sesión sin borrar la cola de intentos pendiente.

## Lo que este documento no certifica

- No se ha creado el keystore: los comandos son los que hay que ejecutar, no un resultado.
- No se ha enviado ningún mensaje FCM: no hay proyecto Firebase ni token de dispositivo.
- El despliegue de Render **sí** corre ya este backend (medido por `curl` el 2026-11-07), pero el
  login real sigue sin ejecutarse desde el teléfono: `POST /v1/auth/login` responde 503 porque
  `MATICA_JWT_SECRET` no está en el entorno. Ver **3.1**.
