package mx.matica.backend

import java.time.Duration
import java.util.Base64

/**
 * Configuración leída del entorno de Render. Ninguna clave vive en el repositorio ni en el APK:
 * `DATABASE_URL` sólo la conoce el servicio, y las claves de servicio simbólico y FCM tampoco
 * viajan al teléfono.
 */
data class BackendConfig(
    val databaseUrl: String?,
    val jwtSecret: String?,
    val tokenTtl: Duration = Duration.ofDays(30),
    val symbolicUrl: String?,
    val symbolicKey: String?,
    val fcmServiceAccount: String?,
    val migrateOnStart: Boolean = true,
    val loginMaxFailures: Int = 10,
    val loginWindow: Duration = Duration.ofMinutes(15),
) {
    /**
     * Un secreto corto no se corrige solo: se rechaza al arrancar. Ausente sí es un estado válido
     * (sólo salud y catálogo), porque el servicio nunca debe requerir una clave para responder
     * `/healthz`.
     */
    fun jwtKey(): ByteArray? {
        val value = jwtSecret?.trim()
        if (value.isNullOrEmpty()) return null
        require(value.toByteArray().size >= MIN_SECRET_BYTES) {
            "MATICA_JWT_SECRET debe tener al menos $MIN_SECRET_BYTES bytes."
        }
        return value.toByteArray()
    }

    companion object {
        const val MIN_SECRET_BYTES: Int = 32

        fun fromEnvironment(): BackendConfig = BackendConfig(
            databaseUrl = System.getenv("DATABASE_URL")?.takeIf { it.isNotBlank() },
            jwtSecret = System.getenv("MATICA_JWT_SECRET"),
            symbolicUrl = System.getenv("MATICA_SYMBOLIC_URL")?.takeIf { it.isNotBlank() },
            symbolicKey = System.getenv("MATICA_SYMBOLIC_API_KEY")?.takeIf { it.isNotBlank() },
            fcmServiceAccount = System.getenv("MATICA_FCM_SERVICE_ACCOUNT")?.takeIf { it.isNotBlank() },
            // Sólo el literal «false» desactiva las migraciones; cualquier otro valor las deja puestas.
            migrateOnStart = System.getenv("MATICA_MIGRATE_ON_START")?.trim()?.lowercase() != "false",
        )

        /** Ayuda para generar el secreto sin copiarlo de un ejemplo público. */
        fun generateSecret(): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                ByteArray(48).also { java.security.SecureRandom().nextBytes(it) },
            )
    }
}
