package mx.matica.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import mx.matica.app.db.MaticaDatabase
import mx.matica.presentation.app.AccountIdentity
import mx.matica.presentation.app.AccountResult
import mx.matica.presentation.app.ClassroomGroup
import mx.matica.presentation.app.GroupResult
import mx.matica.presentation.app.MaticaAccountGateway
import mx.matica.presentation.app.PendingAttempt

internal sealed interface SyncOutcome {
    /** No hay sesión o no hay nada que enviar. */
    data object Idle : SyncOutcome

    data object Done : SyncOutcome

    /** Falló algo transitorio (red o 5xx): vale la pena reintentar con espera. */
    data object Retry : SyncOutcome

    /** El token venció: la cola queda intacta y se vaciará al volver a entrar. */
    data object SignedOut : SyncOutcome
}

/**
 * Implementación Android del portal de cuenta. Los intentos se escriben primero en SQLite y sólo
 * después se pide al worker que los envíe; así un apagón de red no pierde evidencia ni bloquea
 * la práctica.
 */
internal class MaticaAccountRepository(
    private val context: Context,
    private val api: MaticaApiClient,
    private val store: MaticaTokenStore,
    private val database: MaticaDatabase,
) : MaticaAccountGateway {

    override fun restore(): AccountIdentity? = store.identity()

    override suspend fun register(email: String, password: String, displayName: String): AccountResult =
        authenticate(api.register(email, password, displayName.takeIf { it.isNotBlank() }))

    override suspend fun login(email: String, password: String): AccountResult =
        authenticate(api.login(email, password))

    private fun authenticate(result: ApiResult<ApiAuth>): AccountResult = when (result) {
        is ApiResult.Ok -> {
            store.save(result.value)
            scheduleSync(replace = false)
            AccountResult.Success(identityOf(result.value.user))
        }

        is ApiResult.Failure -> AccountResult.Failure(result.message)
    }

    override fun signOut() {
        store.clear()
    }

    override suspend fun createGroup(name: String): GroupResult = groupResult { token ->
        api.createGroup(token, name.trim())
    }

    override suspend fun joinGroup(code: String): GroupResult = groupResult { token ->
        api.joinGroup(token, code.trim().uppercase())
    }

    private suspend fun groupResult(block: suspend (String) -> ApiResult<ApiGroup>): GroupResult = when {
        store.identity() == null -> GroupResult.Failure("Entra a tu cuenta para usar el aula.")
        else -> when (val result = block(store.token() ?: return GroupResult.Failure("La sesión venció. Vuelve a entrar."))) {
            is ApiResult.Ok -> GroupResult.Success(groupOf(result.value))
            is ApiResult.Failure -> GroupResult.Failure(result.message)
        }
    }

    override suspend fun groups(): List<ClassroomGroup> {
        val token = store.token() ?: throw IllegalStateException("Sin sesión")
        return when (val result = api.groups(token)) {
            is ApiResult.Ok -> result.value.map(::groupOf)
            is ApiResult.Failure -> throw IllegalStateException(result.message)
        }
    }

    override fun enqueueAttempt(attempt: PendingAttempt): Int {
        val identity = store.identity() ?: return pendingAttempts()
        database.pendingAttemptsQueries.insertPending(
            id = attempt.id,
            user_id = identity.id,
            skill_id = attempt.skillId,
            exercise_id = attempt.exerciseId,
            before_text = attempt.before,
            after_text = attempt.after,
            verdict = attempt.verdict,
            used_hint = if (attempt.usedHint) 1L else 0L,
            occurred_at = Instant.now().toString(),
            created_at = System.currentTimeMillis(),
        )
        scheduleSync(replace = false)
        return pendingAttempts()
    }

    override fun pendingAttempts(): Int {
        val identity = store.identity() ?: return 0
        return database.pendingAttemptsQueries.countPendingForUser(identity.id).executeAsOne().toInt()
    }

    override fun requestSync() {
        scheduleSync(replace = true)
    }

    suspend fun syncPending(): SyncOutcome {
        val token = store.token() ?: return SyncOutcome.Idle
        val identity = store.identity() ?: return SyncOutcome.Idle
        // La poda es higiene, no requisito: si falla, el envío sigue su curso.
        runCatching {
            database.pendingAttemptsQueries.pruneOlderThan(
                System.currentTimeMillis() - Duration.ofDays(60).toMillis(),
            )
        }

        while (true) {
            val batch = database.pendingAttemptsQueries
                .selectPendingForUser(identity.id, BATCH_SIZE.toLong())
                .executeAsList()
            if (batch.isEmpty()) return SyncOutcome.Done
            for (row in batch) {
                val result = api.sendAttempt(
                    token,
                    api.attempt(
                        id = row.id,
                        skillId = row.skill_id,
                        exerciseId = row.exercise_id,
                        before = row.before_text,
                        after = row.after_text,
                        verdict = row.verdict,
                        usedHint = row.used_hint != 0L,
                        occurredAt = row.occurred_at,
                    ),
                )
                when (result) {
                    is ApiResult.Ok -> database.pendingAttemptsQueries.deletePending(row.id)

                    is ApiResult.Failure -> when {
                        // El token murió: no es culpa del intento; se conserva todo.
                        result.status == 401 -> {
                            store.clear()
                            return SyncOutcome.SignedOut
                        }
                        // 4xx es definitivo (dato inválido o id ajeno): reintentarlo en bucle no
                        // arreglaría nada y taparía la cola.
                        result.status in 400..499 -> database.pendingAttemptsQueries.deletePending(row.id)
                        else -> return SyncOutcome.Retry
                    }
                }
            }
        }
    }

    /**
     * Registra el token de FCM del teléfono para que el aula pueda avisar cuando alguien se une.
     * Sin sesión no hay a quién asociarlo, y el servidor exige Bearer: se devuelve `false` sin llamar.
     */
    suspend fun registerDeviceToken(token: String): Boolean {
        val device = token.trim()
        if (device.isEmpty()) return false
        val auth = store.token() ?: return false
        return api.registerDevice(auth, device) is ApiResult.Ok
    }

    private fun identityOf(user: ApiUser) = AccountIdentity(user.id, user.email, user.displayName)

    private fun groupOf(group: ApiGroup) = ClassroomGroup(
        id = group.id,
        name = group.name,
        inviteCode = group.inviteCode,
        role = group.role,
        memberCount = group.memberCount,
    )

    private fun scheduleSync(replace: Boolean) {
        if (!api.configured) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val SYNC_WORK = "matica-attempt-sync"
        const val BATCH_SIZE = 25
    }
}
