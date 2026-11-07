package mx.matica.backend

import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.UUID

internal sealed interface AuthOutcome {
    data class Success(val token: String, val expiresAt: Instant, val user: User) : AuthOutcome
    data class Failure(val code: String, val message: String) : AuthOutcome
}

internal class AuthService(
    private val users: UserRepository,
    private val hasher: PasswordHasher,
    private val jwt: JwtCodec,
    private val throttle: LoginThrottle,
    private val clock: Clock,
) {
    // Hash señuelo: un correo inexistente tarda lo mismo que uno real con contraseña errada.
    private val dummyHash: String by lazy { hasher.hash("matica-dummy-password") }

    fun register(rawEmail: String, password: String, rawDisplayName: String?): AuthOutcome {
        val email = Validation.normalizeEmail(rawEmail)
            ?: return AuthOutcome.Failure("invalid_email", "Escribe un correo válido.")
        Validation.passwordProblem(password)?.let { return AuthOutcome.Failure("weak_password", it) }
        val user = users.create(
            email = email,
            normalizedEmail = email,
            displayName = Validation.displayName(rawDisplayName),
            passwordHash = hasher.hash(password),
        ) ?: return AuthOutcome.Failure("email_in_use", "Ese correo ya tiene una cuenta.")
        return success(user)
    }

    fun login(rawEmail: String, password: String): AuthOutcome {
        val email = Validation.normalizeEmail(rawEmail)
        if (email == null || password.isEmpty()) {
            return AuthOutcome.Failure("invalid_credentials", "Correo o contraseña incorrectos.")
        }
        if (throttle.isBlocked(email)) {
            return AuthOutcome.Failure("too_many_attempts", "Demasiados intentos. Espera unos minutos.")
        }
        val user = users.findByEmail(email)
        val valid = if (user == null) {
            hasher.verify(password, dummyHash)
            false
        } else {
            hasher.verify(password, user.passwordHash)
        }
        if (!valid || user == null) {
            throttle.recordFailure(email)
            return AuthOutcome.Failure("invalid_credentials", "Correo o contraseña incorrectos.")
        }
        throttle.clear(email)
        return success(user)
    }

    fun authenticate(token: String): JwtClaims? = jwt.verify(token, clock.instant())

    private fun success(user: User): AuthOutcome.Success {
        val now = clock.instant()
        return AuthOutcome.Success(
            token = jwt.issue(user.id, user.email, now),
            expiresAt = now.plus(jwt.ttl),
            user = user,
        )
    }
}

internal object InviteCodes {
    // Sin I, O, 0 ni 1: un código se dicta en voz alta y se copia a mano.
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    fun generate(random: SecureRandom = SecureRandom()): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    fun normalize(raw: String): String? {
        val value = raw.trim().uppercase()
        return value.takeIf { candidate ->
            candidate.length == LENGTH && candidate.all { it in ALPHABET }
        }
    }
}

internal class MaticaServices private constructor(
    val config: BackendConfig,
    val users: UserRepository?,
    val attempts: AttemptRepository?,
    val groups: GroupRepository?,
    val devices: DeviceTokenRepository?,
    val databaseReady: () -> Boolean,
    val clock: Clock,
    val symbolic: SymbolicForwarder,
    val fcm: FcmSender,
) {
    val hasher: PasswordHasher = PasswordHasher()
    val jwt: JwtCodec? = config.jwtKey()?.let { JwtCodec(it, ttl = config.tokenTtl) }
    val throttle: LoginThrottle = LoginThrottle(clock, config.loginMaxFailures, config.loginWindow)
    val auth: AuthService? = run {
        val users = users ?: return@run null
        val jwt = jwt ?: return@run null
        AuthService(users, hasher, jwt, throttle, clock)
    }

    val databaseAvailable: Boolean get() = users != null
    val authAvailable: Boolean get() = auth != null

    companion object {
        fun production(config: BackendConfig, database: Database?): MaticaServices = MaticaServices(
            config = config,
            users = database?.let(::JdbcUserRepository),
            attempts = database?.let(::JdbcAttemptRepository),
            groups = database?.let(::JdbcGroupRepository),
            devices = database?.let(::JdbcDeviceTokenRepository),
            databaseReady = { database?.ping() == true },
            clock = Clock.systemUTC(),
            symbolic = SymbolicForwarder(config.symbolicUrl, config.symbolicKey),
            fcm = FcmSender.fromServiceAccount(config.fcmServiceAccount),
        )

        /** Repositorios en memoria: las pruebas de rutas no necesitan PostgreSQL. */
        fun forTests(
            config: BackendConfig,
            users: UserRepository,
            attempts: AttemptRepository,
            groups: GroupRepository,
            devices: DeviceTokenRepository,
            databaseReady: () -> Boolean = { true },
            clock: Clock = Clock.systemUTC(),
            symbolic: SymbolicForwarder = SymbolicForwarder(null, null),
            fcm: FcmSender = FcmSender.disabled,
        ): MaticaServices = MaticaServices(
            config = config,
            users = users,
            attempts = attempts,
            groups = groups,
            devices = devices,
            databaseReady = databaseReady,
            clock = clock,
            symbolic = symbolic,
            fcm = fcm,
        )
    }
}
