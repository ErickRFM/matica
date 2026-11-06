package mx.matica.presentation.math

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.theme.LocalMaticaSemanticColors
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing

/** Qué representa cada trozo de una ecuación ya impresa. */
enum class EquationRunRole { PLAIN, VARIABLE, OPERATOR, GROUPING }

/** Fragmento contiguo con el mismo rol; concatenarlos reconstruye el texto sin perder un carácter. */
data class EquationRun(val text: String, val role: EquationRunRole)

private const val OPERATORS = "=+-*/^%·÷−"
private const val GROUPINGS = "()"
private val SUPERSCRIPTS = charArrayOf('⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹')

/**
 * Tokeniza la salida de `ExprPrinter` (`2*x + 5 = 11`). Un identificador sólo se ilumina cuando es
 * la palabra completa: en `2*xy` la variable `x` no se marca, porque ahí no es `x` sino parte de
 * otro nombre.
 */
fun equationRuns(text: String, variable: String? = null): List<EquationRun> {
    val runs = mutableListOf<EquationRun>()
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char.isLetter() || char == '_' -> {
                var end = index
                while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end += 1
                val word = text.substring(index, end)
                runs.add(
                    EquationRun(word, if (variable != null && word == variable) EquationRunRole.VARIABLE else EquationRunRole.PLAIN),
                )
                index = end
            }
            char.isDigit() -> {
                var end = index
                while (end < text.length && (text[end].isDigit() || text[end] == '.' || text[end] == ',')) end += 1
                runs.add(EquationRun(text.substring(index, end), EquationRunRole.PLAIN))
                index = end
            }
            char in OPERATORS -> {
                runs.add(EquationRun(char.toString(), EquationRunRole.OPERATOR))
                index += 1
            }
            char in GROUPINGS -> {
                runs.add(EquationRun(char.toString(), EquationRunRole.GROUPING))
                index += 1
            }
            else -> {
                var end = index
                while (end < text.length && text[end].isWhitespace()) end += 1
                if (end == index) end = index + 1
                runs.add(EquationRun(text.substring(index, end), EquationRunRole.PLAIN))
                index = end
            }
        }
    }
    return runs
}

/**
 * Reescritura tipográfica del texto del motor: `*` se muestra como punto medio y `^2` como
 * superíndice. Es sólo presentación; el `Expr` del motor no cambia.
 */
fun prettyMath(text: String): String = buildString {
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char == '*' -> append('·')
            char == '^' -> {
                var next = index + 1
                if (next < text.length && (text[next] == '-' || text[next] == '−')) {
                    append('⁻')
                    next += 1
                }
                var digits = 0
                while (next < text.length && text[next].isDigit()) {
                    append(SUPERSCRIPTS[text[next] - '0'])
                    next += 1
                    digits += 1
                }
                if (digits == 0) append('^') else index = next - 1
            }
            else -> append(char)
        }
        index += 1
    }
}

/**
 * Lectura en voz alta, en español: un lector de pantalla no debe deletrear «x igual a 3» cuando lo
 * que se dice es «x es igual a 3».
 */
fun equationAnnouncement(text: String): String {
    val words = buildString {
        text.forEach { char ->
            append(
                when (char) {
                    '=' -> " es igual a "
                    '+' -> " más "
                    '-', '−' -> " menos "
                    '*', '·', '×' -> " por "
                    '/', '÷' -> " dividido entre "
                    '^' -> " elevado a "
                    '%' -> " por ciento "
                    '(' -> " abre paréntesis "
                    ')' -> " cierra paréntesis "
                    else -> char.toString()
                },
            )
        }
    }
    return words.replace(Regex("\\s+"), " ").trim()
}

/**
 * Ecuación del ejercicio sobre la superficie propia del sistema. Al cambiar de paso el texto nuevo
 * entra desplazado y el anterior se desvanece; con movimiento reducido sólo queda el fundido, y el
 * desplazamiento desaparece porque la posición del texto no debe moverse sola ante una persona con
 * fotofobia.
 */
@Composable
fun EquationView(
    text: String,
    modifier: Modifier = Modifier,
    variable: String? = null,
) {
    val semantic = LocalMaticaSemanticColors.current
    val motion = LocalMotionStyle.current
    val highlight = MaterialTheme.colorScheme.primary
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (motion.reduced) {
                fadeIn(MaticaMotion.fadeSpec(motion)) togetherWith fadeOut(MaticaMotion.fadeSpec(motion))
            } else {
                (slideInHorizontally(MaticaMotion.contentSpec(motion)) { -it / 6 } +
                    fadeIn(MaticaMotion.fadeSpec(motion))) togetherWith
                    fadeOut(MaticaMotion.fadeSpec(motion))
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .background(color = semantic.equationSurface, shape = RoundedCornerShape(MaticaShapes.large)),
        label = "matica-equation",
    ) { current ->
        Text(
            text = equationStyled(current, variable, semantic.onEquationSurface, highlight),
            style = MaterialTheme.typography.headlineMedium,
            color = semantic.onEquationSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = equationAnnouncement(current) }
                .padding(horizontal = MaticaSpacing.lg, vertical = MaticaSpacing.md),
        )
    }
}

/** Colores por rol: la incógnita se resalta y los paréntesis ceden jerarquía visual. */
fun equationStyled(
    text: String,
    variable: String?,
    baseColor: Color,
    highlight: Color,
): AnnotatedString = buildAnnotatedString {
    val pretty = prettyMath(text)
    equationRuns(pretty, variable).forEach { run ->
        val style = when (run.role) {
            EquationRunRole.VARIABLE -> SpanStyle(color = highlight)
            EquationRunRole.GROUPING -> SpanStyle(color = baseColor.copy(alpha = 0.72f))
            EquationRunRole.OPERATOR -> SpanStyle(color = baseColor.copy(alpha = 0.86f))
            EquationRunRole.PLAIN -> SpanStyle(color = baseColor)
        }
        withStyle(style) { append(run.text) }
    }
}
