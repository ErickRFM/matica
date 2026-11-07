package mx.matica.app

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Contrato HTTP del cliente contra la forma que publica `backend/api`. El servidor real no se toca:
 * lo que se comprueba es que el JSON de aquí y el de allí se entiendan.
 */
class MaticaApiClientTest {

    private fun client(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "{}",
        onRequest: (String, String) -> Unit = { _, _ -> },
    ): MaticaApiClient {
        val engine = MockEngine { request ->
            val sent = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: ""
            onRequest(request.url.encodedPath, sent)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return MaticaApiClient("https://matica.test", engine)
    }

    @Test
    fun loginReadsTokenAndUser() = runBlocking {
        var path = ""
        val api = client(
            body = """{"token":"t0ken","expiresAt":"2026-12-06T12:00:00Z","user":{"id":"u-1","email":"a@b.mx","displayName":"Ana"}}""",
        ) { requestPath, _ -> path = requestPath }

        val result = api.login("a@b.mx", "secreto123")

        assertEquals("/v1/auth/login", path)
        val auth = (result as ApiResult.Ok).value
        assertEquals("t0ken", auth.token)
        assertEquals("u-1", auth.user.id)
        assertEquals("Ana", auth.user.displayName)
    }

    @Test
    fun serverMessageWinsOverGenericText() = runBlocking {
        val api = client(
            status = HttpStatusCode.Conflict,
            body = """{"code":"email_in_use","message":"Ese correo ya tiene cuenta."}""",
        )

        val result = api.register("a@b.mx", "secreto123", null) as ApiResult.Failure

        assertEquals(409, result.status)
        assertEquals("Ese correo ya tiene cuenta.", result.message)
    }

    @Test
    fun serverFailureWithoutBodyStillHasSpanishText() = runBlocking {
        val api = client(status = HttpStatusCode.InternalServerError, body = "sin json")

        val result = api.login("a@b.mx", "secreto123") as ApiResult.Failure

        assertEquals(500, result.status)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun attemptSendsHintFlagAndIdempotentId() = runBlocking {
        var sent = ""
        val api = client(
            status = HttpStatusCode.Created,
            body = """{"id":"7d0f9a4a-7f0b-4c39-9b0e-7d6c4c1e0a11","status":"created"}""",
        ) { _, body -> sent = body }

        val result = api.sendAttempt(
            "jwt",
            api.attempt(
                id = "7d0f9a4a-7f0b-4c39-9b0e-7d6c4c1e0a11",
                skillId = "integer-operations",
                exerciseId = "ex-1",
                before = "2+2",
                after = "4",
                verdict = "valid",
                usedHint = true,
                occurredAt = "2026-11-06T12:00:00Z",
            ),
        )

        assertTrue(result is ApiResult.Ok)
        assertTrue(sent.contains("\"usedHint\":true"))
        assertTrue(sent.contains("\"id\":\"7d0f9a4a-7f0b-4c39-9b0e-7d6c4c1e0a11\""))
        assertTrue(sent.contains("\"verdict\":\"valid\""))
    }

    @Test
    fun groupsUnwrapTheListEnvelope() = runBlocking {
        val api = client(
            body = """{"groups":[{"id":"g1","name":"3.º B","role":"owner","memberCount":4,"inviteCode":"ABC234"}]}""",
        )

        val result = api.groups("jwt") as ApiResult.Ok

        assertEquals(1, result.value.size)
        assertEquals("ABC234", result.value.first().inviteCode)
    }

    @Test
    fun deviceRegistrationSendsTokenAndPlatform() = runBlocking {
        var path = ""
        var sent = ""
        val api = client(status = HttpStatusCode.NoContent, body = "") { requestPath, body ->
            path = requestPath
            sent = body
        }

        val result = api.registerDevice("jwt", "fcm-device-token")

        assertEquals("/v1/devices", path)
        assertTrue(sent.contains("\"token\":\"fcm-device-token\""))
        assertTrue(sent.contains("\"platform\":\"android\""))
        assertTrue(result is ApiResult.Ok)
    }

    @Test
    fun buildWithoutUrlNeverTouchesTheNetwork() = runBlocking {
        var calls = 0
        val api = MaticaApiClient(
            "",
            MockEngine {
                calls += 1
                respond("{}", HttpStatusCode.OK)
            },
        )

        val result = api.login("a@b.mx", "secreto123") as ApiResult.Failure

        assertEquals(0, calls)
        assertEquals(0, result.status)
        assertTrue(result.message.contains("sin la URL del servidor"))
    }
}
