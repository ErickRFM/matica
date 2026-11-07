package mx.matica.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.theme.MaticaColorPair

/**
 * Colores de la llama viva de racha.
 */
object StreakFlameColors {
    /** Halo exterior activo: naranja fuego ardiente */
    val ActiveOuter = Color(0xFFFF5722)
    /** Núcleo activo: dorado ámbar luminoso */
    val ActiveCore = Color(0xFFFFD54F)
    /** Borde contenedor activo */
    val ActiveBorder = Color(0xFFFF9800)
    /** Fondo contenedor activo en tema claro */
    val ActiveContainerLight = Color(0xFFFFF3E0)
    /** Fondo contenedor activo en tema oscuro */
    val ActiveContainerDark = Color(0xFF2E1C0C)

    /** Halo exterior inactivo: gris apagado elegante */
    val InactiveOuter = Color(0xFF9E9E9E)
    /** Núcleo inactivo: gris claro sutil */
    val InactiveCore = Color(0xFFCCCCCC)
    /** Borde contenedor inactivo */
    val InactiveBorder = Color(0xFFBDBDBD)
}

/**
 * Calcula el vaivén del vértice de la llama en píxeles normalizados.
 * Función pura para pruebas unitarias.
 */
fun streakFlameTipSwayPx(reduced: Boolean, progress: Float, maxSway: Float = 2.5f): Float {
    if (reduced) return 0f
    // Oscilación sinusoidal de -maxSway a +maxSway
    return kotlin.math.sin(progress * 2f * kotlin.math.PI.toFloat()) * maxSway
}

/**
 * Calcula la escala de pulso enérgico para la insignia de racha activa.
 * Función pura para pruebas unitarias.
 */
fun streakPulseScale(reduced: Boolean, isActive: Boolean, progress: Float): Float {
    if (reduced || !isActive) return 1f
    // Pulso sutil de 1.0f a 1.06f
    return 1f + 0.06f * progress.coerceIn(0f, 1f)
}

/**
 * Texto semántico accesible para lectores de pantalla.
 */
fun streakBadgeAccessibilityDescription(streakDays: Int): String {
    return if (streakDays > 0) {
        val dias = if (streakDays == 1) "1 día" else "$streakDays días"
        "Racha activa: $dias consecutivos de práctica matemática"
    } else {
        "Sin racha activa: 0 días. ¡Practica hoy para encender tu llama!"
    }
}

/**
 * Insignia de racha viva con llama animada en Canvas.
 *
 * Muestra el estado del hábito del estudiante:
 * - Si [streakDays] > 0: llama encendida con halo naranja y núcleo dorado que pulsa con energía.
 * - Si [streakDays] == 0: llama en gris apagado elegante sin animación.
 * - Respeta estrictamente [LocalMotionStyle.current.reduced].
 * - Tipografía [titleMedium] o [labelLarge] con alto contraste garantizado.
 */
@Composable
fun StreakFlameBadge(
    streakDays: Int,
    modifier: Modifier = Modifier,
    flameSize: Dp = 22.dp,
    showLabel: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val motion = LocalMotionStyle.current
    val reduced = motion.reduced
    val isActive = streakDays > 0

    // Animación de oscilación y pulso de fuego
    val infiniteTransition = rememberInfiniteTransition(label = "streak-flame")
    val flameSwayProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flame-sway",
    )
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flame-pulse",
    )

    val tipSway = streakFlameTipSwayPx(reduced || !isActive, flameSwayProgress)
    val pulseScale = streakPulseScale(reduced, isActive, pulseProgress)

    val isDark = MaterialTheme.colorScheme.surface.let {
        // Determinación rápida si el fondo general es oscuro
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.5f
    }

    val containerBg = when {
        isActive && isDark -> StreakFlameColors.ActiveContainerDark
        isActive && !isDark -> StreakFlameColors.ActiveContainerLight
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }

    val borderColor = if (isActive) {
        StreakFlameColors.ActiveBorder.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    val textColor = if (isActive) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    }

    val accessibilityDesc = streakBadgeAccessibilityDescription(streakDays)

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = containerBg,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .semantics(mergeDescendants = true) {
                this.contentDescription = accessibilityDesc
                if (onClick != null) {
                    this.role = Role.Button
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            // Llama vectorial viva en Canvas
            Canvas(modifier = Modifier.size(flameSize)) {
                val w = size.width
                val h = size.height
                val pad = w * 0.1f

                val outerColor = if (isActive) StreakFlameColors.ActiveOuter else StreakFlameColors.InactiveOuter
                val coreColor = if (isActive) StreakFlameColors.ActiveCore else StreakFlameColors.InactiveCore

                // 1. Halo / Cuerpo exterior de fuego estilizado
                val outerFlame = Path().apply {
                    val tipX = w * 0.5f + tipSway
                    val tipY = pad * 0.6f
                    moveTo(tipX, tipY)
                    // Curva hacia la derecha y base
                    cubicTo(
                        w * 0.90f, h * 0.45f,
                        w * 0.80f, h - pad,
                        w * 0.50f, h - pad * 0.7f,
                    )
                    // Curva desde la base hacia la izquierda y punta
                    cubicTo(
                        w * 0.20f, h - pad,
                        w * 0.10f, h * 0.45f,
                        tipX, tipY,
                    )
                    close()
                }
                drawPath(outerFlame, color = outerColor, style = Fill)

                // 2. Núcleo interior dorado / ámbar
                val innerFlame = Path().apply {
                    val coreTipX = w * 0.5f + tipSway * 0.6f
                    val coreTipY = h * 0.40f
                    moveTo(coreTipX, coreTipY)
                    cubicTo(
                        w * 0.74f, h * 0.60f,
                        w * 0.66f, h - pad * 1.05f,
                        w * 0.50f, h - pad * 0.85f,
                    )
                    cubicTo(
                        w * 0.34f, h - pad * 1.05f,
                        w * 0.26f, h * 0.60f,
                        coreTipX, coreTipY,
                    )
                    close()
                }
                drawPath(innerFlame, color = coreColor, style = Fill)
            }

            // Contador de días con tipografía prominente de alto contraste
            if (showLabel) {
                Text(
                    text = "$streakDays",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                )
            }
        }
    }
}
