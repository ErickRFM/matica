package mx.matica.presentation.math

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.math.numeric.Rational

/**
 * Lo que se pinta de una fracción, separado del `Rational` para que el mapeo sea comprobable:
 * un entero nunca dibuja barra y un signo negativo nunca queda escondido dentro del numerador.
 */
data class FractionViewModel(
    val signText: String,
    val wholeText: String?,
    val numeratorText: String?,
    val denominatorText: String?,
    val accessibleText: String,
) {
    val isFraction: Boolean get() = numeratorText != null && denominatorText != null
}

/**
 * `asMixed` escribe 3/2 como «1 1/2» sólo en la vista: el valor sigue siendo el mismo `Rational`.
 * Sin el flag se dibuja la fracción impropia tal como el estudiante la resolvió.
 */
fun fractionModel(value: Rational, asMixed: Boolean = false): FractionViewModel {
    val negative = value.signum < 0
    val magnitude = value.abs
    val signText = if (negative) "−" else ""
    if (magnitude.isInteger) {
        val text = magnitude.numerator.toString()
        return FractionViewModel(signText, null, text, null, announce(signText, null, text, null))
    }
    val denominatorText = magnitude.denominator.toString()
    if (asMixed) {
        val wholePart = magnitude.numerator / magnitude.denominator
        val remainder = (magnitude.numerator % magnitude.denominator).toString()
        val wholeText = wholePart.takeIf { it.signum() != 0 }?.toString()
        return FractionViewModel(
            signText = signText,
            wholeText = wholeText,
            numeratorText = remainder,
            denominatorText = denominatorText,
            accessibleText = announce(signText, wholeText, remainder, denominatorText),
        )
    }
    val numeratorText = magnitude.numerator.toString()
    return FractionViewModel(
        signText = signText,
        wholeText = null,
        numeratorText = numeratorText,
        denominatorText = denominatorText,
        accessibleText = announce(signText, null, numeratorText, denominatorText),
    )
}

private fun announce(sign: String, whole: String?, numerator: String?, denominator: String?): String {
    val parts = buildList {
        if (sign.isNotEmpty()) add("negativo")
        whole?.let { if (it != "0") add(it) }
        when {
            numerator != null && denominator != null -> add("$numerator sobre $denominator")
            numerator != null -> add(numerator)
        }
    }
    return parts.joinToString(" ")
}

/**
 * Fracción en dos líneas con su barra divisoria. La barra se traza de izquierda a derecha al
 * aparecer: ese gesto es el que se asocia a «dividido entre», y con movimiento reducido la barra
 * ya aparece completa.
 */
@Composable
fun FractionView(
    value: Rational,
    modifier: Modifier = Modifier,
    asMixed: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val model = remember(value, asMixed) { fractionModel(value, asMixed) }
    val motion = LocalMotionStyle.current
    val sweep = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(value, motion.reduced) {
        if (motion.reduced) sweep.snapTo(1f) else sweep.animateTo(1f, MaticaMotion.popSpec(motion))
    }
    Row(
        modifier = modifier.semantics { contentDescription = model.accessibleText },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.xs),
    ) {
        if (model.signText.isNotEmpty()) FractionText(model.signText, color)
        model.wholeText?.let { FractionText(it, color) }
        if (model.isFraction) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FractionText(model.numeratorText!!, color)
                FractionBar(sweep.value, color)
                FractionText(model.denominatorText!!, color)
            }
        } else {
            model.numeratorText?.let { FractionText(it, color) }
        }
    }
}
@Composable
private fun FractionText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

@Composable
private fun FractionBar(fraction: Float, color: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
        val barWidth = size.width * fraction
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(barWidth, size.height),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}

/** Celdas de la barra fraccionada: `parts` es el denominador y `filledParts` el numerador exacto. */
data class StripCells(
    val parts: Int,
    val filledParts: Int,
    val improper: Boolean,
    val drawable: Boolean,
)

/**
 * Descompone el `Rational` ya reducido en celdas. Sólo una fracción propia cabe en la barra de un
 * entero: con numerador mayor que el denominador la barra se saldría del todo, y un denominador
 * mayor que [maxParts] daría cientos de rectángulos inútiles. En ambos casos la pantalla debe
 * recurrir a [FractionView].
 */
fun stripCells(value: Rational, maxParts: Int = 12): StripCells {
    val limit = BigInteger.fromLong(maxParts.toLong())
    val magnitude = value.abs
    if (magnitude.isZero) return StripCells(parts = 1, filledParts = 0, improper = false, drawable = true)
    val improper = magnitude.isInteger || magnitude.numerator.compareTo(magnitude.denominator) > 0
    val drawable = !improper && magnitude.denominator.compareTo(limit) <= 0
    val parts = if (drawable) magnitude.denominator.intValue() else maxParts + 1
    val filled = if (drawable) magnitude.numerator.intValue() else 0
    return StripCells(parts = parts, filledParts = filled, improper = improper, drawable = drawable)
}

/**
 * La fracción como barra repartida en tantas celdas como indica el denominador. Aquí el `Rational`
 * ya viene reducido por el motor, así que 2/4 se dibuja como 1/2: la simplificación es visible sin
 * explicarla.
 */
@Composable
fun FractionStrip(
    value: Rational,
    modifier: Modifier = Modifier,
    fillColor: Color = MaticaPalette.Teal,
    emptyColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    maxParts: Int = 12,
) {
    val cells = remember(value, maxParts) { stripCells(value, maxParts) }
    val motion = LocalMotionStyle.current
    if (!cells.drawable) {
        FractionView(value = value, modifier = modifier, color = fillColor)
        return
    }
    val target = cells.filledParts.toFloat()
    val progress = remember { Animatable(if (motion.reduced) target else 0f) }
    LaunchedEffect(target, motion.reduced) {
        if (motion.reduced) progress.snapTo(target) else progress.animateTo(target, MaticaMotion.popSpec(motion))
    }
    val negative = value.signum < 0
    Row(
        modifier = modifier
            .height(28.dp)
            .semantics {
                contentDescription =
                    if (negative) "menos ${cells.filledParts} de ${cells.parts} partes"
                    else "${cells.filledParts} de ${cells.parts} partes"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.xs),
    ) {
        if (negative) FractionText("−", fillColor)
        repeat(cells.parts) { index ->
            val painted = (progress.value - index).coerceIn(0f, 1f)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    val radius = CornerRadius(size.minDimension / 5f)
                    drawRoundRect(color = emptyColor, cornerRadius = radius)
                    drawRoundRect(
                        color = fillColor,
                        size = Size(size.width * painted, size.height),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}