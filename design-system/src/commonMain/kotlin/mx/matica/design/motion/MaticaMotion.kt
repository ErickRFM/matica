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
 * La escala del sistema no toca la amortiguación: endurece o ablanda los muelles vía
 * [stiffnessFactor], que es lo de verdad cambia su velocidad.
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

    /**
     * Banda del factor de rigidez: una escala absurda del sistema (0.05x, 20x) fuera de ella
     * produciría un muelle cuyo periodo nunca parece terminar, o un salto instantáneo.
     */
    const val STIFFNESS_FACTOR_MIN: Float = 0.5f
    const val STIFFNESS_FACTOR_MAX: Float = 2f

    /**
     * Factor por el que se multiplica la rigidez base de un muelle según la escala del sistema.
     * El periodo de un muelle es inversamente proporcional a la raíz de la rigidez, así que sólo
     * escalando `stiffness` (y no la amortiguación) 1.5x se siente más rápido y 0.5x más lento.
     * Con movimiento reducido se devuelve la rigidez base: lo único que debe cambiar es el rebote.
     */
    fun stiffnessFactor(scale: Float): Float =
        if (scale <= 0f) 1f else scale.coerceIn(STIFFNESS_FACTOR_MIN, STIFFNESS_FACTOR_MAX)

    /** Aparición de contenido: sin rebote, para no mover texto que se está leyendo. */
    fun <T> contentSpec(style: MotionStyle): SpringSpec<T> = spring(
        dampingRatio = if (style.reduced) 1f else 0.86f,
        stiffness = Spring.StiffnessMediumLow * stiffnessFactor(style.scale),
    )

    /** Refuerzo de acierto: rebote deliberado que se apaga cuando el sistema pide calma. */
    fun <T> popSpec(style: MotionStyle): SpringSpec<T> = spring(
        dampingRatio = if (style.reduced) 1f else 0.42f,
        stiffness = Spring.StiffnessLow * stiffnessFactor(style.scale),
    )

    /** Fundido de respaldo cuando el movimiento está reducido. */
    fun fadeSpec(style: MotionStyle, baseMs: Int = durations.short): TweenSpec<Float> = tween(
        durationMillis = style.fadeMs(baseMs),
    )
}
