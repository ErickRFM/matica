package mx.matica.presentation.math

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.math.numeric.Rational
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.components.MaticaIconButton
import kotlin.math.abs

/**
 * Recta numérica: un intervalo, sus marcas exactas y los valores subrayados. Las marcas viajan como
 * `Rational` porque una recta con ticks en `0.30000000000000004` enseñaría una matemática falsa.
 */
data class NumberLineModel(
    val from: Rational,
    val to: Rational,
    val ticks: List<Rational>,
    val markers: List<Rational> = emptyList(),
) {
    init {
        require(to > from) { "El extremo derecho debe ser mayor que el izquierdo." }
        require(ticks.isNotEmpty()) { "Una recta sin marcas no puede aproximar un valor." }
    }

    val orderedTicks: List<Rational> = ticks.sorted()
}

/** Posición normalizada 0..1 de un valor, con división exacta antes de la única aproximación en doble. */
fun positionOf(value: Rational, from: Rational, to: Rational): Float {
    val ratio = (value - from) / (to - from)
    return ratio.approximate().coerceIn(0.0, 1.0).toFloat()
}

/**
 * Marca más cercana a un valor exacto. La distancia se mide en doble porque sólo sirve para ordenar
 * candidatos; el valor devuelto sigue siendo el `Rational` exacto de la marca.
 */
fun nearestTick(value: Rational, ticks: List<Rational>): Rational {
    require(ticks.isNotEmpty()) { "Una recta sin marcas no puede aproximar un valor." }
    return ticks.minByOrNull { abs(it.approximate() - value.approximate()) } ?: ticks.first()
}

/** Único punto donde un valor exacto se aproxima; sólo para coordenadas y distancias. */
internal fun Rational.approximate(): Double = numerator.doubleValue() / denominator.doubleValue()

/**
 * Valor pedido por un arrastre: el dedo se traduce a la marca más cercana, nunca a un número
 * intermedio inventado.
 */
fun valueAt(fraction: Float, model: NumberLineModel): Rational {
    val clamped = fraction.coerceIn(0f, 1f)
    return model.orderedTicks.minByOrNull { abs(positionOf(it, model.from, model.to) - clamped) }
        ?: model.orderedTicks.first()
}

/** Marca que cae más cerca del primer cuarto del intervalo; la fracción del rango que se muestra en pantalla. */
fun quarterTick(model: NumberLineModel): Rational =
    nearestTick(model.from + (model.to - model.from) / Rational.of(4, 1), model.orderedTicks)

/**
 * Recta numérica interactiva. Arrastrar y los botones de salto llegan al mismo `Rational` exacto:
 * el gesto existe para que se pueda explorar la recta, no como única vía, de modo que un lector de
 * pantalla o un teclado siguen pudiendo cambiar el valor (WCAG 2.1.1).
 */
@Composable
fun NumberLineView(
    model: NumberLineModel,
    modifier: Modifier = Modifier,
    selected: Rational? = null,
    onValueChange: (Rational) -> Unit = {},
) {
    val motion = LocalMotionStyle.current
    val marker = remember { Animatable(selected?.let { positionOf(it, model.from, model.to) } ?: 0f) }
    LaunchedEffect(selected, model, motion.reduced) {
        val target = selected?.let { positionOf(it, model.from, model.to) } ?: return@LaunchedEffect
        if (motion.reduced) marker.snapTo(target) else marker.animateTo(target, MaticaMotion.popSpec(motion))
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "De ${model.from} a ${model.to}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = MaticaSpacing.xs)
                .semantics { heading() },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .semantics {
                    contentDescription =
                        "Recta numérica de ${model.from} a ${model.to}, con ${model.orderedTicks.size} marcas. " +
                            (selected?.let { "Marcador en $it. " } ?: "") +
                            "Desliza o usa los botones de salto."
                }
                .pointerInput(model) {
                    detectHorizontalDragGestures { change, _ ->
                        val width = size.width
                        if (width <= 0) return@detectHorizontalDragGestures
                        onValueChange(valueAt(change.position.x / width.toFloat(), model))
                    }
                }
                .padding(horizontal = MaticaSpacing.md),
        ) {
            val scheme = MaterialTheme.colorScheme
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.Center)) {
                val y = size.height / 2f
                val usable = size.width
                drawLine(
                    color = scheme.outlineVariant,
                    start = Offset(0f, y),
                    end = Offset(usable, y),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
                model.orderedTicks.forEach { tick ->
                    val x = usable * positionOf(tick, model.from, model.to)
                    val emphasized = tick == selected
                    drawLine(
                        color = if (emphasized) scheme.primary else scheme.onSurfaceVariant,
                        start = Offset(x, y - if (emphasized) 14.dp.toPx() else 9.dp.toPx()),
                        end = Offset(x, y + if (emphasized) 14.dp.toPx() else 9.dp.toPx()),
                        strokeWidth = if (emphasized) 4.dp.toPx() else 2.dp.toPx(),
                    )
                }
                model.markers.forEach { marked ->
                    drawCircle(
                        color = scheme.tertiary,
                        radius = 6.dp.toPx(),
                        center = Offset(usable * positionOf(marked, model.from, model.to), y),
                    )
                }
                if (selected != null) {
                    drawCircle(
                        color = scheme.primary,
                        radius = 12.dp.toPx(),
                        center = Offset(usable * marker.value.coerceIn(0f, 1f), y),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaticaIconButton(
                icon = MaticaIcon.BACK,
                contentDescription = "Marca anterior",
                onClick = { stepTick(model, selected, -1, onValueChange) },
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = selected?.toString() ?: "Sin marcador",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = MaticaSpacing.md),
            )
            MaticaIconButton(
                icon = MaticaIcon.FORWARD,
                contentDescription = "Marca siguiente",
                onClick = { stepTick(model, selected, +1, onValueChange) },
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

/** Moverse por marcas con las flechas: sin selección, la primera marca es el punto de partida. */
private fun stepTick(model: NumberLineModel, selected: Rational?, delta: Int, onValueChange: (Rational) -> Unit) {
    val ticks = model.orderedTicks
    val index = selected?.let { chosen -> ticks.indexOfFirst { it == chosen } } ?: -1
    val next = when {
        index < 0 -> ticks.first()
        else -> (index + delta).coerceIn(0, ticks.lastIndex).let(ticks::get)
    }
    onValueChange(next)
}
