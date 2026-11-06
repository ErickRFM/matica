package mx.matica.design.motion

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
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
 */
@Composable
actual fun platformMotionScale(): Float {
    val resolver = LocalContext.current.contentResolver
    val scales = listOf(
        readScale(resolver, Settings.Global.ANIMATOR_DURATION_SCALE),
        readScale(resolver, Settings.Global.WINDOW_ANIMATION_SCALE),
        readScale(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE),
    )
    return scales.minOrNull() ?: 1f
}

private fun readScale(resolver: ContentResolver, key: String): Float =
    runCatching { Settings.Global.getFloat(resolver, key) }.getOrElse { 1f }
