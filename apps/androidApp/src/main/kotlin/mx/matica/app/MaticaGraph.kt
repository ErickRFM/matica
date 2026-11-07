package mx.matica.app

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import mx.matica.app.db.MaticaDatabase

/**
 * Composición manual de la capa de datos. El worker de sincronización y la actividad comparten la
 * misma instancia para que la cola y el token siempre se lean del mismo sitio.
 */
internal class MaticaGraph private constructor(appContext: Context) {

    private val database: MaticaDatabase by lazy {
        MaticaDatabase(AndroidSqliteDriver(MaticaDatabase.Schema, appContext, DATABASE_NAME))
    }

    private val api = MaticaApiClient(BuildConfig.MATICA_API_BASE_URL)

    val account = MaticaAccountRepository(
        context = appContext,
        api = api,
        store = MaticaTokenStore(appContext),
        database = database,
    )

    val accountAvailable: Boolean = api.configured

    companion object {
        private const val DATABASE_NAME = "matica.db"

        @Volatile
        private var instance: MaticaGraph? = null

        fun from(context: Context): MaticaGraph = instance ?: synchronized(this) {
            instance ?: MaticaGraph(context.applicationContext).also { instance = it }
        }
    }
}
