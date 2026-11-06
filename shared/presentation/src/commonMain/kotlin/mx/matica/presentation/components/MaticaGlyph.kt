package mx.matica.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Destinos de la barra inferior, en el orden exacto del diseño. */
enum class MaticaIcon { HOME, ROUTE, LESSON, REWARD, PROFILE, BACK, FORWARD, CHECK, CLOSE, REFRESH, WARNING, GEM, XP, FLAME, LOCK }

/**
 * Iconos de línea dibujados en canvas. El repo no trae binarios ni XML de Material Icons y el
 * trazo propio garantiza el mismo grosor en claro y oscuro, algo que una fuente de iconos no
 * controla. Ningún icono depende del color para comunicar: siempre va acompañado de texto.
 */
@Composable
fun MaticaGlyph(
    icon: MaticaIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidthDp: Float = 2f,
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = Stroke(width = strokeWidthDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        val pad = w * 0.16f
        when (icon) {
            MaticaIcon.HOME -> {
                drawLine(tint, Offset(pad, h * 0.5f), Offset(w / 2f, pad), stroke.width, cap = StrokeCap.Round)
                drawPath(homePath(w, h, pad), tint, style = stroke)
            }
            MaticaIcon.ROUTE -> {
                val path = Path().apply {
                    moveTo(pad, h - pad)
                    cubicTo(w * 0.15f, h * 0.55f, w * 0.85f, h * 0.45f, w - pad, pad)
                }
                drawPath(path, tint, style = Stroke(width = stroke.width, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke.width * 3f, stroke.width * 2.5f))))
                drawCircle(tint, radius = stroke.width * 1.3f, center = Offset(pad, h - pad))
                drawCircle(tint, radius = stroke.width * 1.3f, center = Offset(w - pad, pad))
            }
            MaticaIcon.LESSON -> {
                drawRoundRect(tint, Offset(pad, pad * 1.2f), Size(w - 2 * pad, h - 2.4f * pad), cornerRadius = CornerRadius(pad * 0.35f), style = stroke)
                drawLine(tint, Offset(w / 2f, pad * 1.2f), Offset(w / 2f, h - pad * 1.2f), stroke.width)
            }
            MaticaIcon.REWARD, MaticaIcon.GEM -> {
                val gem = Path().apply {
                    moveTo(w * 0.5f, pad * 0.9f)
                    lineTo(w - pad, h * 0.42f)
                    lineTo(w * 0.5f, h - pad * 0.9f)
                    lineTo(pad, h * 0.42f)
                    close()
                }
                drawPath(gem, tint, style = stroke)
                drawLine(tint, Offset(pad, h * 0.42f), Offset(w - pad, h * 0.42f), stroke.width)
            }
            MaticaIcon.PROFILE -> {
                drawCircle(tint, radius = w * 0.17f, center = Offset(w / 2f, h * 0.36f), style = stroke)
                val shoulders = Path().apply {
                    moveTo(pad, h - pad * 0.8f)
                    quadraticTo(w / 2f, h * 0.52f, w - pad, h - pad * 0.8f)
                }
                drawPath(shoulders, tint, style = stroke)
            }
            MaticaIcon.BACK -> drawArrow(tint, stroke.width, w, h, reverse = true)
            MaticaIcon.FORWARD -> drawArrow(tint, stroke.width, w, h, reverse = false)
            MaticaIcon.CHECK -> {
                drawLine(tint, Offset(pad, h * 0.55f), Offset(w * 0.42f, h - pad * 1.1f), stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.42f, h - pad * 1.1f), Offset(w - pad, h * 0.3f), stroke.width, cap = StrokeCap.Round)
            }
            MaticaIcon.CLOSE -> {
                drawLine(tint, Offset(pad, pad), Offset(w - pad, h - pad), stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w - pad, pad), Offset(pad, h - pad), stroke.width, cap = StrokeCap.Round)
            }
            MaticaIcon.REFRESH -> {
                drawArc(tint, startAngle = 30f, sweepAngle = 250f, useCenter = false, style = stroke)
                drawLine(tint, Offset(w * 0.78f, h * 0.16f), Offset(w * 0.86f, h * 0.34f), stroke.width, cap = StrokeCap.Round)
            }
            MaticaIcon.WARNING -> {
                val triangle = Path().apply {
                    moveTo(w / 2f, pad)
                    lineTo(w - pad, h - pad)
                    lineTo(pad, h - pad)
                    close()
                }
                drawPath(triangle, tint, style = stroke)
                drawLine(tint, Offset(w / 2f, h * 0.42f), Offset(w / 2f, h * 0.66f), stroke.width, cap = StrokeCap.Round)
                drawCircle(tint, radius = stroke.width * 0.7f, center = Offset(w / 2f, h * 0.78f))
            }
            MaticaIcon.XP -> {
                val star = Path()
                repeat(5) { index ->
                    val angleOuter = -90f + index * 72f
                    val angleInner = angleOuter + 36f
                    star.addPoint(w, h, radius = w * 0.42f, angleDeg = angleOuter, first = index == 0)
                    star.addPoint(w, h, radius = w * 0.19f, angleDeg = angleInner)
                }
                star.close()
                drawPath(star, tint, style = stroke)
            }
            MaticaIcon.FLAME -> {
                val flame = Path().apply {
                    moveTo(w / 2f, pad * 0.8f)
                    cubicTo(w * 0.86f, h * 0.45f, w * 0.72f, h - pad, w / 2f, h - pad * 0.8f)
                    cubicTo(w * 0.28f, h - pad, w * 0.14f, h * 0.45f, w / 2f, pad * 0.8f)
                }
                drawPath(flame, tint, style = stroke)
            }
            MaticaIcon.LOCK -> {
                drawRoundRect(tint, Offset(pad, h * 0.45f), Size(w - 2 * pad, h * 0.4f), cornerRadius = CornerRadius(pad * 0.3f), style = stroke)
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.3f, h * 0.2f),
                    size = Size(w * 0.4f, h * 0.4f),
                    style = stroke,
                )
            }
        }
    }
}

private fun Path.addPoint(w: Float, h: Float, radius: Float, angleDeg: Float, first: Boolean = false) {
    val radians = angleDeg * kotlin.math.PI.toFloat() / 180f
    val point = Offset(w / 2f + radius * kotlin.math.cos(radians), h / 2f + radius * kotlin.math.sin(radians))
    if (first) moveTo(point.x, point.y) else lineTo(point.x, point.y)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(
    tint: Color,
    stroke: Float,
    w: Float,
    h: Float,
    reverse: Boolean,
) {
    val from = if (reverse) Offset(w * 0.78f, h / 2f) else Offset(w * 0.22f, h / 2f)
    val to = if (reverse) Offset(w * 0.22f, h / 2f) else Offset(w * 0.78f, h / 2f)
    drawLine(tint, from, to, stroke, cap = StrokeCap.Round)
    val tip = to
    val wing = if (reverse) -1f else 1f
    drawLine(tint, tip, Offset(tip.x + wing * w * 0.18f, tip.y - h * 0.18f), stroke, cap = StrokeCap.Round)
    drawLine(tint, tip, Offset(tip.x + wing * w * 0.18f, tip.y + h * 0.18f), stroke, cap = StrokeCap.Round)
}

private fun homePath(w: Float, h: Float, pad: Float) = Path().apply {
    moveTo(pad, h * 0.5f)
    lineTo(pad, h - pad)
    lineTo(w - pad, h - pad)
    lineTo(w - pad, h * 0.5f)
    lineTo(w / 2f, pad)
    close()
}
