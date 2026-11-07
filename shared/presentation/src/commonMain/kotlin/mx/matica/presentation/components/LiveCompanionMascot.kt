package mx.matica.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
 * Estados emocionales reactivos del compañero de estudio Mática.
 */
enum class LiveCompanionState(val labelEs: String) {
    /** Estado neutro amigable: respiración y parpadeo periódico. */
    IDLE("en reposo"),
    /** Cuando el alumno se detiene a reflexionar en una pregunta o ejercicio. */
    THINKING("pensando"),
    /** Ánimo y apoyo afectuoso tras un error leve o paso complejo. */
    ENCOURAGING("dando ánimo"),
    /** Celebración entusiasta al acertar un paso o completar una racha. */
    CHEERING("celebrando"),
}

/**
 * Calcula el desplazamiento vertical (bobbing) en dp según el progreso de la animación [0f, 1f].
 * Función pura desacoplada para pruebas unitarias deterministas.
 */
fun companionBobbingOffsetDp(reduced: Boolean, progress: Float): Float {
    if (reduced) return 0f
    // Oscilación suave entre -3 dp y +3 dp
    return -3f + 6f * progress.coerceIn(0f, 1f)
}

/**
 * Mascota compañera reactiva compacta (40-56 dp) para cabeceras y esquinas activas de lección.
 *
 * Emplea la geometría circular y expresividad vectorial de la mascota de Mática,
 * adaptada con nitidez a escalas pequeñas.
 *
 * Características principales:
 * - Flotación sutil vertical (-3 dp a +3 dp con [rememberInfiniteTransition]).
 * - Expresiones faciales reactivas según [LiveCompanionState].
 * - Parpadeo orgánico periódico en [LiveCompanionState.IDLE].
 * - Bocadillo de diálogo opcional con flecha/indicador hacia la mascota.
 * - Respeto estricto del modo accesible [LocalMotionStyle.current.reduced].
 */
@Composable
fun LiveCompanionMascot(
    state: LiveCompanionState = LiveCompanionState.IDLE,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    speechBubble: String? = null,
    bodyColor: Color = MaticaPalette.Lime,
    inkColor: Color = MaticaPalette.Ink,
    contentDescription: String? = null,
) {
    val motion = LocalMotionStyle.current
    val reduced = motion.reduced

    // Flotación vertical (bobbing)
    val bobbingTransition = rememberInfiniteTransition(label = "companion-bobbing")
    val bobbingProgress by bobbingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "companion-bobbing-progress",
    )
    val bobbingOffset = companionBobbingOffsetDp(reduced, bobbingProgress)

    // Parpadeo periódico en estado IDLE
    val eyeOpenness = remember { Animatable(1f) }
    LaunchedEffect(state, reduced) {
        if (reduced || state != LiveCompanionState.IDLE) {
            eyeOpenness.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            kotlinx.coroutines.delay(2600)
            eyeOpenness.animateTo(0.1f, tween(70))
            eyeOpenness.animateTo(1f, tween(90))
        }
    }

    // Efecto de pulso / respiración sutil
    val breathingScale = if (!reduced && state == LiveCompanionState.IDLE) {
        val breatheTransition = rememberInfiniteTransition(label = "companion-breathe")
        val scale by breatheTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breatheScale",
        )
        scale
    } else if (!reduced && state == LiveCompanionState.CHEERING) {
        val cheerTransition = rememberInfiniteTransition(label = "companion-cheer-bounce")
        val scale by cheerTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cheerBounce",
        )
        scale
    } else {
        1f
    }

    val effectiveDescription = contentDescription ?: buildString {
        append("Compañero Mática ${state.labelEs}")
        if (!speechBubble.isNullOrBlank()) {
            append(". Mensaje: $speechBubble")
        }
    }

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                this.contentDescription = effectiveDescription
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .offset(y = bobbingOffset.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier.size(size * breathingScale),
            ) {
                val side = this.size.minDimension
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val radius = side / 2f * 0.94f

                // 1. Cuerpo esférico compacto
                drawCircle(
                    color = bodyColor,
                    radius = radius,
                    center = center,
                    style = Fill,
                )

                // Borde suave de definición
                drawCircle(
                    color = inkColor.copy(alpha = 0.12f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = side * 0.035f),
                )

                val eyeRadius = side * 0.085f
                val eyeY = center.y - side * 0.07f
                val leftEyeX = center.x - side * 0.19f
                val rightEyeX = center.x + side * 0.19f
                val strokeWidth = side * 0.045f

                // 2. Ojos según estado reactivo
                when (state) {
                    LiveCompanionState.CHEERING -> {
                        // Arcos felices sonrientes (^ ^)
                        drawMascotEyeArc(center = Offset(leftEyeX, eyeY), radius = eyeRadius * 1.15f, color = inkColor, strokeWidth = strokeWidth)
                        drawMascotEyeArc(center = Offset(rightEyeX, eyeY), radius = eyeRadius * 1.15f, color = inkColor, strokeWidth = strokeWidth)

                        // Mejillas sonrosadas de alegría
                        drawCircle(
                            color = MaticaPalette.Teal.copy(alpha = 0.28f),
                            radius = side * 0.09f,
                            center = Offset(center.x - side * 0.28f, center.y + side * 0.1f),
                        )
                        drawCircle(
                            color = MaticaPalette.Teal.copy(alpha = 0.28f),
                            radius = side * 0.09f,
                            center = Offset(center.x + side * 0.28f, center.y + side * 0.1f),
                        )
                    }

                    LiveCompanionState.THINKING -> {
                        // Ojos evaluando: pupila desplazada hacia la esquina superior derecha
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

                        drawCircle(
                            color = inkColor,
                            radius = eyeRadius * 1.12f,
                            center = Offset(rightEyeX, eyeY),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = eyeRadius * 0.4f,
                            center = Offset(rightEyeX + eyeRadius * 0.4f, eyeY - eyeRadius * 0.4f),
                        )

                        // Ceja inquisitiva sobre el ojo derecho
                        val browPath = Path().apply {
                            moveTo(rightEyeX - eyeRadius, eyeY - eyeRadius * 1.5f)
                            lineTo(rightEyeX + eyeRadius * 1.1f, eyeY - eyeRadius * 1.9f)
                        }
                        drawPath(browPath, color = inkColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    }

                    LiveCompanionState.ENCOURAGING -> {
                        // Ojos cálidos y amables con brillo generoso
                        drawCircle(
                            color = inkColor,
                            radius = eyeRadius * 1.05f,
                            center = Offset(leftEyeX, eyeY),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = eyeRadius * 0.42f,
                            center = Offset(leftEyeX - eyeRadius * 0.2f, eyeY - eyeRadius * 0.25f),
                        )

                        drawCircle(
                            color = inkColor,
                            radius = eyeRadius * 1.05f,
                            center = Offset(rightEyeX, eyeY),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = eyeRadius * 0.42f,
                            center = Offset(rightEyeX - eyeRadius * 0.2f, eyeY - eyeRadius * 0.25f),
                        )

                        // Sonrojo cálido de apoyo
                        drawCircle(
                            color = MaticaPalette.Teal.copy(alpha = 0.22f),
                            radius = side * 0.08f,
                            center = Offset(center.x - side * 0.27f, center.y + side * 0.1f),
                        )
                        drawCircle(
                            color = MaticaPalette.Teal.copy(alpha = 0.22f),
                            radius = side * 0.08f,
                            center = Offset(center.x + side * 0.27f, center.y + side * 0.1f),
                        )
                    }

                    LiveCompanionState.IDLE -> {
                        val openness = eyeOpenness.value
                        if (openness < 0.25f) {
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
                                topLeft = Offset(leftEyeX - eyeRadius, eyeY - eyeRadius * openness),
                                size = Size(eyeRadius * 2f, eyeRadius * 2f * openness),
                            )
                            drawCircle(
                                color = Color.White,
                                radius = eyeRadius * 0.35f * openness,
                                center = Offset(leftEyeX - eyeRadius * 0.25f, eyeY - eyeRadius * 0.25f * openness),
                            )

                            // Ojo derecho
                            drawOval(
                                color = inkColor,
                                topLeft = Offset(rightEyeX - eyeRadius, eyeY - eyeRadius * openness),
                                size = Size(eyeRadius * 2f, eyeRadius * 2f * openness),
                            )
                            drawCircle(
                                color = Color.White,
                                radius = eyeRadius * 0.35f * openness,
                                center = Offset(rightEyeX - eyeRadius * 0.25f, eyeY - eyeRadius * 0.25f * openness),
                            )
                        }
                    }
                }

                // 3. Boca adaptada a escala compacta
                val mouthY = center.y + side * 0.18f
                val mouthHalfWidth = when (state) {
                    LiveCompanionState.CHEERING -> side * 0.18f
                    LiveCompanionState.ENCOURAGING -> side * 0.15f
                    LiveCompanionState.THINKING -> side * 0.10f
                    LiveCompanionState.IDLE -> side * 0.14f
                }
                val mouthDepth = when (state) {
                    LiveCompanionState.CHEERING -> side * 0.14f
                    LiveCompanionState.ENCOURAGING -> side * 0.08f
                    LiveCompanionState.THINKING -> side * 0.04f
                    LiveCompanionState.IDLE -> side * 0.07f
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
            }
        }

        // Bocadillo de diálogo / pensamiento flotante con flecha apuntando a la mascota
        if (!speechBubble.isNullOrBlank()) {
            CompanionSpeechBubble(
                text = speechBubble,
                isThinking = (state == LiveCompanionState.THINKING),
            )
        }
    }
}

/**
 * Bocadillo de diálogo o pensamiento para el compañero.
 */
@Composable
private fun CompanionSpeechBubble(
    text: String,
    isThinking: Boolean,
    modifier: Modifier = Modifier,
) {
    val bubbleColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier.widthIn(max = 240.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Indicador / punta del bocadillo apuntando hacia la mascota
        Canvas(
            modifier = Modifier.size(width = 7.dp, height = 12.dp),
        ) {
            val pointerPath = Path().apply {
                if (isThinking) {
                    // Círculos de pensamiento
                    // Dibujados en el centro
                } else {
                    // Triángulo clásico de diálogo apuntando a la izquierda (hacia la mascota)
                    moveTo(size.width, 0f)
                    lineTo(0f, size.height / 2f)
                    lineTo(size.width, size.height)
                    close()
                }
            }
            if (!isThinking) {
                drawPath(pointerPath, color = bubbleColor, style = Fill)
                drawPath(
                    pointerPath,
                    color = borderColor,
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
                )
            } else {
                drawCircle(bubbleColor, radius = 2.dp.toPx(), center = Offset(size.width * 0.2f, size.height / 2f))
                drawCircle(borderColor, radius = 2.dp.toPx(), center = Offset(size.width * 0.2f, size.height / 2f), style = Stroke(1.dp.toPx()))
                drawCircle(bubbleColor, radius = 3.dp.toPx(), center = Offset(size.width * 0.7f, size.height / 2f))
                drawCircle(borderColor, radius = 3.dp.toPx(), center = Offset(size.width * 0.7f, size.height / 2f), style = Stroke(1.dp.toPx()))
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bubbleColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            modifier = Modifier.padding(start = 1.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMascotEyeArc(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
) {
    val path = Path().apply {
        moveTo(center.x - radius, center.y + radius * 0.3f)
        quadraticTo(center.x, center.y - radius * 0.85f, center.x + radius, center.y + radius * 0.3f)
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}
