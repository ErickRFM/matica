package mx.matica.backend

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mx.matica.math.skills.SkillId
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("mx.matica.backend")

private const val MAX_SYMBOLIC_BODY = 65_536
private const val MAX_ATTEMPT_WINDOW_DAYS = 90L
private val SYMBOLIC_CAPABILITIES = setOf("simplify", "isEquivalent", "solveEquation", "classifyStep")

internal fun Application.maticaModule(services: MaticaServices) {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true; ignoreUnknownKeys = false })
    }
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_input", "El cuerpo de la petición no es válido."))
        }
        exception<Throwable> { call, cause ->
            // Sin trazas ni cuerpos del estudiante en la respuesta.
            log.error("Fallo no controlado en ${call.request.local.method.value} ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal", "Fallo interno del servidor."))
        }
    }
    routing {
        get("/healthz") { call.respond(ServiceStatus("ok")) }
        get("/readyz") {
            if (services.databaseReady()) call.respond(ServiceStatus("ready"))
            else call.respond(HttpStatusCode.ServiceUnavailable, ServiceStatus("database_unavailable"))
        }
        get("/v1/topics") {
            val topics = SkillId.entries.map { TopicItem(it.id, it.labelEs) }
            call.respond(TopicCatalog(version = 1, topics = topics))
        }
        authRoutes(services)
        attemptRoutes(services)
        groupRoutes(services)
        deviceRoutes(services)
        symbolicRoutes(services)
    }
}

private fun Route.authRoutes(services: MaticaServices) {
    post("/v1/auth/register") {
        if (!call.requireDatabase(services)) return@post
        val auth = services.auth
            ?: return@post call.fail(HttpStatusCode.ServiceUnavailable, "auth_unavailable", "Las cuentas no están configuradas.")
        val body = call.receiveJsonOrNull<RegisterRequest>()
            ?: return@post call.fail(HttpStatusCode.BadRequest, "invalid_input", "Faltan correo o contraseña.")
        val outcome = withContext(Dispatchers.IO) { auth.register(body.email, body.password, body.displayName) }
        call.respondAuth(outcome, HttpStatusCode.Created)
    }

    post("/v1/auth/login") {
        if (!call.requireDatabase(services)) return@post
        val auth = services.auth
            ?: return@post call.fail(HttpStatusCode.ServiceUnavailable, "auth_unavailable", "Las cuentas no están configuradas.")
        val body = call.receiveJsonOrNull<LoginRequest>()
            ?: return@post call.fail(HttpStatusCode.BadRequest, "invalid_input", "Faltan correo o contraseña.")
        val outcome = withContext(Dispatchers.IO) { auth.login(body.email, body.password) }
        call.respondAuth(outcome, HttpStatusCode.OK)
    }

    get("/v1/me") {
        val claims = call.requireClaims(services) ?: return@get
        if (!call.requireDatabase(services)) return@get
        val users = services.users ?: return@get
        val user = withContext(Dispatchers.IO) { users.findById(claims.userId) }
            ?: return@get call.fail(HttpStatusCode.Unauthorized, "session_invalid", "La cuenta ya no existe.")
        call.respond(UserDto(user.id.toString(), user.email, user.displayName))
    }
}

private fun Route.attemptRoutes(services: MaticaServices) {
    post("/v1/attempts") {
        val claims = call.requireClaims(services) ?: return@post
        if (!call.requireDatabase(services)) return@post
        val repository = services.attempts ?: return@post
        val body = call.receiveJsonOrNull<AttemptRequest>()
            ?: return@post call.fail(HttpStatusCode.BadRequest, "invalid_input", "El intento no tiene el formato esperado.")

        val id = body.id.toUuidOrNull()
            ?: return@post call.fail(HttpStatusCode.BadRequest, "invalid_attempt_id", "El id del intento debe ser un UUID.")
        if (!Validation.validSkill(body.skillId)) {
            return@post call.fail(HttpStatusCode.BadRequest, "unknown_skill", "La habilidad '${body.skillId}' no existe.")
        }
        if (!Validation.validVerdict(body.verdict)) {
            return@post call.fail(HttpStatusCode.BadRequest, "unknown_verdict", "El veredicto '${body.verdict}' no es parte del contrato.")
        }
        if (body.exerciseId.isBlank() || body.exerciseId.length > Validation.MAX_EXERCISE_ID) {
            return@post call.fail(HttpStatusCode.BadRequest, "invalid_exercise", "El identificador del ejercicio está vacío o es demasiado largo.")
        }
        if (body.before.isBlank() || body.after.isBlank() ||
            body.before.length > Validation.MAX_LINE || body.after.length > Validation.MAX_LINE
        ) {
            return@post call.fail(HttpStatusCode.BadRequest, "invalid_step", "Cada línea del paso debe tener entre 1 y ${Validation.MAX_LINE} caracteres.")
        }
        val occurredAt = body.occurredAt.toInstantOrNull()
            ?: return@post call.fail(HttpStatusCode.BadRequest, "invalid_time", "La fecha del intento no es ISO-8601.")
        val now = services.clock.instant()
        if (occurredAt.isAfter(now.plusSeconds(300)) || occurredAt.isBefore(now.minusSeconds(Duration.ofDays(MAX_ATTEMPT_WINDOW_DAYS).seconds))) {
            return@post call.fail(HttpStatusCode.BadRequest, "invalid_time", "La fecha del intento está fuera de la ventana aceptada.")
        }

        val record = AttemptRecord(
            id = id,
            userId = claims.userId,
            skillId = body.skillId,
            exerciseId = body.exerciseId,
            before = body.before,
            after = body.after,
            verdict = body.verdict,
            usedHint = body.usedHint,
            occurredAt = occurredAt,
        )
        when (withContext(Dispatchers.IO) { repository.insert(record) }) {
            AttemptInsert.Created -> call.respond(HttpStatusCode.Created, AttemptAccepted(id.toString(), "created"))
            AttemptInsert.Duplicate -> call.respond(HttpStatusCode.OK, AttemptAccepted(id.toString(), "duplicate"))
            AttemptInsert.OtherOwner ->
                call.fail(HttpStatusCode.Conflict, "attempt_conflict", "Ese identificador ya pertenece a otro intento.")
        }
    }

    get("/v1/attempts") {
        val claims = call.requireClaims(services) ?: return@get
        if (!call.requireDatabase(services)) return@get
        val repository = services.attempts ?: return@get
        val since = call.request.queryParameters["since"]?.let {
            it.toInstantOrNull() ?: return@get call.fail(HttpStatusCode.BadRequest, "invalid_time", "'since' no es ISO-8601.")
        }
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 200
        val attempts = withContext(Dispatchers.IO) { repository.list(claims.userId, since, limit) }
        call.respond(
            AttemptsResponse(
                attempts = attempts.map { it.toDto() },
                serverTime = services.clock.instant().toString(),
            ),
        )
    }
}

private fun Route.groupRoutes(services: MaticaServices) {
    post("/v1/groups") {
        val claims = call.requireClaims(services) ?: return@post
        if (!call.requireDatabase(services)) return@post
        val repository = services.groups ?: return@post
        val body = call.receiveJsonOrNull<CreateGroupRequest>()
            ?: return@post call.fail(HttpStatusCode.BadRequest, "invalid_input", "Falta el nombre del grupo.")
        val name = body.name.trim()
        if (name.isEmpty() || name.length > Validation.MAX_GROUP_NAME) {
            return@post call.fail(HttpStatusCode.BadRequest, "invalid_name", "El nombre debe tener entre 1 y ${Validation.MAX_GROUP_NAME} caracteres.")
        }
        val group = withContext(Dispatchers.IO) {
            repeat(MAX_CODE_ATTEMPTS) {
                repository.create(claims.userId, name, InviteCodes.generate())?.let { return@withContext it }
            }
            null
        } ?: return@post call.fail(HttpStatusCode.InternalServerError, "group_not_created", "No se pudo reservar un código de invitación.")
        call.respond(
            HttpStatusCode.Created,
            GroupDto(group.id.toString(), group.name, group.inviteCode, "owner", group.memberCount),
        )
    }

    post("/v1/groups/join") {
        val claims = call.requireClaims(services) ?: return@post
        if (!call.requireDatabase(services)) return@post
        val repository = services.groups ?: return@post
        val body = call.receiveJsonOrNull<JoinGroupRequest>()
            ?: return@post call.fail(HttpStatusCode.BadRequest, "invalid_input", "Falta el código de invitación.")
        val code = InviteCodes.normalize(body.code)
            ?: return@post call.fail(HttpStatusCode.NotFound, "group_not_found", "Ese código no corresponde a ningún grupo.")
        val group = withContext(Dispatchers.IO) { repository.findByCode(code) }
            ?: return@post call.fail(HttpStatusCode.NotFound, "group_not_found", "Ese código no corresponde a ningún grupo.")
        val created = withContext(Dispatchers.IO) { repository.addMember(group.id, claims.userId) }
        val membership = withContext(Dispatchers.IO) { repository.membership(group.id, claims.userId) }
            ?: return@post call.fail(HttpStatusCode.InternalServerError, "group_not_joined", "El grupo no admitió la membresía.")
        call.respond(membership.dto())

        if (created) call.notifyOwner(services, group, claims)
    }

    get("/v1/groups") {
        val claims = call.requireClaims(services) ?: return@get
        if (!call.requireDatabase(services)) return@get
        val repository = services.groups ?: return@get
        val groups = withContext(Dispatchers.IO) { repository.listFor(claims.userId) }
        call.respond(GroupsResponse(groups.map { it.dto() }))
    }

    get("/v1/groups/{id}") {
        val claims = call.requireClaims(services) ?: return@get
        if (!call.requireDatabase(services)) return@get
        val repository = services.groups ?: return@get
        val id = call.parameters["id"]?.toUuidOrNull()
            ?: return@get call.fail(HttpStatusCode.NotFound, "group_not_found", "El grupo no existe.")
        val membership = withContext(Dispatchers.IO) { repository.membership(id, claims.userId) }
            ?: return@get call.fail(HttpStatusCode.NotFound, "group_not_found", "El grupo no existe o no perteneces a él.")
        val members = withContext(Dispatchers.IO) { repository.members(id) }
        call.respond(
            GroupDetailDto(
                group = membership.dto(),
                members = members.map {
                    GroupMemberDto(
                        displayName = it.displayName ?: Validation.maskEmail(it.email),
                        role = it.role,
                        joinedAt = it.joinedAt.toString(),
                    )
                },
            ),
        )
    }
}

private fun Route.deviceRoutes(services: MaticaServices) {
    post("/v1/devices") {
        val claims = call.requireClaims(services) ?: return@post
        if (!call.requireDatabase(services)) return@post
        val repository = services.devices ?: return@post
        val body = call.receiveJsonOrNull<DeviceRequest>()
            ?: return@post call.fail(HttpStatusCode.BadRequest, "invalid_input", "Falta el token del dispositivo.")
        val token = body.token.trim()
        if (token.isEmpty() || token.length > Validation.MAX_DEVICE_TOKEN) {
            return@post call.fail(HttpStatusCode.BadRequest, "invalid_token", "El token del dispositivo no es válido.")
        }
        if (body.platform != "android") {
            return@post call.fail(HttpStatusCode.BadRequest, "unsupported_platform", "Sólo se registran dispositivos Android.")
        }
        withContext(Dispatchers.IO) { repository.upsert(claims.userId, token, body.platform) }
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.symbolicRoutes(services: MaticaServices) {
    post("/v1/symbolic") {
        call.requireClaims(services) ?: return@post
        val declared = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declared != null && declared > MAX_SYMBOLIC_BODY) {
            return@post call.fail(HttpStatusCode.PayloadTooLarge, "payload_too_large", "La expresión supera el límite del contrato.")
        }
        val body = call.receiveText()
        if (body.toByteArray().size > MAX_SYMBOLIC_BODY) {
            return@post call.fail(HttpStatusCode.PayloadTooLarge, "payload_too_large", "La expresión supera el límite del contrato.")
        }
        val capability = try {
            Json.parseToJsonElement(body).jsonObject["capability"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
        if (capability !in SYMBOLIC_CAPABILITIES) {
            return@post call.fail(HttpStatusCode.BadRequest, "unknown_capability", "La capacidad pedida no pertenece al contrato.")
        }
        when (val result = services.symbolic.forward(body)) {
            is ForwardResult.Completed ->
                call.respondText(result.body, ContentType.Application.Json, HttpStatusCode.fromValue(result.status))
            is ForwardResult.Unavailable ->
                call.fail(HttpStatusCode.ServiceUnavailable, result.reason, "El cálculo simbólico no está disponible.")
        }
    }
}

private suspend fun ApplicationCall.respondAuth(outcome: AuthOutcome, successStatus: HttpStatusCode) {
    when (outcome) {
        is AuthOutcome.Success -> respond(
            successStatus,
            AuthResponse(
                token = outcome.token,
                expiresAt = outcome.expiresAt.toString(),
                user = UserDto(outcome.user.id.toString(), outcome.user.email, outcome.user.displayName),
            ),
        )

        is AuthOutcome.Failure -> fail(outcome.status(), outcome.code, outcome.message)
    }
}

private fun AuthOutcome.Failure.status(): HttpStatusCode = when (code) {
    "email_in_use" -> HttpStatusCode.Conflict
    "invalid_credentials" -> HttpStatusCode.Unauthorized
    "too_many_attempts" -> HttpStatusCode.TooManyRequests
    else -> HttpStatusCode.BadRequest
}

private suspend fun ApplicationCall.requireClaims(services: MaticaServices): JwtClaims? {
    val auth = services.auth
    if (auth == null) {
        fail(HttpStatusCode.ServiceUnavailable, "auth_unavailable", "Las cuentas no están configuradas.")
        return null
    }
    val header = request.headers[HttpHeaders.Authorization]
    val token = header
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substringAfter(' ', "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (token == null) {
        fail(HttpStatusCode.Unauthorized, "unauthorized", "Falta el token de la cuenta.")
        return null
    }
    return auth.authenticate(token) ?: run {
        fail(HttpStatusCode.Unauthorized, "unauthorized", "El token venció o no es válido.")
        null
    }
}

private suspend fun ApplicationCall.requireDatabase(services: MaticaServices): Boolean {
    if (services.databaseAvailable) return true
    fail(HttpStatusCode.ServiceUnavailable, "database_unavailable", "El servicio de cuentas no tiene base de datos conectada.")
    return false
}

private suspend fun ApplicationCall.fail(status: HttpStatusCode, code: String, message: String) {
    respond(status, ErrorResponse(code, message))
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveJsonOrNull(): T? = try {
    receive<T>()
} catch (error: Exception) {
    if (error is CancellationException) throw error
    null
}

private fun GroupMembership.dto() = GroupDto(
    id = group.id.toString(),
    name = group.name,
    inviteCode = group.inviteCode.takeIf { role == "owner" },
    role = role,
    memberCount = group.memberCount,
)

private fun AttemptRecord.toDto() = AttemptDto(
    id = id.toString(),
    skillId = skillId,
    exerciseId = exerciseId,
    before = before,
    after = after,
    verdict = verdict,
    usedHint = usedHint,
    occurredAt = occurredAt.toString(),
)

private fun String.toUuidOrNull(): UUID? = try {
    UUID.fromString(this)
} catch (_: Exception) {
    null
}

private fun String.toInstantOrNull(): Instant? = try {
    OffsetDateTime.parse(this).toInstant()
} catch (_: Exception) {
    try {
        Instant.parse(this)
    } catch (_: Exception) {
        null
    }
}

/** Aviso al docente, sin bloquear la respuesta ni romper la unión si FCM no está configurado. */
private fun ApplicationCall.notifyOwner(services: MaticaServices, group: GroupRecord, joiner: JwtClaims) {
    if (!services.fcm.configured) return
    val devices = services.devices ?: return
    val users = services.users ?: return
    val application = application
    application.launch {
        try {
            val tokens = withContext(Dispatchers.IO) { devices.tokensFor(group.ownerId) }
            if (tokens.isEmpty()) return@launch
            val alias = withContext(Dispatchers.IO) { users.findById(joiner.userId) }?.displayName
                ?: Validation.maskEmail(joiner.email)
            services.fcm.send(tokens, "Nuevo integrante", "$alias se unió a ${group.name}")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            log.warn("No se pudo avisar al docente del grupo ${group.id}", error)
        }
    }
}

private const val MAX_CODE_ATTEMPTS = 6
