package mx.matica.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MotionStyle
import mx.matica.design.motion.platformMotionScale
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaTypography

/**
 * Tema en vigor. Se expone como valor, no como parámetro de cada componente: un botón que recibiera
 * `darkTheme: Boolean` podría recibir el valor equivocado y pintar un par que falla contraste, y la
 * prueba no lo detectaría porque la paleta medida seguiría siendo correcta en abstracto.
 */
val LocalMaticaDarkTheme = staticCompositionLocalOf { false }

/**
 * Tema único de Mática.
 *
 * `colorScheme` se pasa siempre de forma explícita: si Material 3 resolviera el suyo por defecto,
 * en Android activaría Material You y sustituiría la paleta medida por colores dinámicos.
 *
 * `motionScale` permite que Ajustes fuerce el movimiento. Con `null` se usa la escala real del
 * sistema, que es el camino por defecto y el que respeta movimiento reducido sin tocar nada.
 */
@Composable
fun MaticaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionScale: Float? = null,
    content: @Composable () -> Unit,
) {
    val semantic = if (darkTheme) MaticaSemanticColors.Dark else MaticaSemanticColors.Light
    val scheme = if (darkTheme) MaticaDarkColorScheme else MaticaLightColorScheme
    val motion = MotionStyle(motionScale ?: platformMotionScale())
    MaticaSystemBarAppearance(darkTheme)
    CompositionLocalProvider(
        LocalMaticaSemanticColors provides semantic,
        LocalMaticaDarkTheme provides darkTheme,
        LocalMotionStyle provides motion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaticaTypography.typography,
            shapes = MaticaShapes.shapes,
            content = content,
        )
    }
}
