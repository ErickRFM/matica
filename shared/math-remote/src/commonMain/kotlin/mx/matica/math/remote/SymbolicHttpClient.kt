package mx.matica.math.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import mx.matica.math.contract.SymbolicJson

/**
 * Ajustes comunes del cliente del servicio de cálculo ampliado. Viven aquí, y no en
 * `:shared:math-engine`, para que el motor offline siga sin dependencias de red.
 *
 * `expectSuccess = false`: el servicio devuelve 422 y 504 con un sobre válido del contrato, y saber
 * qué rechazo fue vale más que una excepción genérica de transporte.
 */
fun HttpClientConfig<*>.applySymbolicClientConfig(baseUrl: String, requestTimeout: Duration) {
    expectSuccess = false
    defaultRequest { url(baseUrl) }
    install(ContentNegotiation) {
        json(SymbolicJson.instance)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = requestTimeout.inWholeMilliseconds
    }
}

fun symbolicHttpClient(
    engineFactory: HttpClientEngineFactory<*>,
    baseUrl: String,
    requestTimeout: Duration = 10.seconds,
): HttpClient = HttpClient(engineFactory) {
    applySymbolicClientConfig(baseUrl, requestTimeout)
}

fun symbolicHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    requestTimeout: Duration = 10.seconds,
): HttpClient = HttpClient(engine) {
    applySymbolicClientConfig(baseUrl, requestTimeout)
}
