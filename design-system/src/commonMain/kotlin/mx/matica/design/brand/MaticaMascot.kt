package mx.matica.design.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.tokens.MaticaPalette

/**
 * Estados expresivos de la mascota de Mática.
 */
enum class MascotEmotion {
    /** Estado neutro amigable: parpadeo suave ocasional. */
    IDLE,
    /** Sonrisa amplia de bienvenida o ánimo. */
    HAPPY,
    /** Ojos orientados hacia arriba/lateral, evaluando un paso o esperando respuesta. */
    THINKING,
    /** Celebración: ojos cerrados de alegría o destello y sonrisa máxima. */
    CELEBRATING,
}

/**
 * Mascota oficial de Mática, renderizada de forma vectorial en `Canvas`.
 *
 * Sigue fielmente la geometría y proporciones del diseño de Figma (`1:9`, `1:148`):
 * un cuerpo circular en tono lima (#B7EF59), ojos con pupila y brillo (#17263F)
 * y una sonrisa en arco con trazo redondeado.
 *
 * Se dibuja en Canvas puro: no depende de binarios rasterizados pesados, escala con
 * nitidez matemática a cualquier densidad de pantalla (dpi) y respeta la reducción
 * de movimiento del sistema operativo.
 */
@Composable
fun MaticaMascot(
    modifier: Modifier = Modifier.size(160.dp),
    emotion: MascotEmotion = MascotEmotion.IDLE,
    bodyColor: Color = MaticaPalette.Lime,
    inkColor: Color = MaticaPalette.Ink,
    contentDescription: String = "Mascota de Mática",
) {
    val motion = LocalMotionStyle.current
    val reduced = motion.reduced

    // Animación de rebote sutil para celebración
    val bounceY = if (!reduced && emotion == MascotEmotion.CELEBRATING) {
        val transition = rememberInfiniteTransition(label = "mascot-bounce")
        val offset by transition.animateFloat(
            initialValue = 0f,
            targetValue = -8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bounceOffset",
        )
        offset
    } else {
        0f
    }

    // Parpadeo periódico en estado IDLE
    val eyeOpenness = remember { Animatable(1f) }
    LaunchedEffect(emotion, reduced) {
        if (reduced || emotion != MascotEmotion.IDLE) {
            eyeOpenness.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            kotlinx.coroutines.delay(2800)
            eyeOpenness.animateTo(0.1f, tween(80))
            eyeOpenness.animateTo(1f, tween(100))
        }
    }

    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        val side = size.minDimension
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f + (bounceY * density)
        val center = Offset(left + side / 2f, top + side / 2f)
        val radius = side / 2f

        // 1. Cuerpo circular principal (Figma 178x178 px)
        drawCircle(
            color = bodyColor,
            radius = radius * 0.96f,
            center = center,
            style = Fill,
        )

        // Borde exterior suave de definición
        drawCircle(
            color = inkColor.copy(alpha = 0.12f),
            radius = radius * 0.96f,
            center = center,
            style = Stroke(width = side * 0.02f),
        )

        // Proporciones relativas según Figma
        // Ojo 1: x_rel ~ 0.28, y_rel ~ 0.35; Ojo 2: x_rel ~ 0.61, y_rel ~ 0.35
        val eyeRadius = side * 0.06f
        val eyeY = top + side * 0.37f
        val leftEyeX = left + side * 0.34f
        val rightEyeX = left + side * 0.66f

        val strokeWidth = side * 0.038f

        // 2. Ojos según estado emocional
        when (emotion) {
            MascotEmotion.CELEBRATING -> {
                // Ojos en arco hacia arriba felices (^ ^)
                drawEyeArc(center = Offset(leftEyeX, eyeY), radius = eyeRadius * 1.1f, color = inkColor, strokeWidth = strokeWidth)
                drawEyeArc(center = Offset(rightEyeX, eyeY), radius = eyeRadius * 1.1f, color = inkColor, strokeWidth = strokeWidth)
            }
            MascotEmotion.THINKING -> {
                // Ojo izquierdo mira a la esquina superior derecha
                drawCircle(
                    color = inkColor,
                    radius = eyeRadius,
                    center = Offset(leftEyeX, eyeY),
                )
                drawCircle(
                    color = Color.White,
                    radius = eyeRadius * 0.35f,
                    center = Offset(leftEyeX + eyeRadius * 0.35f, eyeY - eyeRadius * 0.35f),
                )
                // Ojo derecho ligeramente más abierto
                drawCircle(
                    color = inkColor,
                    radius = eyeRadius * 1.15f,
                    center = Offset(rightEyeX, eyeY),
                )
                drawCircle(
                    color = Color.White,
                    radius = eyeRadius * 0.4f,
                    center = Offset(rightEyeX + eyeRadius * 0.4f, eyeY - eyeRadius * 0.4f),
                )
            }
            else -> {
                // IDLE / HAPPY con parpadeo
                val currentOpenness = eyeOpenness.value
                if (currentOpenness < 0.25f) {
                    // Línea horizontal de ojo cerrado
                    drawLine(
                        color = inkColor,
                        start = Offset(leftEyeX - eyeRadius, eyeY),
                        end = Offset(leftEyeX + eyeRadius, eyeY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = inkColor,
                        start = Offset(rightEyeX - eyeRadius, eyeY),
                        end = Offset(rightEyeX + eyeRadius, eyeY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                } else {
                    // Ojo izquierdo
                    drawOval(
                        color = inkColor,
                        topLeft = Offset(leftEyeX - eyeRadius, eyeY - eyeRadius * currentOpenness),
                        size = Size(eyeRadius * 2f, eyeRadius * 2f * currentOpenness),
                    )
                    // Brillo blanco en pupila izquierda
                    drawCircle(
                        color = Color.White,
                        radius = eyeRadius * 0.32f * currentOpenness,
                        center = Offset(leftEyeX - eyeRadius * 0.25f, eyeY - eyeRadius * 0.25f * currentOpenness),
                    )

                    // Ojo derecho
                    drawOval(
                        color = inkColor,
                        topLeft = Offset(rightEyeX - eyeRadius, eyeY - eyeRadius * currentOpenness),
                        size = Size(eyeRadius * 2f, eyeRadius * 2f * currentOpenness),
                    )
                    // Brillo blanco en pupila derecha
                    drawCircle(
                        color = Color.White,
                        radius = eyeRadius * 0.32f * currentOpenness,
                        center = Offset(rightEyeX - eyeRadius * 0.25f, eyeY - eyeRadius * 0.25f * currentOpenness),
                    )
                }
            }
        }

        // 3. Sonrisa (Figma: ancho ~ 50 px, alto ~ 12.5 px centrado en x)
        val mouthY = top + side * 0.62f
        val mouthHalfWidth = when (emotion) {
            MascotEmotion.HAPPY, MascotEmotion.CELEBRATING -> side * 0.17f
            MascotEmotion.THINKING -> side * 0.11f
            MascotEmotion.IDLE -> side * 0.14f
        }
        val mouthDepth = when (emotion) {
            MascotEmotion.HAPPY, MascotEmotion.CELEBRATING -> side * 0.10f
            MascotEmotion.THINKING -> side * 0.04f
            MascotEmotion.IDLE -> side * 0.07f
        }

        val mouthPath = Path().apply {
            moveTo(center.x - mouthHalfWidth, mouthY)
            quadraticTo(
                center.x, mouthY + mouthDepth * 2f,
                center.x + mouthHalfWidth, mouthY,
            )
        }

        drawPath(
            path = mouthPath,
            color = inkColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // 4. Pequeño sonrojo en mejillas para emociones felices
        if (emotion == MascotEmotion.HAPPY || emotion == MascotEmotion.CELEBRATING) {
            val cheekRadius = side * 0.06f
            val cheekY = top + side * 0.52f
            drawCircle(
                color = MaticaPalette.Teal.copy(alpha = 0.22f),
                radius = cheekRadius,
                center = Offset(left + side * 0.22f, cheekY),
            )
            drawCircle(
                color = MaticaPalette.Teal.copy(alpha = 0.22f),
                radius = cheekRadius,
                center = Offset(left + side * 0.78f, cheekY),
            )
        }
    }
}

/** Dibuja un arco de ojo sonriente (^). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyeArc(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
) {
    val path = Path().apply {
        moveTo(center.x - radius, center.y + radius * 0.3f)
        quadraticTo(center.x, center.y - radius * 0.8f, center.x + radius, center.y + radius * 0.3f)
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}
