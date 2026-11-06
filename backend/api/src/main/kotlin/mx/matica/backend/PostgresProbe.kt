package mx.matica.backend

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.sql.DriverManager
import java.util.Properties

/**
 * Normaliza la URL interna de Render a JDBC sin escribir contraseñas en logs.
 * La base nunca se comparte con SIII ni Compañero.
 */
internal object PostgresProbe {
    data class ConnectionDetails(val jdbcUrl: String, val properties: Properties)

    fun details(databaseUrl: String): ConnectionDetails {
        if (databaseUrl.startsWith("jdbc:postgresql://")) {
            return ConnectionDetails(databaseUrl, Properties())
        }
        val uri = URI(databaseUrl)
        require(uri.scheme in setOf("postgres", "postgresql")) { "Se requiere PostgreSQL" }
        val host = requireNotNull(uri.host) { "Falta host de PostgreSQL" }
        val dbPath = uri.rawPath.takeIf { !it.isNullOrBlank() && it != "/" }
            ?: error("Falta el nombre de la base")
        val port = if (uri.port < 0) 5432 else uri.port
        require(port in 1..65535)
        val properties = Properties()
        uri.rawUserInfo?.split(":", limit = 2)?.let {
            if (it.isNotEmpty()) properties.setProperty("user", decode(it[0]))
            if (it.size == 2) properties.setProperty("password", decode(it[1]))
        }
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        return ConnectionDetails("jdbc:postgresql://$host:$port$dbPath$query", properties)
    }

    private fun decode(value: String) = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)

    fun ready(databaseUrl: String?): Boolean {
        if (databaseUrl.isNullOrBlank()) return false
        return try {
            val config = details(databaseUrl)
            DriverManager.getConnection(config.jdbcUrl, config.properties).use { connection ->
                connection.createStatement().use { statement ->
                    statement.queryTimeout = 3
                    // Health is not readiness: the real Mática schema must also exist.
                    statement.executeQuery("SELECT EXISTS (SELECT 1 FROM matica.schema_version WHERE version = 1)").use { result ->
                        result.next() && result.getBoolean(1)
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
