package mx.matica.backend

import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class JwtClaims(
    val userId: UUID,
    val email: String,
    val expiresAt: Instant,
    val role: String = "student",
    val isAnonymous: Boolean = false,
)

/**
 * JWT HS256 mínimo, sin plugins de autenticación: el único consumidor es este servicio y así el
 * formato queda probado con las mismas reglas que se verifican. El token no lleva más datos que
 * el identificador, el correo y la expiración.
 */
internal class JwtCodec(
    secret: ByteArray,
    private val issuer: String = "matica-api",
    val ttl: Duration = Duration.ofDays(30),
) {
    private val key = SecretKeySpec(secret, "HmacSHA256")
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val json = Json { ignoreUnknownKeys = true }

    fun issue(
        userId: UUID,
        email: String,
        now: Instant = Instant.now(),
        role: String = "student",
        isAnonymous: Boolean = false,
    ): String {
        val header = encoder.encodeToString(HEADER.toByteArray())
        val claims = encoder.encodeToString(
            json.encodeToString(Claims.serializer(), Claims(
                sub = userId.toString(),
                email = email,
                iss = issuer,
                iat = now.epochSecond,
                exp = now.plus(ttl).epochSecond,
                role = role,
                isAnonymous = isAnonymous,
            )).toByteArray(),
        )
        val signature = encoder.encodeToString(sign("$header.$claims"))
        return "$header.$claims.$signature"
    }

    fun verify(token: String, now: Instant = Instant.now()): JwtClaims? = try {
        if (token.length > MAX_TOKEN_LENGTH) return null
        val parts = token.split('.')
        if (parts.size != 3) return null
        val header = json.parseToJsonElement(String(decoder.decode(parts[0]))).jsonObject
        if (header["alg"]?.jsonPrimitive?.content != "HS256") return null
        val expected = sign("${parts[0]}.${parts[1]}")
        val provided = decoder.decode(parts[2])
        if (!MessageDigest.isEqual(expected, provided)) return null
        val claims = json.decodeFromString(Claims.serializer(), String(decoder.decode(parts[1])))
        if (claims.iss != issuer) return null
        if (claims.iat > now.epochSecond + CLOCK_SKEW_SECONDS) return null
        val expiresAt = Instant.ofEpochSecond(claims.exp)
        if (!expiresAt.isAfter(now)) return null
        val userId = UUID.fromString(claims.sub)
        JwtClaims(
            userId = userId,
            email = claims.email,
            expiresAt = expiresAt,
            role = claims.role,
            isAnonymous = claims.isAnonymous,
        )
    } catch (_: Exception) {
        null
    }

    private fun sign(payload: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(key)
            doFinal(payload.toByteArray())
        }

    @Serializable
    private data class Claims(
        val sub: String,
        val email: String,
        val iss: String,
        val iat: Long,
        val exp: Long,
        val role: String = "student",
        val isAnonymous: Boolean = false,
    )

    companion object {
        private const val HEADER = """{"alg":"HS256","typ":"JWT"}"""
        private const val MAX_TOKEN_LENGTH = 4_096
        private const val CLOCK_SKEW_SECONDS = 60L
    }
}

/**
 * Límite de intentos por correo. En memoria y por instancia: frena el ataque trivial, no sustituye
 * un WAF. Se reinicia solo al cumplirse la ventana.
 */
internal class LoginThrottle(
    private val clock: Clock = Clock.systemUTC(),
    private val maxFailures: Int = 10,
    private val window: Duration = Duration.ofMinutes(15),
) {
    private data class Entry(var failures: Int, var windowStart: Instant)

    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun isBlocked(email: String): Boolean {
        val entry = entries[email] ?: return false
        val now = clock.instant()
        if (Duration.between(entry.windowStart, now) >= window) {
            entries.remove(email)
            return false
        }
        return entry.failures >= maxFailures
    }

    @Synchronized
    fun recordFailure(email: String) {
        val now = clock.instant()
        val entry = entries[email]
        if (entry == null || Duration.between(entry.windowStart, now) >= window) {
            entries[email] = Entry(failures = 1, windowStart = now)
        } else {
            entry.failures += 1
        }
        if (entries.size > MAX_TRACKED) {
            entries.entries.removeIf { Duration.between(it.value.windowStart, now) >= window }
        }
    }

    @Synchronized
    fun clear(email: String) {
        entries.remove(email)
    }

    companion object {
        private const val MAX_TRACKED = 10_000
    }
}
