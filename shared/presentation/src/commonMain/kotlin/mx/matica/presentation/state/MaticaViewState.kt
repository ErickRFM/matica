package mx.matica.presentation.state

/**
 * Los cinco estados que toda pantalla de Mática debe poder mostrar (B3-03).
 *
 * `Offline` va separado de `Error` a propósito: sin conexión el estudiante puede seguir trabajando
 * con el motor local, y confundir ambos estados le haría cerrar una sesión que sí funciona.
 */
sealed interface MaticaViewState<out T> {

    data object Loading : MaticaViewState<Nothing>

    data class Empty(
        val title: String,
        val description: String,
        val retryLabel: String = RETRY_LABEL,
    ) : MaticaViewState<Nothing>

    data class Error(val title: String, val detail: String, val retryLabel: String = RETRY_LABEL) : MaticaViewState<Nothing>

    data class Offline(val title: String, val detail: String) : MaticaViewState<Nothing>

    data class Success<T>(val data: T) : MaticaViewState<T>

    companion object {
        const val RETRY_LABEL = "Reintentar"

        fun <T> success(data: T): MaticaViewState<T> = Success(data)
    }
}

/** Texto accesible único por estado; las pruebas exigen que ninguno quede vacío. */
fun MaticaViewState<*>.announceAs(): String = when (this) {
    is MaticaViewState.Loading -> "Cargando"
    is MaticaViewState.Empty -> description
    is MaticaViewState.Error -> detail
    is MaticaViewState.Offline -> detail
    is MaticaViewState.Success -> "Listo"
}
