package mx.matica.design.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Tokens de movimiento. Las duraciones son múltiplos de 40 ms y los rebotes se quedan por debajo
 * del 1 de amortiguación para que la gamificación se sienta elástica sin resultar nerviosa.
 * Con movimiento reducido la amortiguación sube a 1: el muelle llega directo, sin rebote.
 */
object MaticaMotion {

    val durations = Durations(instant = 80, short = 160, medium = 240, long = 400, xLong = 600)

    data class Durations(
        val instant: Int,
        val short: Int,
        val medium: Int,
        val long: Int,
        val xLong: Int,
    ) {
        /** De menor a mayor; las pruebas exigen que este orden sea estricto. */
        val scale: List<Int> = listOf(instant, short, medium, long, xLong)
    }

    /** Aparición de contenido: sin rebote, para no mover texto que se está leyendo. */
    fun <T> contentSpec(style: MotionStyle): SpringSpec<T> = spring(
        dampingRatio = if (style.reduced) 1f else 0.86f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Refuerzo de acierto: rebote deliberado que se apaga cuando el sistema pide calma. */
    fun <T> popSpec(style: MotionStyle): SpringSpec<T> = spring(
        dampingRatio = if (style.reduced) 1f else 0.42f,
        stiffness = Spring.StiffnessLow,
    )

    /** Fundido de respaldo cuando el movimiento está reducido. */
    fun fadeSpec(style: MotionStyle, baseMs: Int = durations.short): TweenSpec<Float> = tween(
        durationMillis = style.fadeMs(baseMs),
    )
}
