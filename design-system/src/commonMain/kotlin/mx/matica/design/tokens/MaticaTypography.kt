package mx.matica.design.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica del sistema.
 *
 * La familia es la del sistema porque el repo no incluye binarios de fuente y descargarlas rompe
 * el build offline. Los tamaños son derivación propia a partir del peso visual del diseño.
 */
object MaticaTypography {
    private val family = FontFamily.Default

    private fun style(size: TextUnit, weight: FontWeight, lineHeight: TextUnit, tracking: TextUnit = 0.sp) =
        TextStyle(
            fontFamily = family,
            fontWeight = weight,
            fontSize = size,
            lineHeight = lineHeight,
            letterSpacing = tracking,
        )

    val displayLarge = style(36.sp, FontWeight.Bold, 44.sp)
    val displayMedium = style(30.sp, FontWeight.Bold, 38.sp)
    val headlineLarge = style(26.sp, FontWeight.Bold, 34.sp)
    val headlineMedium = style(22.sp, FontWeight.SemiBold, 30.sp)
    val headlineSmall = style(19.sp, FontWeight.SemiBold, 26.sp)
    val titleLarge = style(18.sp, FontWeight.SemiBold, 26.sp)
    val titleMedium = style(16.sp, FontWeight.SemiBold, 24.sp)
    val titleSmall = style(14.sp, FontWeight.Medium, 20.sp)
    val bodyLarge = style(16.sp, FontWeight.Normal, 24.sp)
    val bodyMedium = style(14.sp, FontWeight.Normal, 20.sp)
    val bodySmall = style(12.sp, FontWeight.Normal, 16.sp)
    val labelLarge = style(15.sp, FontWeight.SemiBold, 20.sp)
    val labelMedium = style(13.sp, FontWeight.Medium, 18.sp)
    val labelSmall = style(11.sp, FontWeight.Medium, 16.sp)

    /** Todos los estilos de la escala, ordenados de mayor a menor (con empates permitidos). */
    val descending: List<TextStyle> = listOf(
        displayLarge, displayMedium, headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, bodyLarge, labelLarge, titleSmall, bodyMedium,
        labelMedium, bodySmall, labelSmall,
    )

    val typography = Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = titleSmall,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall,
    )
}
