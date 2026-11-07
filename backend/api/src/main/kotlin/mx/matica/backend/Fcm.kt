package mx.matica.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Envío HTTP v1 de Firebase. Sin `MATICA_FCM_SERVICE_ACCOUNT` queda apagado y el resto de la API
 * funciona igual: las notificaciones son un extra, no una dependencia del aula.
 */
internal interface FcmSender {
    val configured: Boolean
    suspend fun send(tokens: List<String>, title: String, body: String): Int

    companion object {
        val disabled: FcmSender = object : FcmSender {
            override val configured: Boolean = false
            override suspend fun send(tokens: List<String>, title: String, body: String): Int = 0
        }

        fun fromServiceAccount(rawJson: String?): FcmSender {
            if (rawJson.isNullOrBlank()) return disabled
            return try {
                FirebaseFcmSender(ServiceAccount.parse(rawJson))
            } catch (_: Exception) {
                disabled
            }
        }
    }
}

private data class ServiceAccount(
    val projectId: String,
    val clientEmail: String,
    val privateKey: String,
    val tokenUri: String,
) {
    companion object {
        fun parse(rawJson: String): ServiceAccount {
            val json = Json.parseToJsonElement(rawJson).jsonObject
            fun field(name: String): String =
                json[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: error("Falta $name en la cuenta de servicio")
            return ServiceAccount(
                projectId = field("project_id"),
                clientEmail = field("client_email"),
                privateKey = field("private_key"),
                tokenUri = json["token_uri"]?.jsonPrimitive?.content ?: "https://oauth2.googleapis.com/token",
            )
        }
    }
}

private class FirebaseFcmSender(private val account: ServiceAccount) : FcmSender {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
    }
    private val privateKey: PrivateKey = decodePrivateKey(account.privateKey)

    @Volatile private var accessToken: String? = null
    @Volatile private var accessTokenExpiry: Instant = Instant.EPOCH

    override val configured: Boolean = true

    override suspend fun send(tokens: List<String>, title: String, body: String): Int {
        if (tokens.isEmpty()) return 0
        val token = accessToken() ?: return 0
        var sent = 0
        tokens.forEach { deviceToken ->
            val payload = json.encodeToString(
                SendBody.serializer(),
                SendBody(Message(token = deviceToken, notification = Notification(title = title, body = body))),
            )
            val response = try {
                client.post("https://fcm.googleapis.com/v1/projects/${account.projectId}/messages:send") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            } catch (_: Exception) {
                return@forEach
            }
            if (response.status.value in 200..299) sent += 1
        }
        return sent
    }

    private suspend fun accessToken(): String? {
        val now = Instant.now()
        val cached = accessToken
        if (cached != null && now.isBefore(accessTokenExpiry.minusSeconds(60))) return cached
        val assertion = buildAssertion(now)
        val response = try {
            client.post(account.tokenUri) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                            append("assertion", assertion)
                        },
                    ),
                )
            }
        } catch (_: Exception) {
            return null
        }
        if (response.status.value !in 200..299) return null
        val body = response.bodyAsText()
        val parsed = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            return null
        }
        val value = parsed["access_token"]?.jsonPrimitive?.content ?: return null
        val ttl = parsed["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3_600L
        accessToken = value
        accessTokenExpiry = now.plusSeconds(ttl)
        return value
    }

    private fun buildAssertion(now: Instant): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val claims = encoder.encodeToString(
            buildString {
                append("{\"iss\":\"").append(account.clientEmail).append("\",")
                append("\"scope\":\"https://www.googleapis.com/auth/firebase.messaging\",")
                append("\"aud\":\"").append(account.tokenUri).append("\",")
                append("\"iat\":").append(now.epochSecond).append(',')
                append("\"exp\":").append(now.plusSeconds(3_600).epochSecond).append('}')
            }.toByteArray(),
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update("$header.$claims".toByteArray())
            sign()
        }
        return "$header.$claims.${encoder.encodeToString(signature)}"
    }

    private fun decodePrivateKey(pem: String): PrivateKey {
        val base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s"), "")
        return KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))
    }

    @Serializable
    private data class SendBody(val message: Message)

    @Serializable
    private data class Message(val token: String, val notification: Notification)

    @Serializable
    private data class Notification(val title: String, val body: String)
}
