package mx.matica.app

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Consulta anónima y de solo lectura. No lleva credenciales en el APK.
 * Una URL no configurada siempre deja el motor local disponible.
 */
internal object MaticaBackendProbe {
    suspend fun isAvailable(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (!baseUrl.startsWith("https://")) return@withContext false
        val connection = try {
            URL(baseUrl.trimEnd('/') + "/readyz").openConnection() as HttpURLConnection
        } catch (_: Exception) {
            return@withContext false
        }
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 3500
            connection.readTimeout = 3500
            connection.instanceFollowRedirects = false
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
