package mx.matica.presentation.math

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.components.MaticaIconButton
import kotlinx.coroutines.launch

/** De qué habla la figura: el total es la suma de los cuatro lados o el recubrimiento del rectángulo. */
enum class FigureMeasure { PERIMETER, AREA }

/**
 * Rectángulo cuyo lado desconocido es la incógnita del ejercicio. No hay medidas inventadas aquí:
 * el lado conocido y el total salen de la propia ecuación, y si la ecuación no se puede leer como
 * rectángulo, [rectangleFigureOf] devuelve `null` y la pantalla no dibuja nada.
 */
data class RectangleFigure(
    val measure: FigureMeasure,
    val variable: String,
    val knownSide: Rational,
    val total: Rational,
) {
    init {
        require(knownSide > Rational.ZERO) { "Un lado de la figura no puede ser cero ni negativo." }
    }

    /** Total que tiene la figura cuando el lado desconocido vale `side`. Aritmética exacta, nunca `Double`. */
    fun totalAt(side: Rational): Rational = when (measure) {
        FigureMeasure.PERIMETER -> TWO * (side + knownSide)
        FigureMeasure.AREA -> side * knownSide
    }

    val totalLabelEs: String get() = if (measure == FigureMeasure.PERIMETER) "perímetro" else "área"

    /** Lo que hay que conseguir, dicho como lo diría el libro: `perímetro 14`, `área 6`. */
    val goalEs: String get() = "$totalLabelEs $total"
}

private val PLUS = Expr.Sum.Term.Operator.PLUS
private val TWO: Rational = Rational.fromLong(2L)

/** El impresor escribe `2x = 6` dentro de una suma de un término; para leer la figura hay que abrirla. */
private fun unwrap(side: Expr): Expr {
    val only = (side as? Expr.Sum)?.terms?.singleOrNull()
    return if (only != null && only.operator == PLUS) only.operand else side
}

private fun constantOf(side: Expr): Rational? = (side as? Expr.Const)?.value

private fun productFactors(side: Expr): List<Expr>? = (side as? Expr.Product)
    ?.factors
    ?.takeIf { it.size == 2 }
    ?.map { it.operand }

/** `2(x + b)`: el 2 es la mitad del perímetro, así que el lado conocido es `b`. */
private fun halfPerimeterSide(side: Expr): Rational? {
    val factors = productFactors(side) ?: return null
    val coefficient = constantOf(factors[0]) ?: return null
    if (coefficient != TWO) return null
    val sum = factors[1] as? Expr.Sum ?: return null
    if (sum.terms.size != 2) return null
    val varTerm = sum.terms[0]
    if (varTerm.operator != PLUS || varTerm.operand !is Expr.Var) return null
    val constantTerm = sum.terms[1]
    val offset = (constantTerm.operand as? Expr.Const)?.value ?: return null
    // `x - 3` dentro del paréntesis no es un lado de longitud conocida: la figura lo negaría.
    if (constantTerm.operator != PLUS || offset <= Rational.ZERO) return null
    return offset
}

/** `a·x = A`: el ancho conocido `a`, positivo. */
private fun areaWidthOf(side: Expr, variable: String): Rational? {
    val factors = productFactors(side) ?: return null
    val width = constantOf(factors[0]) ?: return null
    val named = factors[1] as? Expr.Var ?: return null
    if (named.name != variable || width <= Rational.ZERO) return null
    return width
}

/**
 * Lectura geométrica de la ecuación, o `null` cuando no la hay.
 *
 * Sólo dos formas se dibujan como rectángulo sin mentir: `2(x + b) = P`, el perímetro de un
 * rectángulo de lados `x` y `b`, y `a·x = A`, el área de un rectángulo de ancho `a`. Todo lo demás
 * queda fuera —`3(x + 2) = 15` no es un perímetro que algún libro dibuja, y un lado escrito como
 * resta no tiene longitud que marcar— porque una figura inventada enseñaría una matemática que el
 * ejercicio no es.
 *
 * La comprobación final es contra la solución del propio ejercicio: si `totalAt(solution)` no
 * devuelve exactamente el total escrito, la plantilla está mintiendo y no se dibuja.
 */
fun rectangleFigureOf(equals: Expr.Equals, variable: String, solution: Rational): RectangleFigure? =
    if (solution <= Rational.ZERO) {
        null
    } else {
        listOf(
            readingOf(equals.left, equals.right, variable, solution),
            readingOf(equals.right, equals.left, variable, solution),
        ).firstNotNullOfOrNull { it }
    }

private fun readingOf(expression: Expr, otherSide: Expr, variable: String, solution: Rational): RectangleFigure? {
    val total = constantOf(unwrap(otherSide)) ?: return null
    if (total <= Rational.ZERO) return null
    val opened = unwrap(expression)
    val asPerimeter = halfPerimeterSide(opened)?.let { FigureMeasure.PERIMETER to it }
    val reading = asPerimeter ?: areaWidthOf(opened, variable)?.let { FigureMeasure.AREA to it } ?: return null
    val figure = RectangleFigure(
        measure = reading.first,
        variable = variable,
        knownSide = reading.second,
        total = total,
    )
    return figure.takeIf { it.totalAt(solution) == total }
}

/**
 * Candidatos para el lado arrastrable: las marcas positivas de la recta del propio ejercicio.
 * Se usan las que el plan ya calcula, en lugar de una escala nueva, para que el estudiante no pueda
 * acabar en un valor que ninguna otra pantalla del mismo ejercicio sabe nombrar.
 */
fun figureCandidates(numberLine: NumberLineModel): List<Rational> =
    numberLine.orderedTicks.filter { it > Rational.ZERO }.distinct()

/**
 * Puntos del arrastre: el dedo se traduce al candidato más cercano, nunca a un número intermedio.
 * Sin candidatos no hay lectura que dar.
 */
fun figureValueAt(fraction: Float, candidates: List<Rational>): Rational? {
    val first = candidates.firstOrNull() ?: return null
    return nearestCandidateAt(fraction, from = first, to = candidates.last(), candidates = candidates)
}

/**
 * Lados en las unidades de dibujo. La proporción es la real hasta donde el marco permite: un
 * rectángulo 1×12 se vería como una raya, así que la desproporción se recorta contra `maxRatio`
 * (cuatro por lado, valor de dibujo y no de layout, con su propia prueba). El recorte sólo afecta
 * al dibujo: las etiquetas siguen diciendo los valores exactos.
 */
fun figureScales(knownSide: Rational, unknownSide: Rational, maxRatio: Rational = Rational.of(4L, 1L)): Pair<Rational, Rational> {
    require(knownSide > Rational.ZERO && unknownSide > Rational.ZERO) { "La figura no dibuja lados cero o negativos." }
    val ratio = knownSide / unknownSide
    return when {
        ratio > maxRatio -> maxRatio to Rational.ONE
        ratio < Rational.ONE / maxRatio -> Rational.ONE to maxRatio
        else -> knownSide to unknownSide
    }
}

/**
 * Rectángulo del ejercicio: el lado conocido va rotulado con su valor y el desconocido se mueve.
 * Arrastrar y las dos flechas llegan al mismo valor exacto, así que un lector de pantalla o un
 * teclado siguen pudiendo cambiar la hipótesis (WCAG 2.1.1).
 */
@Composable
fun RectangleFigureView(
    figure: RectangleFigure,
    candidates: List<Rational>,
    hypothesis: Rational,
    onHypothesisChange: (Rational) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionStyle.current
    val scheme = MaterialTheme.colorScheme
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val (widthUnits, heightUnits) = remember(hypothesis, figure.knownSide) {
        figureScales(figure.knownSide, hypothesis)
    }
    val unit = remember(canvasSize, widthUnits, heightUnits) {
        unitOf(canvasSize, widthUnits, heightUnits)
    }
    val widthPx = unit * widthUnits.approximate().toFloat()
    val heightPx = unit * heightUnits.approximate().toFloat()
    val target = if (canvasSize.width > 0 && canvasSize.height > 0) widthPx to heightPx else null
    val drawn = remember { Animatable(0f) }
    val drawnHeight = remember { Animatable(0f) }
    LaunchedEffect(target, motion.reduced) {
        val size = target ?: return@LaunchedEffect
        if (motion.reduced) {
            drawn.snapTo(size.first)
            drawnHeight.snapTo(size.second)
        } else {
            // Los dos lados crecen juntos: animar sólo el ancho hacía que el primer fotograma de la
            // carta mostrara una línea vertical de alto completo y ancho cero, y el rectángulo
            // llegara después. `contentSpec` y no `popSpec`: el rebote del pop se sale del marco de
            // 148 dp (0.42 de amortiguación sobre el 0.86 de holgura) y acá se está apareciendo
            // dibujo, no celebrando un acierto.
            launch { drawn.animateTo(size.first, MaticaMotion.contentSpec(motion)) }
            launch { drawnHeight.animateTo(size.second, MaticaMotion.contentSpec(motion)) }
        }
    }
    val matches = figure.totalAt(hypothesis) == figure.total
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .semantics {
                    contentDescription =
                        "Rectángulo de lado conocido ${figure.knownSide} y lado que mueves $hypothesis, " +
                            "con ${figure.totalLabelEs} ${figure.totalAt(hypothesis)}. " +
                            "Se pide ${figure.goalEs}. " +
                            if (matches) "Coincide." else "Aún no coincide: desliza o usa las flechas."
                }
                .pointerInput(candidates) {
                    if (candidates.isEmpty()) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        val width = size.width
                        if (width <= 0) return@detectHorizontalDragGestures
                        figureValueAt(change.position.x / width.toFloat(), candidates)
                            ?.let(onHypothesisChange)
                    }
                }
                .padding(horizontal = MaticaSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
                val heightShown = drawnHeight.value
                val left = (size.width - drawn.value) / 2f
                val top = (size.height - heightShown) / 2f
                val sideStroke = 3.dp.toPx()
                // Relleno con el primario traslúcido y contorno con `outline`, no `surfaceVariant`:
                // la figura vive dentro de una `MaticaCard` cuyo contenedor ES `surfaceVariant`, así
                // que el relleno original pintaba del color exacto de su propio fondo y el cuerpo del
                // rectángulo desaparecía. Medido: la región era 99.45 % de un solo color.
                drawRect(
                    color = scheme.primary.copy(alpha = 0.16f),
                    topLeft = Offset(left, top),
                    size = Size(drawn.value, heightShown),
                )
                drawRect(
                    color = scheme.outline,
                    topLeft = Offset(left, top),
                    size = Size(drawn.value, heightShown),
                    style = Stroke(width = sideStroke),
                )
                // El lado que se mueve es el izquierdo, pintado con el color primario y más grueso
                // para que se vea cuál de los cuatro lados es el que el estudiante está cambiando.
                drawLine(
                    color = scheme.primary,
                    start = Offset(left, top),
                    end = Offset(left, top + heightShown),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = scheme.primary,
                    start = Offset(left + drawn.value, top),
                    end = Offset(left + drawn.value, top + heightShown),
                    strokeWidth = sideStroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
                drawCircle(
                    color = scheme.primary,
                    radius = 9.dp.toPx(),
                    center = Offset(left, top + heightShown),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaticaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaticaIconButton(
                icon = MaticaIcon.BACK,
                contentDescription = "Lado anterior",
                onClick = { stepCandidate(candidates, hypothesis, -1, onHypothesisChange) },
                modifier = Modifier.size(MaticaLayout.touchTarget),
            )
            Text(
                text = "Lado ${hypothesis} · ${figure.totalLabelEs} ${figure.totalAt(hypothesis)}",
                style = MaterialTheme.typography.titleMedium,
                color = if (matches) scheme.primary else scheme.onSurface,
            )
            MaticaIconButton(
                icon = MaticaIcon.FORWARD,
                contentDescription = "Lado siguiente",
                onClick = { stepCandidate(candidates, hypothesis, +1, onHypothesisChange) },
                modifier = Modifier.size(MaticaLayout.touchTarget),
            )
        }
    }
}

private fun unitOf(size: IntSize, widthUnits: Rational, heightUnits: Rational): Float {
    if (size.width <= 0 || size.height <= 0) return 0f
    val horizontal = size.width / widthUnits.approximate().toFloat()
    val vertical = size.height / heightUnits.approximate().toFloat()
    return minOf(horizontal, vertical) * 0.86f
}

private fun stepCandidate(candidates: List<Rational>, current: Rational, delta: Int, onHypothesisChange: (Rational) -> Unit) {
    if (candidates.isEmpty()) return
    val index = candidates.indexOfFirst { it == current }
    val next = if (index < 0) {
        candidates.first()
    } else {
        candidates[(index + delta).coerceIn(0, candidates.lastIndex)]
    }
    onHypothesisChange(next)
}
