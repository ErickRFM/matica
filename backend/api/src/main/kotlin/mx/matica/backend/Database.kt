package mx.matica.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import org.flywaydb.core.Flyway

/**
 * Pool único del proceso. La base de Mática es exclusiva del servicio; no se comparte con otros
 * proyectos y sólo se alcanza desde el backend, nunca desde el APK.
 */
internal class Database private constructor(private val dataSource: HikariDataSource) : AutoCloseable {

    fun <T> withConnection(block: (Connection) -> T): T = dataSource.connection.use(block)

    /** Migraciones idempotentes: Flyway registra lo aplicado en `flyway_schema_history`. */
    fun migrate() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    fun ping(): Boolean = try {
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.queryTimeout = 3
                statement.executeQuery("SELECT 1").use { result -> result.next() && result.getInt(1) == 1 }
            }
        }
    } catch (_: Exception) {
        false
    }

    override fun close() = dataSource.close()

    companion object {
        fun open(databaseUrl: String, maxPoolSize: Int = 4): Database {
            val details = PostgresProbe.details(databaseUrl)
            val config = HikariConfig().apply {
                jdbcUrl = details.jdbcUrl
                username = details.properties.getProperty("user")
                password = details.properties.getProperty("password")
                maximumPoolSize = maxPoolSize
                minimumIdle = 1
                connectionTimeout = 5_000
                validationTimeout = 3_000
                isAutoCommit = true
                poolName = "matica-api"
            }
            return Database(HikariDataSource(config))
        }
    }
}
