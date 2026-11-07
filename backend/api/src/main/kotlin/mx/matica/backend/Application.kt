package mx.matica.backend

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

/**
 * Punto de entrada de Render.
 *
 * Arranca sin base ni secreto para que `/healthz` siempre responda, pero en cuanto encuentra
 * `DATABASE_URL` aplica las migraciones antes de aceptar tráfico: nunca sirve con un esquema a
 * medias. Un secreto JWT presente pero demasiado corto sí aborta el arranque: es una
 * configuración insegura, no una función opcional.
 */
fun main() {
    val logger = LoggerFactory.getLogger("mx.matica.backend")
    val config = BackendConfig.fromEnvironment()
    val database = config.databaseUrl?.let { Database.open(it) }
    if (database != null && config.migrateOnStart) {
        logger.info("Aplicando migraciones de PostgreSQL")
        database.migrate()
    }
    val services = MaticaServices.production(config, database)
    if (!services.databaseAvailable) {
        logger.warn("DATABASE_URL ausente: sólo salud y catálogo; cuentas e intentos responden 503")
    }
    if (!services.authAvailable) {
        logger.warn("MATICA_JWT_SECRET ausente: el registro y el login responden 503")
    }
    if (!services.symbolic.configured) {
        logger.info("MATICA_SYMBOLIC_URL/KEY ausentes: /v1/symbolic responde 503")
    }
    if (!services.fcm.configured) {
        logger.info("FCM sin cuenta de servicio: las notificaciones quedan apagadas")
    }

    Runtime.getRuntime().addShutdownHook(Thread { database?.close() })

    val port = System.getenv("PORT")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 10000
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = { maticaModule(services) })
        .start(wait = true)
}
