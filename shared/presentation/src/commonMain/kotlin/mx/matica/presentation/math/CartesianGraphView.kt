package mx.matica.presentation.math

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
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
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.math.numeric.Rational
import kotlin.math.abs

/** Punto muestreado del plano: ambos componentes siguen exactos hasta el dibujo. */
data class PlotPoint(val x: Rational, val y: Rational)

/** Coordenada ya normalizada 0..1 dentro de la caja de dibujo. */
data class NormalizedPoint(val xFraction: Float, val yFraction: Float)

/**
 * Serie trazada. Los cortes entre segmentos son la información: una recta vertical fingida entre dos
 * ramas de una hipérbola dibujaría una asíntota que la función no tiene.
 */
data class PlotSeries(
    val segments: List<List<NormalizedPoint>>,
    val gaps: Int,
    val outOfRange: Int,
)

/**
 * Muestrea `f` en `samples` puntos exactos del intervalo. Un `null` significa «no está definido
 * aquí» y corta el trazo; nunca se interpola entre un punto definido y uno indefinido.
 */
fun samplePlot(
    f: (Rational) -> Rational?,
    xFrom: Rational,
    xTo: Rational,
    samples: Int,
): List<PlotPoint?> {
    require(samples >= 2) { "Una gráfica necesita al menos dos muestras." }
    require(xTo > xFrom) { "El eje horizontal debe crecer hacia la derecha." }
    val step = (xTo - xFrom) / (Rational.of((samples - 1).toLong(), 1L))
    return (0L until samples.toLong()).map { index ->
        val x = xFrom + step * index
        f(x)?.let { y -> PlotPoint(x, y) }
    }
}

/**
 * Convierte las muestras en segmentos dibujables descartando lo que cae fuera del rango vertical
 * pedido: un valor fuera de la ventana no se recorta, se corta, porque recortarlo dibujaría una
 * meseta falsa pegada al borde.
 */
fun plotSeries(
    points: List<PlotPoint?>,
    xFrom: Rational,
    xTo: Rational,
    yFrom: Rational,
    yTo: Rational,
): PlotSeries {
    val segments = mutableListOf<List<NormalizedPoint>>()
    var current = mutableListOf<NormalizedPoint>()
    var gaps = 0
    var outOfRange = 0
    points.forEach { point ->
        if (point == null) {
            gaps += 1
            if (current.isNotEmpty()) segments.add(current.toList())
            current.clear()
            return@forEach
        }
        if (point.y < yFrom || point.y > yTo) {
            outOfRange += 1
            if (current.isNotEmpty()) segments.add(current.toList())
            current.clear()
            return@forEach
        }
        current.add(
            NormalizedPoint(
                xFraction = positionOf(point.x, xFrom, xTo),
                yFraction = 1f - positionOf(point.y, yFrom, yTo),
            ),
        )
    }
    if (current.isNotEmpty()) segments.add(current.toList())
    return PlotSeries(segments = segments, gaps = gaps, outOfRange = outOfRange)
}

/**
 * Clave del revelado: tamaño e identidad de lo trazado (cuántos trazos, cuántos puntos, cuántos
 * cortes y dónde empiezan/terminan), no la serie entera. `seriesOf` se recalcula fuera de la pantalla
 * en cada recomposición y usar la lista de clave reiniciaba la curva desde cero.
 */
data class PlotRevealKey(
    val segments: Int,
    val points: Int,
    val breaks: Int,
    val firstX: Float,
    val lastX: Float,
    val lastY: Float,
)

fun plotRevealKey(series: PlotSeries): PlotRevealKey {
    val first = series.segments.firstOrNull()?.firstOrNull()
    val last = series.segments.lastOrNull()?.lastOrNull()
    return PlotRevealKey(
        segments = series.segments.size,
        points = series.segments.sumOf { it.size },
        breaks = series.gaps + series.outOfRange,
        firstX = first?.xFraction ?: 0f,
        lastX = last?.xFraction ?: 0f,
        lastY = last?.yFraction ?: 0f,
    )
}

/**
 * X que pide un arrastre sobre el plano: la fracción del ancho se traduce al candidato más cercano
 * dentro de la ventana dibujada. El punto nunca queda en una coordenada intermedia inventada.
 */
fun trackerXAt(fraction: Float, xFrom: Rational, xTo: Rational, candidates: List<Rational>): Rational? {
    if (candidates.isEmpty()) return null
    val clamped = fraction.coerceIn(0f, 1f)
    return candidates.minByOrNull { abs(positionOf(it, xFrom, xTo) - clamped) }
}

/**
 * Tramos visibles de la serie, ya en píxeles del canvas. La medición ocurre fuera de la fase de
 * dibujo porque `PathMeasure.setPath` muta lo que mide y Compose puede volver a ejecutar `draw`
 * cuando quiera. Con `progress` en 1 devuelve la curva completa.
 */
internal fun plotVisibleSegments(series: PlotSeries, sizePx: IntSize, progress: Float): List<Path> {
    if (sizePx.width <= 0 || sizePx.height <= 0 || progress <= 0f) return emptyList()
    val measured = PathMeasure()
    return series.segments.mapNotNull { segment ->
        if (segment.size < 2) return@mapNotNull null
        val path = Path()
        segment.forEachIndexed { index, point ->
            val x = sizePx.width * point.xFraction
            val y = sizePx.height * point.yFraction
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        measured.setPath(path, false)
        val visible = Path()
        // El trazo progresivo se saca de la longitud real del camino: un `phase` fingido dejaría la
        // curva punteada en lugar de dibujarse de izquierda a derecha.
        if (measured.getSegment(0f, measured.length * progress.coerceIn(0f, 1f), visible, true)) visible else null
    }
}

/**
 * Plano cartesiano con el trazo progresivo: la curva se dibuja sola de izquierda a derecha, que es
 * el orden en que se lee una función. Con movimiento reducido aparece completa.
 *
 * Con `trackerY` además hay un punto arrastrable sobre la recta: el dedo sólo mueve la coordenada
 * horizontal, enganchada a un candidato exacto, y la vertical sale de la propia función. Así el
 * punto no puede caerse de la recta ni parar en un valor que el motor no sepa nombrar.
 */
@Composable
fun CartesianGraphView(
    series: PlotSeries,
    modifier: Modifier = Modifier,
    axisLabelX: String = "x",
    axisLabelY: String = "y",
    solutionMarkers: List<Rational> = emptyList(),
    xFrom: Rational = Rational.ZERO,
    xTo: Rational = Rational.ONE,
    yFrom: Rational = Rational.ZERO,
    yTo: Rational = Rational.ONE,
    trackerY: ((Rational) -> Rational)? = null,
    trackerX: Rational? = null,
    trackerCandidates: List<Rational> = emptyList(),
    onTrackerXChange: (Rational) -> Unit = {},
) {
    val motion = LocalMotionStyle.current
    val draw = remember { Animatable(if (motion.reduced) 1f else 0f) }
    val shape = plotRevealKey(series)
    LaunchedEffect(shape, motion.reduced) {
        if (motion.reduced) draw.snapTo(1f) else draw.animateTo(1f, MaticaMotion.contentSpec(motion))
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.tertiary
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val tracedSegments = remember(series, canvasSize, draw.value) {
        plotVisibleSegments(series, canvasSize, draw.value)
    }
    val tracked = trackerY?.let { function -> trackerX?.let { x -> x to function(x) } }
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 220 dp son la caja que ocupan los ejes y las curvas (medida de dibujo, con su escala
            // propia); no es espaciado entre elementos, así que no debe convertirse en un token.
            .height(220.dp)
            .semantics {
                contentDescription =
                    "Gráfica en el plano de $axisLabelX contra $axisLabelY, con ${series.segments.size} " +
                        "trazos continuos y ${series.gaps + series.outOfRange} cortes." +
                        (tracked?.let { " Punto en la recta: $axisLabelX = ${it.first}, $axisLabelY = ${it.second}." } ?: "")
            }
            .pointerInput(trackerCandidates, xFrom, xTo) {
                if (trackerY == null || trackerCandidates.isEmpty()) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    val width = size.width
                    if (width <= 0) return@detectHorizontalDragGestures
                    trackerXAt(change.position.x / width.toFloat(), xFrom, xTo, trackerCandidates)
                        ?.let(onTrackerXChange)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
            val horizontal = size.height / 2f
            val vertical = size.width / 2f
            drawLine(gridColor, Offset(0f, horizontal), Offset(size.width, horizontal), 2.dp.toPx())
            drawLine(gridColor, Offset(vertical, 0f), Offset(vertical, size.height), 2.dp.toPx())
            tracedSegments.forEach { segment ->
                // 3 dp de grueso: medida del trazo, no de layout.
                drawPath(path = segment, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            solutionMarkers.forEach { solution ->
                val x = size.width * positionOf(solution, xFrom, xTo)
                drawCircle(color = markerColor, radius = 6.dp.toPx(), center = Offset(x, horizontal))
            }
            tracked?.let { point ->
                drawCircle(
                    color = lineColor,
                    radius = 9.dp.toPx(),
                    center = Offset(
                        size.width * positionOf(point.first, xFrom, xTo),
                        // El eje vertical está invertido en píxeles: invertir los extremos del
                        // intervalo es la única manera de que el punto caiga sobre la recta dibujada.
                        size.height * positionOf(point.second, yTo, yFrom),
                    ),
                )
            }
        }
        Text(
            text = axisLabelX,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(MaticaSpacing.xs),
        )
        Text(
            text = axisLabelY,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart).padding(MaticaSpacing.xs),
        )
        tracked?.let { point ->
            Text(
                text = "$axisLabelX = ${point.first} · $axisLabelY = ${point.second}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(MaticaSpacing.xs),
            )
        }
    }
}
