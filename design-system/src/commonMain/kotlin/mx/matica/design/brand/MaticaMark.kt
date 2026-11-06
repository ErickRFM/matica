package mx.matica.design.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * Marca de Mática: una «M» dibujada como una ruta, con un punto de llegada.
 *
 * No es un binario ni una fuente de iconos: el trazo se mide en el mismo `Canvas` que el resto de
 * glifos de la app, así que conserva el grosor y el contraste en los dos temas sin exportar PNG.
 * La geometría vive en funciones puras para que el trazo progresivo se pueda probar sin pantalla.
 */
@Composable
fun MaticaMark(
    modifier: Modifier = Modifier.size(96.dp),
    drawn: Float = 1f,
    travelingDot: Float? = null,
    tile: Color = MaterialTheme.colorScheme.primaryContainer,
    stroke: Color = MaterialTheme.colorScheme.primary,
    destination: Color = MaterialTheme.colorScheme.tertiary,
) {
    Canvas(modifier = modifier) {
        val side = size.minDimension
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val corner = side * 0.26f
        drawRoundRect(
            color = tile,
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = CornerRadius(corner, corner),
        )

        val strokeWidth = side * STROKE_FRACTION
        maticaMarkSegments(drawn).forEach { segment ->
            drawLine(
                color = stroke,
                start = Offset(left + segment.startX * side, top + segment.startY * side),
                end = Offset(left + segment.endX * side, top + segment.endY * side),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        // El punto de llegada aparece cuando el trazo ya lo alcanzó; antes sería una meta sin ruta.
        val dotProgress = ((drawn - DESTINATION_START) / (1f - DESTINATION_START)).coerceIn(0f, 1f)
        if (dotProgress > 0f) {
            val (destX, destY) = maticaMarkPoint(1f)
            drawCircle(
                color = destination,
                radius = side * DESTINATION_RADIUS * dotProgress,
                center = Offset(left + destX * side, top + destY * side),
            )
        }

        // La mota viaja por la ruta ya trazada: es la parte de «carga» de la marca. Sólo se dibuja
        // hasta donde llegó el trazo, para que no aparezca flotando en un tramo que aún no existe.
        // Lleva la tinta del destino para leerse como avance hacia el punto de llegada; el halo es la
        // misma tinta al 28 %, así no compite un segundo color con el trazo.
        if (travelingDot != null && dotProgress > 0f) {
            val travel = travelingDot.coerceIn(0f, drawn)
            val (moteX, moteY) = maticaMarkPoint(travel)
            val center = Offset(left + moteX * side, top + moteY * side)
            drawCircle(color = destination.copy(alpha = 0.28f), radius = side * MOTE_HALO_RADIUS, center = center)
            drawCircle(color = destination, radius = side * MOTE_RADIUS, center = center)
        }
    }
}

/** Un tramo recto de la marca, en coordenadas normalizadas (0..1) del cuadro que la dibuja. */
data class MarkSegment(val startX: Float, val startY: Float, val endX: Float, val endY: Float)

/**
 * Vértices de la «M» en zigzag. Se declara como lista para que el trazo progresivo reparta la
 * longitud real de cada tramo: un progreso lineal sobre los cuatro tramos haría que los verticales
 * largos se dibujaran a la misma velocidad que los cortos y el gesto se vería a tirones.
 */
private val MarkPoints: List<Pair<Float, Float>> = listOf(
    0.26f to 0.70f,
    0.38f to 0.30f,
    0.50f to 0.56f,
    0.62f to 0.30f,
    0.74f to 0.70f,
)

/** Proporción del cuadro que ocupa el trazo; el resto es el aire de la marca. */
private const val STROKE_FRACTION = 0.075f
private const val DESTINATION_RADIUS = 0.045f
private const val DESTINATION_START = 0.82f
private const val MOTE_RADIUS = 0.030f
private const val MOTE_HALO_RADIUS = 0.060f

/**
 * Tramos que ya existen para [drawn] (0..1). El progreso se reparte por longitud acumulada, así que
 * el último tramo se recorta a la mitad exacta cuando el trazo va a mitad de camino.
 */
fun maticaMarkSegments(drawn: Float): List<MarkSegment> {
    val fraction = drawn.coerceIn(0f, 1f)
    if (fraction <= 0f) return emptyList()
    val total = totalLength()
    val target = total * fraction
    val result = mutableListOf<MarkSegment>()
    var consumed = 0f
    for (index in 0 until MarkPoints.lastIndex) {
        val (x1, y1) = MarkPoints[index]
        val (x2, y2) = MarkPoints[index + 1]
        val length = distance(x1, y1, x2, y2)
        if (consumed >= target) break
        val take = (target - consumed).coerceAtMost(length)
        if (take <= 0f) break
        val ratio = take / length
        result += MarkSegment(x1, y1, x1 + (x2 - x1) * ratio, y1 + (y2 - y1) * ratio)
        if (ratio >= 1f) consumed += length else break
    }
    return result
}

/** Punto de la marca a [fraction] (0..1) de su longitud total. Lo usa la mota que viaja en el splash. */
fun maticaMarkPoint(fraction: Float): Pair<Float, Float> {
    val clamped = fraction.coerceIn(0f, 1f)
    val total = totalLength()
    val target = total * clamped
    var consumed = 0f
    for (index in 0 until MarkPoints.lastIndex) {
        val (x1, y1) = MarkPoints[index]
        val (x2, y2) = MarkPoints[index + 1]
        val length = distance(x1, y1, x2, y2)
        if (consumed + length >= target) {
            val ratio = if (length == 0f) 0f else (target - consumed) / length
            return (x1 + (x2 - x1) * ratio) to (y1 + (y2 - y1) * ratio)
        }
        consumed += length
    }
    return MarkPoints.last()
}

/** Longitud total de la marca, normalizada al cuadro 0..1. */
fun maticaMarkLength(): Float = totalLength()

private fun totalLength(): Float {
    var total = 0f
    for (index in 0 until MarkPoints.lastIndex) {
        val (x1, y1) = MarkPoints[index]
        val (x2, y2) = MarkPoints[index + 1]
        total += distance(x1, y1, x2, y2)
    }
    return total
}

private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
    sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
