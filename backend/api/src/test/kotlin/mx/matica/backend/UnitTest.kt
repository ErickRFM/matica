package mx.matica.backend

import java.security.KeyPairGenerator
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtCodecTest {
    private val secret = "un-secreto-de-pruebas-con-32-bytes-o-mas".toByteArray()
    private val codec = JwtCodec(secret, ttl = Duration.ofMinutes(30))
    private val now = Instant.parse("2026-11-06T12:00:00Z")
    private val userId = UUID.randomUUID()

    @Test
    fun issuedTokenRoundTrips() {
        val token = codec.issue(userId, "ana@escuela.mx", now)
        val claims = codec.verify(token, now.plusSeconds(60))
        assertNotNull(claims)
        assertEquals(userId, claims.userId)
        assertEquals("ana@escuela.mx", claims.email)
        assertEquals(now.plus(Duration.ofMinutes(30)), claims.expiresAt)
    }

    @Test
    fun expiredTokenIsRejected() {
        val token = codec.issue(userId, "ana@escuela.mx", now)
        assertNull(codec.verify(token, now.plus(Duration.ofMinutes(31))))
    }

    @Test
    fun tamperedSignatureIsRejected() {
        val token = codec.issue(userId, "ana@escuela.mx", now)
        val tampered = token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'
        assertNull(codec.verify(tampered, now))
    }

    @Test
    fun anotherSecretCannotVerify() {
        val token = codec.issue(userId, "ana@escuela.mx", now)
        val other = JwtCodec("otro-secreto-distinto-con-32-bytes!!".toByteArray())
        assertNull(other.verify(token, now))
    }

    @Test
    fun malformedTokensAreRejected() {
        listOf("", "abc", "a.b.c", "a.b", "....").forEach { assertNull(codec.verify(it, now)) }
    }
}

class PasswordHasherTest {
    private val hasher = PasswordHasher(memoryKb = 1_024, iterations = 1)

    @Test
    fun hashVerifiesOnlyTheOriginalPassword() {
        val hash = hasher.hash("contrasena-larga")
        assertTrue(hash.startsWith("argon2id\$v=19\$m=1024,t=1,p=1\$"))
        assertTrue(hasher.verify("contrasena-larga", hash))
        assertFalse(hasher.verify("contrasena-largx", hash))
    }

    @Test
    fun saltsMakeHashesDifferent() {
        assertNotEquals(hasher.hash("contrasena-larga"), hasher.hash("contrasena-larga"))
    }

    @Test
    fun corruptHashesAreRejectedWithoutThrowing() {
        listOf("", "bcrypt\$2a\$...", "argon2id\$v=19\$m=x,t=3,p=1\$a\$b", "argon2id\$v=19\$m=1024,t=1,p=1\$\$").forEach {
            assertFalse(hasher.verify("contrasena-larga", it), "Aceptó un hash corrupto: $it")
        }
    }
}

class InviteCodesTest {
    @Test
    fun generatedCodesUseTheUnambiguousAlphabet() {
        repeat(200) {
            val code = InviteCodes.generate()
            assertEquals(InviteCodes.LENGTH, code.length)
            assertTrue(code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" }, "Código ambiguo: $code")
        }
    }

    @Test
    fun normalizationAcceptsLowercaseAndRejectsAmbiguousCharacters() {
        assertEquals("ABC234", InviteCodes.normalize(" abc234 "))
        assertNull(InviteCodes.normalize("ABCDE0"))
        assertNull(InviteCodes.normalize("ABCDEI"))
        assertNull(InviteCodes.normalize("ABC23"))
    }
}

class ValidationTest {
    @Test
    fun emailIsNormalizedAndValidated() {
        assertEquals("ana@escuela.mx", Validation.normalizeEmail("  Ana@Escuela.MX "))
        assertNull(Validation.normalizeEmail("sin-arroba"))
        assertNull(Validation.normalizeEmail("a@b"))
        assertNull(Validation.normalizeEmail(""))
    }

    @Test
    fun passwordLengthIsBounded() {
        assertNotNull(Validation.passwordProblem("corta"))
        assertNull(Validation.passwordProblem("contrasena-larga"))
        assertNotNull(Validation.passwordProblem("x".repeat(Validation.MAX_PASSWORD + 1)))
    }

    @Test
    fun classroomAliasesHideTheEmail() {
        assertEquals("a***@escuela.mx", Validation.maskEmail("ana@escuela.mx"))
        assertEquals("estudiante", Validation.maskEmail("sin-arroba"))
    }
}

class LoginThrottleTest {
    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
    }

    @Test
    fun blocksAfterTheLimitAndRecoversAfterTheWindow() {
        val clock = MutableClock(Instant.parse("2026-11-06T12:00:00Z"))
        val throttle = LoginThrottle(clock, maxFailures = 2, window = Duration.ofMinutes(15))
        throttle.recordFailure("ana@escuela.mx")
        assertFalse(throttle.isBlocked("ana@escuela.mx"))
        throttle.recordFailure("ana@escuela.mx")
        assertTrue(throttle.isBlocked("ana@escuela.mx"))
        clock.current = clock.current.plus(Duration.ofMinutes(16))
        assertFalse(throttle.isBlocked("ana@escuela.mx"))
    }

    @Test
    fun successClearsTheCounter() {
        val clock = MutableClock(Instant.parse("2026-11-06T12:00:00Z"))
        val throttle = LoginThrottle(clock, maxFailures = 2)
        repeat(2) { throttle.recordFailure("ana@escuela.mx") }
        assertTrue(throttle.isBlocked("ana@escuela.mx"))
        throttle.clear("ana@escuela.mx")
        assertFalse(throttle.isBlocked("ana@escuela.mx"))
    }
}

class FcmConfigurationTest {
    @Test
    fun absentOrMalformedServiceAccountsLeavePushDisabled() {
        assertFalse(FcmSender.fromServiceAccount(null).configured)
        assertFalse(FcmSender.fromServiceAccount("").configured)
        assertFalse(FcmSender.fromServiceAccount("no-es-json").configured)
        assertFalse(FcmSender.fromServiceAccount("""{"project_id":"solo-esto"}""").configured)
    }

    @Test
    fun aCompleteGeneratedServiceAccountIsAccepted() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2_048) }.generateKeyPair()
        val pem = buildString {
            append("-----BEGIN PRIVATE KEY-----\\n")
            append(Base64.getMimeEncoder().encodeToString(pair.private.encoded).replace("\n", "\\n"))
            append("\\n-----END PRIVATE KEY-----\\n")
        }
        val json = """
            {"project_id":"matica-test","client_email":"sender@matica-test.iam.gserviceaccount.com",
             "private_key":"$pem","token_uri":"https://oauth2.googleapis.com/token"}
        """.trimIndent()
        assertTrue(FcmSender.fromServiceAccount(json).configured)
    }
}
