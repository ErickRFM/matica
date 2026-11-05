package mx.matica.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaTypography

/**
 * Tema único de Mática.
 *
 * `colorScheme` se pasa siempre de forma explícita: si Material 3 resolviera el suyo por defecto,
 * en Android activaría Material You y sustituiría la paleta medida por colores dinámicos.
 */
@Composable
fun MaticaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val semantic = if (darkTheme) MaticaSemanticColors.Dark else MaticaSemanticColors.Light
    val scheme = if (darkTheme) MaticaDarkColorScheme else MaticaLightColorScheme
    CompositionLocalProvider(LocalMaticaSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaticaTypography.typography,
            shapes = MaticaShapes.shapes,
            content = content,
        )
    }
}
