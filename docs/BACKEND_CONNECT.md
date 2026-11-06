# Mática — conexión inicial (B9)

## Estado verificado
- El APK mantiene matemáticas/ejercicios locales; la cuenta sigue desactivada hasta tener Auth real.
- El Docker construye sólo la API con `-Pmatica.backendOnly=true`; comparte el archivo `SkillId.kt` directamente con el motor y no necesita Android SDK.
- Backend `:backend:api`: `GET /healthz`, `GET /readyz`, `GET /v1/topics`. El catálogo sirve temas sin grados escolares.
- `/healthz` significa que responde el proceso; **no** certifica Postgres. `/readyz` devuelve 503 sin acceso JDBC.
- No existen aún endpoints de escritura, recompensas, sincronización ni login. No simular que existen.
- Un Render existente (`matica-symbolic-staging`) es **auxiliar SymPy**. No es este backend y no se debe sustituir.

## Variables por destino

| Dónde | Variable | Valor |
| --- | --- | --- |
| Render API (secreto) | `DATABASE_URL` | URL interna PostgreSQL de una base dedicada a Mática. No poner en Git/Android |
| Render API (automática) | `PORT` | Render la asigna; localmente usa 10000 |
| Android (pública, build) | `MATICA_API_BASE_URL` | URL HTTPS de tu servicio API, sin ruta de endpoint, p.ej. `https://matica-api-staging.onrender.com` |
| Gradle (alternativa a env) | `matica.api.baseUrl` | Misma URL pública con `-Pmatica.api.baseUrl=...` |

En Android sólo hay una sonda HTTPS anónima a `/readyz`; ningún secreto viaja en la app.
El servicio utiliza sólo `DATABASE_URL` para probar PostgreSQL; no aplica migraciones ni modifica una base existente.

## Arranque y validación

```bash
./gradlew -Pmatica.backendOnly=true :backend:api:test
./gradlew -Pmatica.backendOnly=true :backend:api:run
curl -i http://localhost:10000/healthz
curl -i http://localhost:10000/v1/topics
curl -i http://localhost:10000/readyz
# 503 esperado sin DATABASE_URL. No es error de /healthz.
```

Con Render, crea la base de datos **dedicada** en el workspace elegido y asigna su URL interna a `DATABASE_URL`. No uses las bases de SIII/Compañero. Confirma `/readyz=200` antes de activar sincronización real. Despliega la API con `backend/api/Dockerfile` o aplica `render.yaml` tras revisar plan/costo.

## Servicio simbólico ya existente

`matica-symbolic-staging` usa `MATICA_SYMBOLIC_API_KEY` **sólo** en ese servidor; el futuro backend Ktor deberá recibir el mismo valor de forma privada cuando se implemente su proxy autenticado. Esta PR todavía no configura ese proxy y no necesita la clave para compilar o responder `/v1/topics`.

## Política de interfaz
Los temas se nombran por su concepto (p.ej. Operaciones con enteros, Jerarquía de operaciones, Ecuaciones), nunca por etapa escolar. El dominio (en desarrollo, competente, fluido) es progreso personal, no un grado escolar.

## Pendiente sin fingir
Autenticación individual segura, migraciones Postgres, tablas y límites por usuario, persistencia local (SQLDelight), idempotencia de intentos y sincronización completa. El servicio SymPy requiere su token interno **sólo entre servidores**; no conectar directamente Android a `/v1/symbolic`.
