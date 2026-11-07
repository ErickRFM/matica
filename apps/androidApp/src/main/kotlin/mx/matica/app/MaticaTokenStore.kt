package mx.matica.app

import android.content.Context
import java.time.Instant
import java.time.OffsetDateTime
import mx.matica.presentation.app.AccountIdentity

/**
 * Sesión guardada en las preferencias privadas de la app (`allowBackup="false"`): el token no sale
 * del sandbox ni viaja a ninguna copia de seguridad. Si el token venció, se borra al leerlo.
 */
internal class MaticaTokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("matica_account", Context.MODE_PRIVATE)

    fun save(auth: ApiAuth) {
        prefs.edit()
            .putString(KEY_TOKEN, auth.token)
            .putString(KEY_EXPIRES, auth.expiresAt)
            .putString(KEY_ID, auth.user.id)
            .putString(KEY_EMAIL, auth.user.email)
            .putString(KEY_NAME, auth.user.displayName)
            .apply()
    }

    fun token(): String? {
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        if (expired()) {
            clear()
            return null
        }
        return token
    }

    fun identity(): AccountIdentity? {
        val token = token() ?: return null
        val id = prefs.getString(KEY_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val email = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() } ?: return null
        return AccountIdentity(id, email, prefs.getString(KEY_NAME, null))
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun expired(): Boolean {
        val raw = prefs.getString(KEY_EXPIRES, null) ?: return true
        val expiresAt = runCatching { Instant.parse(raw) }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant() }
            .getOrNull()
            ?: return true
        // Cinco minutos de margen: la comprobación sirve para no enviar un token ya muerto, y un
        // reloj de teléfono adelantado no debe cerrar la sesión antes de tiempo.
        return expiresAt.isBefore(Instant.now().minusSeconds(300))
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_EXPIRES = "expiresAt"
        const val KEY_ID = "userId"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "displayName"
    }
}
