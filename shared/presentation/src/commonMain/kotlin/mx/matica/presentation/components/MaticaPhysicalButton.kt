package mx.matica.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.matica.design.feedback.MaticaFeedback
import mx.matica.design.feedback.rememberMaticaFeedback
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.theme.LocalMaticaDarkTheme
import mx.matica.design.theme.MaticaColorPair
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing

/** Variantes táctiles 3D soportadas por [MaticaPhysicalButton]. */
enum class MaticaPhysicalButtonVariant {
    PRIMARY,
    ACCENT,
    NEUTRAL,
    DESTRUCTIVE,
}

/** Configuración de color para la superficie, base 3D y texto del botón. */
data class MaticaPhysicalButtonColors(
    val foreground: Color,
    val background: Color,
    val base: Color,
    val usage: String,
) {
    fun toColorPair(): MaticaColorPair = MaticaColorPair(foreground, background, usage)
}

/** Parámetros predeterminados del botón físico 3D. */
object MaticaPhysicalButtonDefaults {
    val Elevation: Dp = 4.dp
    val MinTouchTarget: Dp = MaticaLayout.touchTarget
    const val SPRING_DAMPING: Float = Spring.DampingRatioMediumBouncy
    const val SPRING_STIFFNESS: Float = Spring.StiffnessMediumLow
    val CornerRadius: Dp = MaticaShapes.medium

    /**
     * Calcula el desplazamiento en Y del cuerpo del botón según el estado de presión.
     */
    fun calculatePressedOffset(
        isPressed: Boolean,
        enabled: Boolean = true,
        elevation: Dp = Elevation,
    ): Dp = if (isPressed && enabled) elevation else 0.dp
}

/**
 * Paleta de colores para los botones físicos 3D con garantía de contraste WCAG 2.2 AA (>= 4.5:1).
 */
object MaticaPhysicalButtonPalette {

    fun colorsOf(variant: MaticaPhysicalButtonVariant, dark: Boolean): MaticaPhysicalButtonColors = when (variant) {
        MaticaPhysicalButtonVariant.PRIMARY ->
            if (dark) {
                MaticaPhysicalButtonColors(
                    foreground = MaticaPalette.Night,
                    background = MaticaPalette.BlueLight,
                    base = MaticaPalette.Blue,
                    usage = "Botón físico primario en oscuro",
                )
            } else {
                MaticaPhysicalButtonColors(
                    foreground = MaticaPalette.Paper,
                    background = MaticaPalette.Blue,
                    base = MaticaPalette.BlueDeep,
                    usage = "Botón físico primario en claro",
                )
            }
        MaticaPhysicalButtonVariant.ACCENT ->
            if (dark) {
                MaticaPhysicalButtonColors(
                    foreground = MaticaPalette.Night,
                    background = MaticaPalette.Lime,
                    base = Color(0xFF7EAD27),
                    usage = "Botón físico de acento en oscuro",
                )
            } else {
                MaticaPhysicalButtonColors(
                    foreground = MaticaPalette.Ink,
                    background = MaticaPalette.Lime,
                    base = Color(0xFF8BBF2B),
                    usage = "Botón físico de acento en claro",
                )
            }
        MaticaPhysicalButtonVariant.NEUTRAL ->
            if (dark) {
                MaticaPhysicalButtonColors(
                    foreground = MaticaPalette.MistText,
                    background = MaticaPalette.NightRaised,
                    base = MaticaPalette.Night,
                    usage = "Botón físico neutro en oscuro",
                )
            } else {
                MaticaPhysicalButtonColors(
                    foreground = MaticaPalette.VioletInk,
                    background = MaticaPalette.BlueMist,
                    base = MaticaPalette.BlueSoft,
                    usage = "Botón físico neutro en claro",
                )
            }
        MaticaPhysicalButtonVariant.DESTRUCTIVE ->
            if (dark) {
                MaticaPhysicalButtonColors(
                    foreground = MaticaPalette.Night,
                    background = MaticaPalette.DangerLight,
                    base = MaticaPalette.Danger,
                    usage = "Botón físico destructivo en oscuro",
                )
            } else {
                MaticaPhysicalButtonColors(
                    foreground = MaticaPalette.Paper,
                    background = MaticaPalette.Danger,
                    base = Color(0xFF7D0013),
                    usage = "Botón físico destructivo en claro",
                )
            }
    }

    /** Lista completa de pares evaluados por las pruebas de contraste WCAG AA. */
    fun measuredPairs(): List<MaticaColorPair> =
        listOf(false, true).flatMap { dark ->
            MaticaPhysicalButtonVariant.entries.map { colorsOf(it, dark).toColorPair() }
        }
}

/**
 * Botón físico 3D con sensación 'chunky' (estilo juguetes táctiles de precisión).
 *
 * Cuenta con una base inferior sombreada de 4 dp que simula elevación física.
 * Al presionarse, el cuerpo desciende en Y exactamente 4 dp, colapsando la base inferior
 * y disparando respuesta háptica y auditiva ([MaticaFeedback.keyTap]).
 * Al soltarse, regresa con física de resorte elástico ([Spring.DampingRatioMediumBouncy]).
 * Respeta [LocalMotionStyle.current.reduced] aplicando transición instantánea sin rebote.
 */
@Composable
fun MaticaPhysicalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MaticaPhysicalButtonVariant = MaticaPhysicalButtonVariant.PRIMARY,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    elevation: Dp = MaticaPhysicalButtonDefaults.Elevation,
    shape: CornerBasedShape = RoundedCornerShape(MaticaPhysicalButtonDefaults.CornerRadius),
    feedback: MaticaFeedback? = rememberMaticaFeedback(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaticaPhysicalButtonPalette.colorsOf(variant, LocalMaticaDarkTheme.current)
    val motion = LocalMotionStyle.current
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed && enabled) {
            feedback?.keyTap()
        }
    }

    val targetOffset = MaticaPhysicalButtonDefaults.calculatePressedOffset(isPressed, enabled, elevation)
    val pressedOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = if (motion.reduced) {
            snap()
        } else if (isPressed) {
            spring(stiffness = Spring.StiffnessMedium)
        } else {
            spring(
                dampingRatio = MaticaPhysicalButtonDefaults.SPRING_DAMPING,
                stiffness = MaticaPhysicalButtonDefaults.SPRING_STIFFNESS,
            )
        },
        label = "matica-physical-button-offset",
    )

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = MaticaPhysicalButtonDefaults.MinTouchTarget)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        // Base inferior extruida que simula la elevación 3D del botón
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = elevation)
                .background(
                    color = if (enabled) colors.base else colors.base.copy(alpha = 0.35f),
                    shape = shape,
                ),
        )

        // Cuerpo superior del botón que desciende físicamente en Y al ser presionado
        Box(
            modifier = Modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .padding(bottom = elevation)
                .offset(y = pressedOffset)
                .background(
                    color = if (enabled) colors.background else colors.background.copy(alpha = 0.45f),
                    shape = shape,
                )
                .padding(horizontal = MaticaSpacing.lg, vertical = MaticaSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/**
 * Sobrecarga conveniente de [MaticaPhysicalButton] con rótulo de texto y glifos opcionales.
 */
@Composable
fun MaticaPhysicalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MaticaPhysicalButtonVariant = MaticaPhysicalButtonVariant.PRIMARY,
    enabled: Boolean = true,
    leadingGlyph: MaticaIcon? = null,
    trailingGlyph: MaticaIcon? = null,
    fullWidth: Boolean = false,
    elevation: Dp = MaticaPhysicalButtonDefaults.Elevation,
    shape: CornerBasedShape = RoundedCornerShape(MaticaPhysicalButtonDefaults.CornerRadius),
    feedback: MaticaFeedback? = rememberMaticaFeedback(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = MaticaPhysicalButtonPalette.colorsOf(variant, LocalMaticaDarkTheme.current)
    val contentColor = if (enabled) colors.foreground else colors.foreground.copy(alpha = 0.45f)

    MaticaPhysicalButton(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        fullWidth = fullWidth,
        elevation = elevation,
        shape = shape,
        feedback = feedback,
        interactionSource = interactionSource,
    ) {
        if (leadingGlyph != null) {
            Box(Modifier.size(20.dp)) {
                MaticaGlyph(icon = leadingGlyph, tint = contentColor)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
        if (trailingGlyph != null) {
            Box(Modifier.size(20.dp)) {
                MaticaGlyph(icon = trailingGlyph, tint = contentColor)
            }
        }
    }
}
