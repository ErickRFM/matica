package mx.matica.design.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * En JVM (pruebas de escritorio y CI sin hardware táctil/altavoz nativo móvil),
 * devuelve una instancia segura que no bloquea ni produce excepciones.
 */
@Composable
actual fun rememberMaticaFeedback(
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
): MaticaFeedback = remember { NoOpMaticaFeedback }
