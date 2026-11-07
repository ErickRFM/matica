package mx.matica.backend

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ApiTest {
    private val fixedNow = Instant.parse("2026-11-06T12:00:00Z")

    @Test
    fun topicsAreNamedWithoutSchoolGrades() = testApplication {
        application { maticaModule(fixture().services) }
        val response = client.get("/v1/topics")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Operaciones con enteros"))
        assertTrue(body.contains("\"version\":1"))
        listOf("primaria", "secundaria", "preparatoria", "bachillerato", "licenciatura", "universitario")
            .forEach { assertFalse(body.contains(it, ignoreCase = true), "Se filtró la etiqueta: $it") }
    }

    @Test
    fun healthIsNotDatabaseReadiness() = testApplication {
        val fixture = fixture()
        application {
            maticaModule(
                MaticaServices.forTests(
                    config = testConfig(),
                    users = fixture.users,
                    attempts = fixture.attempts,
                    groups = fixture.groups,
                    devices = fixture.devices,
                    databaseReady = { false },
                    clock = fixture.clock,
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, client.get("/healthz").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/readyz").status)
    }

    @Test
    fun readinessRequiresRealDatabase() = testApplication {
        application { maticaModule(fixture().services) }
        assertEquals(HttpStatusCode.OK, client.get("/readyz").status)
    }

    @Test
    fun missingInfrastructureNeverMeansReady() = testApplication {
        application { maticaModule(MaticaServices.production(testConfig(jwtSecret = null), database = null)) }
        assertEquals(HttpStatusCode.OK, client.get("/healthz").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/readyz").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/v1/auth/login").status)
    }

    @Test
    fun registerReturnsTokenAndMeEchoesTheAccount() = testApplication {
        application { maticaModule(fixture().services) }
        val token = client.register("ana@escuela.mx")
        val me = client.get("/v1/me") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, me.status)
        val user = me.bodyAsText().json()
        assertEquals("ana@escuela.mx", user["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun registrationRejectsDuplicateEmailWeakPasswordAndBadEmail() = testApplication {
        application { maticaModule(fixture().services) }
        client.register("ana@escuela.mx")
        assertEquals(
            HttpStatusCode.Conflict,
            client.registerRaw("ana@escuela.mx", "otra-contrasena").status,
        )
        assertEquals(HttpStatusCode.BadRequest, client.registerRaw("b@escuela.mx", "corta").status)
        assertEquals(HttpStatusCode.BadRequest, client.registerRaw("sin-arroba", "contrasena-larga").status)
    }

    @Test
    fun loginRejectsWrongPasswordAndThrottlesRepeatedFailures() = testApplication {
        val fixture = fixture(maxFailures = 3)
        application { maticaModule(fixture.services) }
        client.register("ana@escuela.mx")
        repeat(3) {
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.loginRaw("ana@escuela.mx", "equivocada").status,
            )
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.loginRaw("ana@escuela.mx", "contrasena-larga").status)
    }

    @Test
    fun attemptIsIdempotentAndListedOnce() = testApplication {
        application { maticaModule(fixture().services) }
        val token = client.register("ana@escuela.mx")
        val attemptId = UUID.randomUUID().toString()
        val payload = attemptJson(attemptId)

        val created = client.post("/v1/attempts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.Created, created.status)

        val duplicate = client.post("/v1/attempts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.OK, duplicate.status)
        assertEquals("duplicate", duplicate.bodyAsText().json()["status"]?.jsonPrimitive?.content)

        val listed = client.get("/v1/attempts") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, listed.status)
        val attempts = Json.parseToJsonElement(listed.bodyAsText()).jsonObject["attempts"]!!
            .jsonObjectArray()
        assertEquals(1, attempts.size)
    }

    @Test
    fun attemptRejectsUnknownSkillAndFutureTime() = testApplication {
        application { maticaModule(fixture().services) }
        val token = client.register("ana@escuela.mx")
        val unknownSkill = client.post("/v1/attempts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(attemptJson(UUID.randomUUID().toString(), skill = "astrologia"))
        }
        assertEquals(HttpStatusCode.BadRequest, unknownSkill.status)
        val future = client.post("/v1/attempts") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(attemptJson(UUID.randomUUID().toString(), occurredAt = "2030-01-01T00:00:00Z"))
        }
        assertEquals(HttpStatusCode.BadRequest, future.status)
    }

    @Test
    fun attemptsRequireAnAccount() = testApplication {
        application { maticaModule(fixture().services) }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/attempts") { setBody(attemptJson(UUID.randomUUID().toString())) }.status)
    }

    @Test
    fun groupInvitationJoinsAndShowsMembers() = testApplication {
        application { maticaModule(fixture().services) }
        val owner = client.register("docente@escuela.mx")
        val student = client.register("estudiante@escuela.mx")

        val created = client.post("/v1/groups") {
            header(HttpHeaders.Authorization, "Bearer $owner")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Álgebra 2A"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val group = created.bodyAsText().json()
        val groupId = group["id"]!!.jsonPrimitive.content
        val code = group["inviteCode"]!!.jsonPrimitive.content
        assertEquals("owner", group["role"]!!.jsonPrimitive.content)

        val joined = client.post("/v1/groups/join") {
            header(HttpHeaders.Authorization, "Bearer $student")
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$code"}""")
        }
        assertEquals(HttpStatusCode.OK, joined.status)
        assertNull(joined.bodyAsText().json()["inviteCode"]?.jsonPrimitive?.contentOrNull())

        val detail = client.get("/v1/groups/$groupId") { header(HttpHeaders.Authorization, "Bearer $owner") }
        assertEquals(HttpStatusCode.OK, detail.status)
        val body = detail.bodyAsText().json()
        assertEquals(2, body["group"]!!.jsonObject["memberCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, Json.parseToJsonElement(body["members"].toString()).jsonObjectArray().size)
    }

    @Test
    fun groupDetailIsHiddenFromNonMembers() = testApplication {
        application { maticaModule(fixture().services) }
        val owner = client.register("docente@escuela.mx")
        val outsider = client.register("ajeno@escuela.mx")
        val created = client.post("/v1/groups") {
            header(HttpHeaders.Authorization, "Bearer $owner")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Álgebra 2A"}""")
        }
        val groupId = created.bodyAsText().json()["id"]!!.jsonPrimitive.content
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/v1/groups/$groupId") { header(HttpHeaders.Authorization, "Bearer $outsider") }.status,
        )
    }

    @Test
    fun deviceTokenRegistrationIsAccepted() = testApplication {
        application { maticaModule(fixture().services) }
        val token = client.register("ana@escuela.mx")
        val response = client.post("/v1/devices") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"token":"fcm-token-de-prueba","platform":"android"}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun symbolicProxyIsOffUntilBothVariablesExist() = testApplication {
        application { maticaModule(fixture().services) }
        val token = client.register("ana@escuela.mx")
        val response = client.post("/v1/symbolic") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"capability":"simplify"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun accountsAreDisabledWithoutJwtSecret() = testApplication {
        application {
            val fixture = fixture(config = testConfig(jwtSecret = null))
            maticaModule(fixture.services)
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.registerRaw("ana@escuela.mx", "contrasena-larga").status)
    }

    @Test
    fun renderDatabaseUrlSupportsEncodedCredentials() {
        val details = PostgresProbe.details("postgresql://matica:p%40ss%2Bword@db.internal:5432/matica")
        assertEquals("jdbc:postgresql://db.internal:5432/matica", details.jdbcUrl)
        assertEquals("matica", details.properties.getProperty("user"))
        assertEquals("p@ss+word", details.properties.getProperty("password"))
    }

    @Test
    fun missingDatabaseUrlIsNeverReady() {
        assertFalse(PostgresProbe.ready(null))
        assertFalse(PostgresProbe.ready(""))
    }

    private fun fixture(
        clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
        config: BackendConfig = testConfig(),
        maxFailures: Int = 10,
    ): Fixture = Fixture(clock = clock, config = config.copy(loginMaxFailures = maxFailures))

    private fun attemptJson(id: String, skill: String = "integer-operations", occurredAt: String = "2026-11-06T11:55:00Z"): String =
        """
        {"id":"$id","skillId":"$skill","exerciseId":"integer-add-1","before":"2+2","after":"4",
         "verdict":"valid","usedHint":false,"occurredAt":"$occurredAt"}
        """.trimIndent()
}

private fun testConfig(
    jwtSecret: String? = "secreto-de-prueba-con-mas-de-32-bytes!!",
    maxFailures: Int = 10,
): BackendConfig = BackendConfig(
    databaseUrl = "postgresql://no-se-usa",
    jwtSecret = jwtSecret,
    symbolicUrl = null,
    symbolicKey = null,
    fcmServiceAccount = null,
    migrateOnStart = false,
    loginMaxFailures = maxFailures,
)

private class Fixture(
    val clock: Clock,
    config: BackendConfig,
) {
    val users = InMemoryUsers()
    val attempts = InMemoryAttempts()
    val groups = InMemoryGroups(users)
    val devices = InMemoryDevices()
    val services = MaticaServices.forTests(config, users, attempts, groups, devices, clock = clock)
}

private suspend fun HttpClient.registerRaw(email: String, password: String) = post("/v1/auth/register") {
    contentType(ContentType.Application.Json)
    setBody("""{"email":"$email","password":"$password"}""")
}

private suspend fun HttpClient.loginRaw(email: String, password: String) = post("/v1/auth/login") {
    contentType(ContentType.Application.Json)
    setBody("""{"email":"$email","password":"$password"}""")
}

private suspend fun HttpClient.register(email: String, password: String = "contrasena-larga"): String {
    val response = registerRaw(email, password)
    assertEquals(HttpStatusCode.Created, response.status)
    val token = response.bodyAsText().json()["token"]?.jsonPrimitive?.content
    assertNotNull(token)
    return token
}

private fun String.json(): JsonObject = Json.parseToJsonElement(this).jsonObject

private fun kotlinx.serialization.json.JsonElement.jsonObjectArray(): List<JsonObject> =
    (this as kotlinx.serialization.json.JsonArray).map { it.jsonObject }

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

private class InMemoryUsers : UserRepository {
    private val byId = linkedMapOf<UUID, User>()

    override fun findByEmail(normalizedEmail: String): User? =
        byId.values.firstOrNull { it.email == normalizedEmail }

    override fun findById(id: UUID): User? = byId[id]

    override fun create(
        email: String,
        normalizedEmail: String,
        displayName: String?,
        passwordHash: String,
    ): User? {
        if (findByEmail(normalizedEmail) != null) return null
        val user = User(UUID.randomUUID(), email, displayName, passwordHash)
        byId[user.id] = user
        return user
    }
}

private class InMemoryAttempts : AttemptRepository {
    private val rows = linkedMapOf<UUID, AttemptRecord>()

    override fun insert(record: AttemptRecord): AttemptInsert {
        val existing = rows[record.id]
        return when {
            existing == null -> {
                rows[record.id] = record
                AttemptInsert.Created
            }

            existing.userId == record.userId -> AttemptInsert.Duplicate
            else -> AttemptInsert.OtherOwner
        }
    }

    override fun list(userId: UUID, since: Instant?, limit: Int): List<AttemptRecord> =
        rows.values
            .filter { it.userId == userId && (since == null || it.occurredAt.isAfter(since)) }
            .sortedBy { it.occurredAt }
            .takeLast(limit)
}

private class InMemoryGroups(private val users: UserRepository) : GroupRepository {
    private val groups = linkedMapOf<UUID, GroupRecord>()
    private val members = mutableMapOf<UUID, MutableMap<UUID, String>>()

    override fun create(ownerId: UUID, name: String, inviteCode: String): GroupRecord? {
        if (groups.values.any { it.inviteCode == inviteCode }) return null
        val group = GroupRecord(UUID.randomUUID(), name, inviteCode, ownerId, memberCount = 1)
        groups[group.id] = group
        members[group.id] = mutableMapOf(ownerId to "owner")
        return group
    }

    override fun findByCode(code: String): GroupRecord? = groups.values.firstOrNull { it.inviteCode == code }

    override fun findById(id: UUID): GroupRecord? = groups[id]?.withCount()

    override fun membership(groupId: UUID, userId: UUID): GroupMembership? {
        val group = groups[groupId] ?: return null
        val role = members[groupId]?.get(userId) ?: return null
        return GroupMembership(group.withCount(), role)
    }

    override fun addMember(groupId: UUID, userId: UUID): Boolean =
        members.getOrPut(groupId) { mutableMapOf() }.putIfAbsent(userId, "student") == null

    override fun listFor(userId: UUID): List<GroupMembership> = groups.values
        .filter { members[it.id]?.containsKey(userId) == true }
        .map { GroupMembership(it.withCount(), members.getValue(it.id).getValue(userId)) }

    override fun members(groupId: UUID): List<GroupMemberRecord> = members[groupId]
        ?.mapNotNull { (userId, role) ->
            users.findById(userId)?.let {
                GroupMemberRecord(it.id, it.email, it.displayName, role, Instant.parse("2026-11-06T11:00:00Z"))
            }
        }
        .orEmpty()

    private fun GroupRecord.withCount(): GroupRecord = copy(memberCount = members[id]?.size ?: 0)
}

private class InMemoryDevices : DeviceTokenRepository {
    private val tokens = mutableMapOf<UUID, MutableSet<String>>()

    override fun upsert(userId: UUID, token: String, platform: String) {
        tokens.getOrPut(userId) { mutableSetOf() }.add(token)
    }

    override fun tokensFor(userId: UUID): List<String> = tokens[userId]?.toList().orEmpty()
}
