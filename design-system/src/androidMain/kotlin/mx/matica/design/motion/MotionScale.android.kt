package mx.matica.design.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Escala real de movimiento del teléfono.
 *
 * Se inspeccionó `android/provider/Settings$Global.class` del SDK 37 y las únicas constantes
 * públicas relacionadas son `ANIMATOR_DURATION_SCALE`, `WINDOW_ANIMATION_SCALE` y
 * `TRANSITION_ANIMATION_SCALE`; no existe `ANIMATION_ALLOWED`, así que el interruptor de
 * movimiento reducido se deduce de que cualquiera de las tres escalas valga 0.
 *
 * Se toma el mínimo de las tres: es la lectura conservadora, y si el proveedor falla se devuelve
 * escala neutra porque una app de matemáticas debe seguir siendo usable, no romperse.
 *
 * Cada `Settings.Global.getFloat` es una consulta al proveedor de ajustes, y esto se leía en cada
 * recomposición: un arrastre en la gráfica recomponía varias veces por cuadro y el coste del sistema
 * iba en la misma ruta que el dibujo. Ahora el valor se recuerda y sólo se vuelve a leer cuando el
 * proveedor avisa por su `ContentObserver`, que es también la forma de que el cambio se note sin
 * reiniciar la app.
 */
@Composable
actual fun platformMotionScale(): Float {
    val resolver = LocalContext.current.contentResolver
    var scales by remember(resolver) { mutableStateOf(readScales(resolver)) }
    DisposableEffect(resolver) {
        val changes = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scales = readScales(resolver)
            }
        }
        MOTION_KEYS.forEach { key ->
            resolver.registerContentObserver(Settings.Global.getUriFor(key), false, changes)
        }
        onDispose { resolver.unregisterContentObserver(changes) }
    }
    return scales.minOrNull() ?: 1f
}

private val MOTION_KEYS = listOf(
    Settings.Global.ANIMATOR_DURATION_SCALE,
    Settings.Global.WINDOW_ANIMATION_SCALE,
    Settings.Global.TRANSITION_ANIMATION_SCALE,
)

private fun readScales(resolver: ContentResolver): List<Float> =
    MOTION_KEYS.map { key -> runCatching { Settings.Global.getFloat(resolver, key) }.getOrElse { 1f } }
