package mx.matica.backend

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mx.matica.math.skills.SkillId

@Serializable
data class ServiceStatus(val status: String, val service: String = "matica-api")

@Serializable
data class TopicItem(val id: String, val title: String)

@Serializable
data class TopicCatalog(val version: Int, val topics: List<TopicItem>)

/** Punto de entrada de Render. Nunca requiere una clave para arrancar. */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 10000
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::maticaModule).start(wait = true)
}

/**
 * API deliberadamente de sólo lectura hasta tener verificación de identidad y RLS/ACL.
 * Nunca se aceptan intentos o premios anónimos.
 */
fun Application.maticaModule(databaseReady: () -> Boolean = { PostgresProbe.ready(System.getenv("DATABASE_URL")) }) {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true; ignoreUnknownKeys = false })
    }
    routing {
        get("/healthz") { call.respond(ServiceStatus("ok")) }
        get("/readyz") {
            if (databaseReady()) call.respond(ServiceStatus("ready"))
            else call.respond(HttpStatusCode.ServiceUnavailable, ServiceStatus("database_unavailable"))
        }
        get("/v1/topics") {
            val topics = SkillId.entries.map { TopicItem(it.id, it.labelEs) }
            call.respond(TopicCatalog(version = 1, topics = topics))
        }
    }
}
