package mx.matica.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Vacía la cola de intentos cuando hay red. Fallo transitorio → `retry` con retroceso exponencial;
 * token vencido o datos inválidos → termina, porque repetir no cambiaría el resultado.
 */
internal class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = when (MaticaGraph.from(applicationContext).account.syncPending()) {
        SyncOutcome.Retry -> Result.retry()
        SyncOutcome.Idle, SyncOutcome.Done, SyncOutcome.SignedOut -> Result.success()
    }
}
