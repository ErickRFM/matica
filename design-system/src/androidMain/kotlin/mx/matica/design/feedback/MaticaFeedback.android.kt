package mx.matica.design.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import mx.matica.design.motion.LocalMotionStyle

/**
 * Implementación de [MaticaFeedback] para Android con síntesis procedimental por [ToneGenerator]
 * y vibraciones hápticas de precisión mediante [Vibrator] y [HapticFeedback].
 *
 * Ventajas:
 * 1. Cero archivos de audio pesados en assets (sin MP3/WAV externos, 100% offline).
 * 2. Protección total contra caídas por falta de permisos o restricción de hardware con try-catch.
 * 3. Respeta accesibilidad y movimiento reducido ([LocalMotionStyle.current.reduced]).
 */
class AndroidMaticaFeedback(
    private val toneGenerator: ToneGenerator?,
    private val vibrator: Vibrator?,
    private val hapticFeedback: HapticFeedback,
    private val soundEnabled: Boolean,
    private val hapticsEnabled: Boolean,
) : MaticaFeedback {

    override fun playSound(sound: FeedbackSound) {
        if (!soundEnabled || toneGenerator == null) return
        try {
            when (sound) {
                FeedbackSound.CLICK -> toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 25)
                FeedbackSound.STEP_SELECT -> toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
                FeedbackSound.CORRECT -> toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                FeedbackSound.INCORRECT -> toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 160)
                FeedbackSound.VICTORY -> toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 320)
                FeedbackSound.NODE_CLICK -> toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
            }
        } catch (_: Throwable) {
            // Protección ante fallos de hardware de audio o interrupciones del sistema
        }
    }

    override fun performHaptic(haptic: FeedbackHaptic) {
        if (!hapticsEnabled) return
        var vibrated = false
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = when (haptic) {
                        FeedbackHaptic.LIGHT_TICK ->
                            VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
                        FeedbackHaptic.SELECTION ->
                            VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE)
                        FeedbackHaptic.CONFIRMATION ->
                            VibrationEffect.createWaveform(longArrayOf(0, 25, 40, 35), -1)
                        FeedbackHaptic.ERROR ->
                            VibrationEffect.createOneShot(65, VibrationEffect.DEFAULT_AMPLITUDE)
                        FeedbackHaptic.CELEBRATION ->
                            VibrationEffect.createWaveform(longArrayOf(0, 40, 30, 60, 40, 80), -1)
                    }
                    vibrator.vibrate(effect)
                    vibrated = true
                } else {
                    @Suppress("DEPRECATION")
                    when (haptic) {
                        FeedbackHaptic.LIGHT_TICK, FeedbackHaptic.SELECTION -> vibrator.vibrate(12)
                        FeedbackHaptic.CONFIRMATION -> vibrator.vibrate(longArrayOf(0, 25, 40, 35), -1)
                        FeedbackHaptic.ERROR -> vibrator.vibrate(65)
                        FeedbackHaptic.CELEBRATION -> vibrator.vibrate(longArrayOf(0, 40, 30, 60, 40, 80), -1)
                    }
                    vibrated = true
                }
            }
        } catch (_: Throwable) {
            // Fallback a HapticFeedback de Compose en caso de SecurityException
        }

        if (!vibrated) {
            try {
                when (haptic) {
                    FeedbackHaptic.LIGHT_TICK, FeedbackHaptic.SELECTION ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    FeedbackHaptic.CONFIRMATION, FeedbackHaptic.ERROR, FeedbackHaptic.CELEBRATION ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            } catch (_: Throwable) {
                // Silencioso si el dispositivo tampoco permite feedback de vistas
            }
        }
    }
}

@Composable
actual fun rememberMaticaFeedback(
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
): MaticaFeedback {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val motion = LocalMotionStyle.current

    val toneGenerator = remember(soundEnabled) {
        if (!soundEnabled) null
        else {
            try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            } catch (_: Throwable) {
                null
            }
        }
    }

    DisposableEffect(toneGenerator) {
        onDispose {
            try {
                toneGenerator?.release()
            } catch (_: Throwable) {
                // Ignore disposal errors
            }
        }
    }

    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    return remember(toneGenerator, vibrator, hapticFeedback, soundEnabled, hapticsEnabled, motion.reduced) {
        AndroidMaticaFeedback(
            toneGenerator = toneGenerator,
            vibrator = vibrator,
            hapticFeedback = hapticFeedback,
            soundEnabled = soundEnabled,
            hapticsEnabled = hapticsEnabled && !motion.reduced,
        )
    }
}
