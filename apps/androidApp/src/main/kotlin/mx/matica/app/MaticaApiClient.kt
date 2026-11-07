package mx.matica.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** `status = 0` significa que la petición ni siquiera salió (sin red o URL sin configurar). */
    data class Failure(val message: String, val status: Int) : ApiResult<Nothing>
}

@Serializable
internal data class ApiUser(val id: String, val email: String, val displayName: String? = null)

@Serializable
internal data class ApiAuth(val token: String, val expiresAt: String, val user: ApiUser)

@Serializable
internal data class ApiGroup(
    val id: String,
    val name: String,
    val inviteCode: String? = null,
    val role: String,
    val memberCount: Int,
)

@Serializable
private data class ApiGroups(val groups: List<ApiGroup>)

@Serializable
private data class ApiLogin(val email: String, val password: String)

@Serializable
private data class ApiRegister(val email: String, val password: String, val displayName: String? = null)

@Serializable
private data class ApiCreateGroup(val name: String)

@Serializable
private data class ApiJoinGroup(val code: String)

@Serializable
private data class ApiDevice(val token: String, val platform: String)

@Serializable
internal data class ApiAttemptAccepted(val id: String, val status: String)

@Serializable
internal data class ApiAttempt(
    val id: String,
    val skillId: String,
    val exerciseId: String,
    val before: String,
    val after: String,
    val verdict: String,
    val usedHint: Boolean,
    val occurredAt: String,
)

@Serializable
private data class ApiError(val code: String = "", val message: String = "")

/**
 * Cliente HTTP de la app. La URL base es pública y se hornea al compilar; aquí no hay ninguna clave
 * del servidor. Cada método devuelve el resultado como dato, para que la cola local decida si
 * reintenta, descarta o detiene el envío sin excepciones sueltas.
 */
internal class MaticaApiClient(
    private val baseUrl: String,
    // Inyectable sólo para las pruebas de host; la app usa OkHttp.
    engine: HttpClientEngine = OkHttp.create(),
) {

    val configured: Boolean = baseUrl.startsWith("https://")

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
    }

    suspend fun register(email: String, password: String, displayName: String?): ApiResult<ApiAuth> =
        call(HttpMethod.Post, "/v1/auth/register", json.encodeToString(ApiRegister.serializer(), ApiRegister(email, password, displayName)))

    suspend fun login(email: String, password: String): ApiResult<ApiAuth> =
        call(HttpMethod.Post, "/v1/auth/login", json.encodeToString(ApiLogin.serializer(), ApiLogin(email, password)))

    suspend fun me(token: String): ApiResult<ApiUser> = call(HttpMethod.Get, "/v1/me", null, token)

    suspend fun sendAttempt(token: String, attempt: ApiAttempt): ApiResult<ApiAttemptAccepted> =
        call(HttpMethod.Post, "/v1/attempts", json.encodeToString(ApiAttempt.serializer(), attempt), token)

    suspend fun createGroup(token: String, name: String): ApiResult<ApiGroup> =
        call(HttpMethod.Post, "/v1/groups", json.encodeToString(ApiCreateGroup.serializer(), ApiCreateGroup(name)), token)

    suspend fun joinGroup(token: String, code: String): ApiResult<ApiGroup> =
        call(HttpMethod.Post, "/v1/groups/join", json.encodeToString(ApiJoinGroup.serializer(), ApiJoinGroup(code)), token)

    suspend fun groups(token: String): ApiResult<List<ApiGroup>> =
        when (val result = call<ApiGroups>(HttpMethod.Get, "/v1/groups", null, token)) {
            is ApiResult.Ok -> ApiResult.Ok(result.value.groups)
            is ApiResult.Failure -> result
        }

    /** El servidor responde 204 sin cuerpo; el cliente no tiene nada que decodificar. */
    suspend fun registerDevice(authToken: String, deviceToken: String): ApiResult<Unit> =
        when (val result = raw(
            HttpMethod.Post,
            "/v1/devices",
            json.encodeToString(ApiDevice.serializer(), ApiDevice(deviceToken, "android")),
            authToken,
        )) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Failure -> result
        }

    fun attempt(
        id: String,
        skillId: String,
        exerciseId: String,
        before: String,
        after: String,
        verdict: String,
        usedHint: Boolean,
        occurredAt: String,
    ): ApiAttempt = ApiAttempt(id, skillId, exerciseId, before, after, verdict, usedHint, occurredAt)

    private data class Raw(val text: String, val status: Int)

    private suspend inline fun <reified T> call(
        method: HttpMethod,
        path: String,
        body: String?,
        token: String? = null,
    ): ApiResult<T> = when (val result = raw(method, path, body, token)) {
        is ApiResult.Ok -> try {
            ApiResult.Ok(json.decodeFromString<T>(result.value.text))
        } catch (error: Exception) {
            ApiResult.Failure(
                "El servidor respondió en un formato que esta app no entiende.",
                result.value.status,
            )
        }

        is ApiResult.Failure -> result
    }

    private suspend fun raw(
        method: HttpMethod,
        path: String,
        body: String?,
        token: String? = null,
    ): ApiResult<Raw> {
        if (!configured) {
            return ApiResult.Failure(
                "Esta versión se compiló sin la URL del servidor, así que las cuentas no están disponibles.",
                0,
            )
        }
        val response = try {
            client.request(baseUrl.trimEnd('/') + path) {
                this.method = method
                token?.let { bearerAuth(it) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        } catch (error: Exception) {
            return ApiResult.Failure("No se alcanzó el servidor. Revisa tu conexión.", 0)
        }
        val text = try {
            response.bodyAsText()
        } catch (error: Exception) {
            return ApiResult.Failure("La respuesta del servidor llegó incompleta.", 0)
        }
        if (response.status.value !in 200..299) {
            val error = runCatching { json.decodeFromString(ApiError.serializer(), text) }.getOrNull()
            val message = error?.message?.takeIf { it.isNotBlank() } ?: fallbackMessage(response.status.value)
            return ApiResult.Failure(message, response.status.value)
        }
        return ApiResult.Ok(Raw(text, response.status.value))
    }

    private fun fallbackMessage(status: Int): String = when (status) {
        401 -> "La sesión venció. Vuelve a entrar con tu cuenta."
        403 -> "Esta cuenta no tiene permiso para hacer eso."
        404 -> "No se encontró ese recurso."
        409 -> "Ese dato ya existe."
        429 -> "Demasiados intentos seguidos. Espera unos minutos."
        in 500..599 -> "El servidor falló. Inténtalo más tarde."
        else -> "El servidor rechazó la petición ($status)."
    }
}
