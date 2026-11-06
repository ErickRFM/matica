# Mática — Base Supabase + API Ktor en Render

## Arquitectura de despliegue

- **Supabase PostgreSQL**: una base principal nueva y separada de SIII y Compañero; esquema privado `matica`.
- **Supabase Auth**: identidad de los estudiantes; los registros `learner_profiles.user_id` se vinculan a `auth.users`.
- **Render**: servicio Docker del Ktor `backend/api/Dockerfile` que usa una variable privada `DATABASE_URL` para hablar con PostgreSQL.
- **Android local**: URL HTTPS del Ktor configurada por `MATICA_API_BASE_URL`. La app no debe recibir la contraseña SQL ni claves secretas de Supabase.

### 1. Crear proyecto Supabase exclusivo

Elegir explícitamente la organización correcta en la cuenta conectada y crear el proyecto **matica** en una región de EE. UU. cercana a Render. No crear tablas en el proyecto SIII. La conexión requiere verificar que el plan seleccionado no tenga costo no autorizado.

Después de provisionar, ejecutar el contenido versionado de `backend/database/schema.sql` contra **ese proyecto nuevo** usando las herramientas de migración seguras, y verificar con `backend/database/verify.sql`. El esquema es privado y revoca permisos `anon`/`authenticated` a las tablas; **ninguna lectura ni escritura de progreso se permite directamente desde el navegador** hasta implementar autenticación de backend.

### 2. Crear API en Render

Elegir explícitamente workspace. Crear servicio Docker `matica-api-staging` desde GitHub `ErickRFM/matica`, rama `main`, contexto raíz y Dockerfile `backend/api/Dockerfile`. Región sugerida Virginia para Supabase us-east-1. Configurar:

```dotenv
DATABASE_URL=<CONNECTION_STRING_PRIVADA_SUPABASE_POSTGRES>
PORT=10000
```

Obtén el valor de DATABASE_URL en **Supabase Dashboard → Connect → Connection string** (preferir pooler de sesión IPv4 si Render no dispone de IPv6), con SSL obligatorio. La conexión tipo `postgresql://` es normalizada a JDBC por `PostgresProbe`. Pegar únicamente en **Render → Environment**; nunca en GitHub, Android ni archivos versionados.

El healthcheck de Render usa `/readyz`. Devuelve HTTP 200 solo si hay conexión y está aplicada la versión 1 de la base, y 503 si no hay conexión/esquema.

### 3. Variables para desarrollo local

En Windows, abre PowerShell dentro del repositorio Mática:

```powershell
$env:MATICA_API_BASE_URL = "https://matica-api-staging.onrender.com"
# Para ejecutar exclusivamente el Ktor local y probar PostgreSQL:
$env:DATABASE_URL = "<CONNECTION_STRING_PRIVADA_SUPABASE_POSTGRES>"
.\gradlew -Pmatica.backendOnly=true :backend:api:run
```

La URL es un ejemplo de nombre: **verificar la URL efectiva devuelta por Render**, no asumir que está creada.

Para compilar Android con API Render:
```powershell
$env:MATICA_API_BASE_URL = "<URL_HTTPS_REAL_DEVUELTA_POR_RENDER>"
.\gradlew :apps:androidApp:assembleDebug
```

Opcionales para un futuro cliente de Supabase Auth (todavía no integrado):
```dotenv
MATICA_SUPABASE_URL=https://<project-ref>.supabase.co
MATICA_SUPABASE_PUBLISHABLE_KEY=sb_publishable_<CLAVE_PUBLICA>
```

La clave **publishable** se puede incluir en clientes cuando sus llamadas respetan RLS. La `service_role` y contraseña de la base **jamás** deben compilarse en APK ni colocarse en `NEXT_PUBLIC_*` o en un repositorio. Tampoco asumas que tener una clave publishable equivale a habilitar sincronización de usuario: aún falta implementar los endpoints autenticados para registrar intentos, XP, rachas y sincronización.

### 4. Pruebas de integración

1. `GET https://<render-domain>/healthz` → 200 (proceso vivo).
2. `GET https://<render-domain>/readyz` → 200 (PostgreSQL + migración v1, **no** solo proceso vivo).
3. `GET https://<render-domain>/v1/topics` → 200 con catálogo de temas.
4. `backend/database/verify.sql` → versión 1 y 11 tablas `rowsecurity=true`.
5. Comprobar que roles anónimos no leen tablas `matica`. 
6. Registrar un usuario de pruebas autenticado antes de intentar persistir progreso (el backend todavía no ofrece escritura de progreso).

## Estado real

**Este documento y el SQL no crean por sí mismos el proyecto cloud.** Los identificadores y claves reales deben venir de la operación de creación efectivamente completada en Supabase y Render. No generar ni inventar contraseñas ni exponer credenciales de otro proyecto.
