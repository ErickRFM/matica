package mx.matica.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal sealed interface ForwardResult {
    data class Completed(val status: Int, val body: String) : ForwardResult
    data class Unavailable(val reason: String) : ForwardResult
}

/**
 * El teléfono nunca conoce `MATICA_SYMBOLIC_API_KEY`: la petición pasa por aquí y sólo este
 * servicio habla con el motor simbólico. La URL y la clave son de entorno, no del cliente, así que
 * un usuario no puede redirigir la llamada a otro host.
 */
internal class SymbolicForwarder(
    private val baseUrl: String?,
    private val serviceKey: String?,
) {
    private val url: String? = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
    private val key: String? = serviceKey?.trim()?.takeIf { it.isNotEmpty() }

    val configured: Boolean get() = url != null && key != null

    init {
        if (url != null) {
            require(url.startsWith("https://") || url.startsWith("http://127.0.0.1")) {
                "MATICA_SYMBOLIC_URL debe ser HTTPS (o 127.0.0.1 en desarrollo)."
            }
        }
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 15_000
        }
    }

    suspend fun forward(jsonBody: String): ForwardResult {
        val url = this.url
        val key = this.key
        if (url == null || key == null) return ForwardResult.Unavailable("symbolic_not_configured")
        return try {
            val response = client.post("$url/v1/symbolic") {
                header("X-Matica-Service-Key", key)
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
            ForwardResult.Completed(response.status.value, response.bodyAsText())
        } catch (_: Exception) {
            ForwardResult.Unavailable("symbolic_unreachable")
        }
    }
}
