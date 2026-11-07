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

## Lo que este documento no certifica

- No se ha creado el keystore: los comandos son los que hay que ejecutar, no un resultado.
- No se ha enviado ningún mensaje FCM: no hay proyecto Firebase ni token de dispositivo.
- No se ha desplegado esta tanda en Render; el `/v1/auth/login` de producción sigue en 404 hasta el
  merge y el redespliegue.
