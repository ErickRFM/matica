package mx.matica.presentation.app

/** Identidad visible de la cuenta. La contraseña nunca sale del formulario hacia el dominio. */
data class AccountIdentity(val id: String, val email: String, val displayName: String?)

data class ClassroomGroup(
    val id: String,
    val name: String,
    val inviteCode: String?,
    val role: String,
    val memberCount: Int,
)

/**
 * Un intento ya resuelto por el motor local, listo para la cola. `occurredAt` lo pone la capa de
 * plataforma: el dominio no depende de un reloj global para algo que sólo importa al sincronizar.
 */
data class PendingAttempt(
    val id: String,
    val skillId: String,
    val exerciseId: String,
    val before: String,
    val after: String,
    val verdict: String,
    val usedHint: Boolean,
)

sealed interface AccountResult {
    data class Success(val identity: AccountIdentity) : AccountResult
    data class Failure(val message: String) : AccountResult
}

sealed interface GroupResult {
    data class Success(val group: ClassroomGroup) : GroupResult
    data class Failure(val message: String) : GroupResult
}

/**
 * Frontera entre la presentación (multiplataforma, sin red) y la implementación Android. Todo lo
 * que puede fallar devuelve un mensaje en español ya pensado para mostrarse; la sesión no inventa
 * un estado de cuenta cuando el servidor no respondió.
 */
interface MaticaAccountGateway {
    fun restore(): AccountIdentity?

    suspend fun register(email: String, password: String, displayName: String): AccountResult

    suspend fun login(email: String, password: String): AccountResult

    fun signOut()

    suspend fun createGroup(name: String): GroupResult

    suspend fun joinGroup(code: String): GroupResult

    suspend fun groups(): List<ClassroomGroup>

    /** Guarda en el teléfono y devuelve cuántos intentos quedan pendientes. */
    fun enqueueAttempt(attempt: PendingAttempt): Int

    fun pendingAttempts(): Int

    fun requestSync()
}
