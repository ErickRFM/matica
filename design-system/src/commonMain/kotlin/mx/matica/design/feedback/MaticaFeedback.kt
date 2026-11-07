package mx.matica.design.feedback

import androidx.compose.runtime.Composable

/**
 * Catálogo de sonidos procedimentales para retroalimentación auditiva en Mática.
 * No depende de archivos de audio externos para mantener 100% de fiabilidad offline
 * y compilaciones ligeras sin assets pesados.
 */
enum class FeedbackSound {
    CLICK,
    STEP_SELECT,
    CORRECT,
    INCORRECT,
    VICTORY,
    NODE_CLICK,
}

/**
 * Catálogo de respuestas hápticas y vibraciones sensoriales.
 */
enum class FeedbackHaptic {
    LIGHT_TICK,
    CONFIRMATION,
    ERROR,
    CELEBRATION,
    SELECTION,
}

/**
 * Interfaz para invocar retroalimentación auditiva y háptica en la aplicación.
 */
interface MaticaFeedback {
    fun playSound(sound: FeedbackSound)
    fun performHaptic(haptic: FeedbackHaptic)

    /** Toque sutil al presionar una tecla matemática o botón rápido. */
    fun keyTap() {
        playSound(FeedbackSound.CLICK)
        performHaptic(FeedbackHaptic.LIGHT_TICK)
    }

    /** Selección de una tarjeta de opción de paso (andamiaje). */
    fun stepSelect() {
        playSound(FeedbackSound.STEP_SELECT)
        performHaptic(FeedbackHaptic.SELECTION)
    }

    /** Respuesta correcta o paso algebraico válido. */
    fun correct() {
        playSound(FeedbackSound.CORRECT)
        performHaptic(FeedbackHaptic.CONFIRMATION)
    }

    /** Paso inválido, error de cálculo o sintaxis. */
    fun incorrect() {
        playSound(FeedbackSound.INCORRECT)
        performHaptic(FeedbackHaptic.ERROR)
    }

    /** Celebración al completar lección o meta. */
    fun victory() {
        playSound(FeedbackSound.VICTORY)
        performHaptic(FeedbackHaptic.CELEBRATION)
    }

    /** Clic en un nodo o estación del mapa de ruta. */
    fun nodeClick() {
        playSound(FeedbackSound.NODE_CLICK)
        performHaptic(FeedbackHaptic.SELECTION)
    }
}

/**
 * Implementación no-operativa para pruebas unitarias, CI o entornos sin soporte de audio/háptico.
 */
object NoOpMaticaFeedback : MaticaFeedback {
    override fun playSound(sound: FeedbackSound) = Unit
    override fun performHaptic(haptic: FeedbackHaptic) = Unit
}

/**
 * Grabador de llamadas para verificar comportamientos hápticos y sonoros en pruebas.
 */
class RecordingMaticaFeedback : MaticaFeedback {
    val playedSounds = mutableListOf<FeedbackSound>()
    val performedHaptics = mutableListOf<FeedbackHaptic>()

    override fun playSound(sound: FeedbackSound) {
        playedSounds += sound
    }

    override fun performHaptic(haptic: FeedbackHaptic) {
        performedHaptics += haptic
    }

    fun clear() {
        playedSounds.clear()
        performedHaptics.clear()
    }
}

/**
 * Recuerda e inicializa el controlador de retroalimentación de la plataforma actual.
 *
 * @param soundEnabled Habilita o silencia la reproducción de tonos procedimentales.
 * @param hapticsEnabled Habilita o deshabilita la vibración y retroalimentación táctil.
 */
@Composable
expect fun rememberMaticaFeedback(
    soundEnabled: Boolean = true,
    hapticsEnabled: Boolean = true,
): MaticaFeedback
