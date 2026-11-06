package mx.matica.design.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Escala de movimiento del dispositivo. `scale == 0f` significa que el sistema pidió
 * movimiento reducido y toda la capa visual debe degradarse a cambios instantáneos o fundidos cortos.
 */
data class MotionStyle(val scale: Float) {

    val reduced: Boolean get() = scale <= 0f

    /** Escala una duración base; con movimiento reducido devuelve 0 para que el cambio sea instantáneo. */
    fun ms(base: Int): Int = if (reduced) 0 else (base * scale).toInt().coerceAtLeast(1)

    /** Duración máxima para un fundido accesible cuando el movimiento está reducido. */
    fun fadeMs(base: Int): Int = if (reduced) base.coerceAtMost(FADE_REDUCED_MAX) else ms(base)

    companion object {
        /** Material limita el fundido de respaldo a 150 ms cuando no hay movimiento. */
        const val FADE_REDUCED_MAX = 150
        val Default = MotionStyle(1f)
    }
}

val LocalMotionStyle = staticCompositionLocalOf { MotionStyle.Default }

/**
 * Lee la escala real del sistema. En Android son los factores de `Settings.Global`; en JVM no hay
 * sistema operativo de escritorio que consultar y se devuelve la escala neutra.
 */
@Composable
expect fun platformMotionScale(): Float

@Composable
fun rememberMotionStyle(): MotionStyle = MotionStyle(platformMotionScale())
