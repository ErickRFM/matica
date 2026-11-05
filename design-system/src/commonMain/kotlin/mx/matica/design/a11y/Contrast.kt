package mx.matica.design.a11y

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Contraste WCAG 2.2 sobre los colores del sistema.
 *
 * Las funciones asumen colores opacos: un color con alfa debería mezclarse con su fondo antes de
 * medirlo, y esa mezcla ya no es un token del sistema.
 */
object Contrast {
    const val AA_NORMAL_TEXT: Double = 4.5
    const val AA_LARGE_TEXT: Double = 3.0

    fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val linear = value.toDouble()
            return if (linear <= 0.03928) linear / 12.92 else ((linear + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    fun ratio(foreground: Color, background: Color): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun threshold(largeText: Boolean): Double = if (largeText) AA_LARGE_TEXT else AA_NORMAL_TEXT

    fun meetsAa(foreground: Color, background: Color, largeText: Boolean = false): Boolean =
        ratio(foreground, background) >= threshold(largeText)
}
