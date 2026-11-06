package mx.matica.presentation.route

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Pila de navegación real: cada destino es un [MaticaRoute] tipado y la pila completa se puede
 * serializar a `List<String>` (y de vuelta) para sobrevivir a la rotación y a la muerte del proceso.
 *
 * Las decisiones de transporte viven aquí, no en los composables, para que «back desde la capa de
 * pistas regresa al reproductor» sea una prueba y no una observación manual.
 */
class MaticaNavigator(start: MaticaRoute) {

    private val entries = mutableStateListOf(start)

    /** Ruta visible: la superior de la pila, incluida una capa modal si está abierta. */
    val current: MaticaRoute get() = entries.last()

    /**
     * Pantalla que queda debajo de la capa modal. El host la sigue dibujando mientras la hoja está
     * abierta para que cerrarla no vuelva a montar el reproductor desde cero.
     */
    val base: MaticaRoute get() = entries.lastOrNull { !it.isOverlay } ?: entries.last()

    /** Profundidad sin contar capas modales: da la dirección de la transición entre pantallas. */
    val baseDepth: Int get() = entries.count { !it.isOverlay }

    val canGoBack: Boolean get() = entries.size > 1

    val depth: Int get() = entries.size

    var lastError: String? by mutableStateOf(null)
        private set

    /** Deja de apilar si el destino ya es el actual: un doble tap no duplica la pantalla. */
    fun go(route: MaticaRoute) {
        if (route == current) return
        entries.add(route)
    }

    /** Sustituye la pantalla actual sin crecer la pila (fin de lección, cambio de sección). */
    fun replace(route: MaticaRoute) {
        if (entries.isEmpty()) entries.add(route) else entries[entries.lastIndex] = route
    }

    /** Regresa un nivel. Devuelve `false` cuando ya está en la raíz: el host decide si sale de la app. */
    fun goBack(): Boolean {
        if (!canGoBack) return false
        entries.removeAt(entries.lastIndex)
        return true
    }

    /** Vuelve a una ruta que ya está en la pila, descartando lo que quedó encima. */
    fun popTo(route: MaticaRoute): Boolean {
        val index = entries.indexOf(route)
        if (index < 0) return false
        while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
        return true
    }

    fun resetTo(route: MaticaRoute) {
        entries.clear()
        entries.add(route)
    }

    /** Estado serializable. Sólo rutas válidas: un `path` corrupto se descarta, no revienta la app. */
    fun asPaths(): List<String> = entries.map { it.path }

    fun restore(paths: List<String>): Boolean {
        val restored = paths.mapNotNull { MaticaRoute.parse(it) }
        if (restored.isEmpty()) {
            lastError = "El historial guardado no contenía ninguna ruta válida."
            return false
        }
        entries.clear()
        entries.addAll(restored)
        return true
    }

    /**
     * Invariante de la pila: ningún destino puede quedar inalcanzable por un `path` que él mismo no
     * sabe producir. Se usa en pruebas y ante enlaces profundos del sistema.
     */
    fun acceptsLink(path: String): MaticaRoute? = MaticaRoute.parse(path).also { route ->
        if (route == null) lastError = "Enlace desconocido: '$path'."
    }

    override fun toString(): String = entries.joinToString(" -> ") { it.path }
}
