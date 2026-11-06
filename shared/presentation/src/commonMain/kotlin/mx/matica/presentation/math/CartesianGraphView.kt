package mx.matica.presentation.math

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.math.numeric.Rational

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
 * Plano cartesiano con el trazo progresivo: la curva se dibuja sola de izquierda a derecha, que es
 * el orden en que se lee una función. Con movimiento reducido aparece completa.
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
) {
    val motion = LocalMotionStyle.current
    val draw = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(series, motion.reduced) {
        if (motion.reduced) draw.snapTo(1f) else draw.animateTo(1f, MaticaMotion.contentSpec(motion))
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.tertiary
    val measured = remember { PathMeasure() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics {
                contentDescription =
                    "Gráfica en el plano de $axisLabelX contra $axisLabelY, con ${series.segments.size} " +
                        "trazos continuos y ${series.gaps + series.outOfRange} cortes."
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val horizontal = size.height / 2f
            val vertical = size.width / 2f
            drawLine(gridColor, Offset(0f, horizontal), Offset(size.width, horizontal), 2.dp.toPx())
            drawLine(gridColor, Offset(vertical, 0f), Offset(vertical, size.height), 2.dp.toPx())
            series.segments.forEach { segment ->
                if (segment.size < 2) return@forEach
                val path = Path()
                segment.forEachIndexed { index, point ->
                    val x = size.width * point.xFraction
                    val y = size.height * point.yFraction
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                // El trazo progresivo se saca de la longitud real del camino: un `phase` fingido
                // dejaría la curva punteada en lugar de dibujarse de izquierda a derecha.
                measured.setPath(path, false)
                val visible = Path()
                if (measured.getSegment(0f, measured.length * draw.value, visible, true)) {
                    drawPath(
                        path = visible,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            solutionMarkers.forEach { solution ->
                val x = size.width * positionOf(solution, xFrom, xTo)
                drawCircle(color = markerColor, radius = 6.dp.toPx(), center = Offset(x, horizontal))
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
    }
}
