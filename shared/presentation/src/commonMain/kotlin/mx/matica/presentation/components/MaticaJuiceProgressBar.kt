package mx.matica.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.theme.LocalMaticaDarkTheme
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaSpacing

/** Parámetros y utilidades de cálculo para [MaticaJuiceProgressBar]. */
object MaticaJuiceProgressBarDefaults {
    val DefaultHeight: Dp = 16.dp
    val MinHeight: Dp = 8.dp
    const val SPRING_DAMPING: Float = Spring.DampingRatioMediumBouncy
    const val SPRING_STIFFNESS: Float = Spring.StiffnessMediumLow
    const val SHIMMER_DURATION_MS: Int = 2200

    /**
     * Calcula las posiciones relativas (0f..1f) de los hitos o muescas intermedias.
     */
    fun calculateMilestoneFractions(notches: Int): List<Float> {
        if (notches <= 0) return emptyList()
        return List(notches) { index ->
            (index + 1).toFloat() / (notches + 1).toFloat()
        }
    }

    /**
     * Ajusta el progreso al rango válido [0f, 1f].
     */
    fun clampProgress(progress: Float): Float = progress.coerceIn(0f, 1f)

    /**
     * Determina si el haz de luz debe animarse y dibujarse.
     */
    fun shouldDrawSheen(currentProgress: Float, isReducedMotion: Boolean): Boolean =
        !isReducedMotion && currentProgress > 0.02f
}

/**
 * Barra de progreso fluida con 'Juice':
 * - Avance animado con física de resorte elástico ([Spring.DampingRatioMediumBouncy]).
 * - Haz de luz (*shimmer sheen beam*) continuo y sutil que viaja de izquierda a derecha.
 * - Muescas de hitos intermedias (*milestone notches*) opcionales distribuidas uniformemente.
 * - Soporte estricto de accesibilidad: si [LocalMotionStyle.current.reduced] está activo,
 *   el avance es instantáneo ([snap]) y el brillo se apaga.
 */
@Composable
fun MaticaJuiceProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaticaPalette.Lime,
    trackColor: Color? = null,
    height: Dp = MaticaJuiceProgressBarDefaults.DefaultHeight,
    milestones: Int = 0,
    milestoneFractions: List<Float> = emptyList(),
    label: String? = null,
    contentDescription: String? = label,
) {
    val motion = LocalMotionStyle.current
    val isDark = LocalMaticaDarkTheme.current
    val clampedTarget = MaticaJuiceProgressBarDefaults.clampProgress(progress)

    // Animación de avance con resorte elástico o snap instantáneo en movimiento reducido
    val animatedProgress by animateFloatAsState(
        targetValue = clampedTarget,
        animationSpec = if (motion.reduced) {
            snap()
        } else {
            spring(
                dampingRatio = MaticaJuiceProgressBarDefaults.SPRING_DAMPING,
                stiffness = MaticaJuiceProgressBarDefaults.SPRING_STIFFNESS,
            )
        },
        label = "juice-progress-spring",
    )

    // Haz de luz continuo (shimmer sheen)
    val infiniteTransition = rememberInfiniteTransition(label = "juice-sheen-transition")
    val sheenPhase by if (motion.reduced) {
        remember { mutableStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = -0.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = MaticaJuiceProgressBarDefaults.SHIMMER_DURATION_MS,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "juice-sheen-phase",
        )
    }

    val resolvedTrackColor = trackColor ?: if (isDark) {
        MaticaPalette.NightRaised
    } else {
        MaticaPalette.BlueSoft.copy(alpha = 0.45f)
    }

    val effectiveNotches = if (milestoneFractions.isNotEmpty()) {
        milestoneFractions
    } else {
        MaticaJuiceProgressBarDefaults.calculateMilestoneFractions(milestones)
    }

    val barModifier = Modifier
        .fillMaxWidth()
        .height(height)
        .semantics {
            if (contentDescription != null) {
                this.contentDescription = contentDescription
            }
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = clampedTarget,
                range = 0f..1f,
                steps = if (milestones > 0) milestones else 0,
            )
        }

    if (label != null) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            JuiceProgressBarCanvas(
                modifier = barModifier,
                animatedProgress = animatedProgress,
                sheenPhase = sheenPhase,
                drawSheen = MaticaJuiceProgressBarDefaults.shouldDrawSheen(animatedProgress, motion.reduced),
                color = color,
                trackColor = resolvedTrackColor,
                notches = effectiveNotches,
                isDark = isDark,
            )
        }
    } else {
        JuiceProgressBarCanvas(
            modifier = modifier.then(barModifier),
            animatedProgress = animatedProgress,
            sheenPhase = sheenPhase,
            drawSheen = MaticaJuiceProgressBarDefaults.shouldDrawSheen(animatedProgress, motion.reduced),
            color = color,
            trackColor = resolvedTrackColor,
            notches = effectiveNotches,
            isDark = isDark,
        )
    }
}

/**
 * Sobrecarga de [MaticaJuiceProgressBar] para conteo discreto de pasos completados ([done] / [total]).
 */
@Composable
fun MaticaJuiceProgressBar(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    color: Color = MaticaPalette.Lime,
    trackColor: Color? = null,
    height: Dp = MaticaJuiceProgressBarDefaults.DefaultHeight,
    showNotchesForSteps: Boolean = false,
    label: String? = null,
) {
    val progress = if (total <= 0) 0f else (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val notches = if (showNotchesForSteps && total > 1) total - 1 else 0

    MaticaJuiceProgressBar(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        height = height,
        milestones = notches,
        label = label,
    )
}

@Composable
private fun JuiceProgressBarCanvas(
    modifier: Modifier,
    animatedProgress: Float,
    sheenPhase: Float,
    drawSheen: Boolean,
    color: Color,
    trackColor: Color,
    notches: List<Float>,
    isDark: Boolean,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val cornerRadius = CornerRadius(h / 2f, h / 2f)
        val trackPath = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, cornerRadius))
        }

        // Pista base redondeada
        drawRoundRect(
            color = trackColor,
            size = Size(w, h),
            cornerRadius = cornerRadius,
        )

        // Sutil bisel interior / sombra en el borde superior de la pista
        drawRoundRect(
            color = Color.Black.copy(alpha = if (isDark) 0.25f else 0.08f),
            size = Size(w, h * 0.35f),
            cornerRadius = cornerRadius,
        )

        // Barra activa y efectos visuales contenidos dentro de la cápsula
        clipPath(trackPath) {
            val activeWidth = (w * animatedProgress.coerceIn(0f, 1f)).coerceAtLeast(0f)

            if (activeWidth > 0f) {
                // Fondo de la barra activa
                drawRoundRect(
                    color = color,
                    size = Size(activeWidth, h),
                    cornerRadius = cornerRadius,
                )

                // Resalte superior (gloss 3D que otorga sensación gomosa/táctil)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(0f, 0f),
                    size = Size(activeWidth, h * 0.42f),
                    cornerRadius = cornerRadius,
                )

                // Haz de luz continuo (shimmer sheen beam)
                if (drawSheen) {
                    val beamWidth = (activeWidth * 0.35f).coerceIn(28.dp.toPx(), 96.dp.toPx())
                    val beamCenter = activeWidth * sheenPhase
                    val sheenBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.52f),
                            Color.White.copy(alpha = 0f),
                        ),
                        start = Offset(beamCenter - beamWidth / 2f, 0f),
                        end = Offset(beamCenter + beamWidth / 2f, h),
                    )
                    drawRect(
                        brush = sheenBrush,
                        topLeft = Offset(beamCenter - beamWidth / 2f, 0f),
                        size = Size(beamWidth, h),
                    )
                }
            }

            // Muescas de hitos (*milestone notches*)
            for (fraction in notches) {
                val notchX = w * fraction
                val isPassed = animatedProgress >= fraction
                val notchColor = if (isPassed) {
                    Color.White.copy(alpha = 0.6f)
                } else {
                    if (isDark) MaticaPalette.NightSurface.copy(alpha = 0.6f) else MaticaPalette.Ink.copy(alpha = 0.15f)
                }
                val notchWidth = 2.dp.toPx()
                val notchHeight = h * 0.5f
                val notchTop = (h - notchHeight) / 2f
                drawRoundRect(
                    color = notchColor,
                    topLeft = Offset(notchX - notchWidth / 2f, notchTop),
                    size = Size(notchWidth, notchHeight),
                    cornerRadius = CornerRadius(notchWidth / 2f),
                )
            }
        }
    }
}
