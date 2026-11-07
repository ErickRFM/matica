package mx.matica.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.theme.LocalMaticaDarkTheme
import mx.matica.design.theme.MaticaColorPair
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing

/**
 * Modelo de opción para andamiaje de pasos (Figma Screen 4, nodo 11:214).
 *
 * @property letter Letra distintiva ("A", "B", "C")
 * @property text Descripción de la operación (ej. "Dividir ambos lados entre 2")
 * @property resultingExpression Expresión matemática a la que conduce el paso si es válida
 * @property isCorrect Si la opción es pedagógicamente el primer paso correcto
 */
data class StepOption(
    val letter: String,
    val text: String,
    val resultingExpression: String? = null,
    val isCorrect: Boolean = false,
)

/**
 * Paleta de colores y contrastes del componente [StepChoiceCard], auditada contra WCAG AA (4.5:1).
 */
object StepChoicePalette {
    val WarningAmber = Color(0xFFB45309)
    val WarningAmberDark = Color(0xFFFBBF24)
    val WarningBgLight = Color(0xFFFFFBEB)

    fun backgroundColor(isSelected: Boolean, isCorrect: Boolean?, dark: Boolean): Color = when {
        isCorrect == true -> if (dark) MaticaPalette.NightSurface else MaticaPalette.Mint
        isCorrect == false -> if (dark) MaticaPalette.NightRaised else WarningBgLight
        isSelected -> if (dark) MaticaPalette.BlueDeep else MaticaPalette.BlueMist
        else -> if (dark) MaticaPalette.NightSurface else MaticaPalette.Paper
    }

    fun borderColor(isSelected: Boolean, isCorrect: Boolean?, dark: Boolean): Color = when {
        isCorrect == true -> if (dark) MaticaPalette.Teal else MaticaPalette.GreenInk
        isCorrect == false -> if (dark) WarningAmberDark else WarningAmber
        isSelected -> if (dark) MaticaPalette.BlueLight else MaticaPalette.Blue
        else -> if (dark) MaticaPalette.BlueDeep else MaticaPalette.BlueSoft
    }

    fun badgeBackgroundColor(isSelected: Boolean, isCorrect: Boolean?, dark: Boolean): Color = when {
        isCorrect == true -> if (dark) MaticaPalette.Teal else MaticaPalette.GreenInk
        isCorrect == false -> if (dark) WarningAmberDark else WarningAmber
        isSelected -> if (dark) MaticaPalette.BlueLight else MaticaPalette.Blue
        else -> if (dark) MaticaPalette.NightRaised else MaticaPalette.BlueMist
    }

    fun badgeTextColor(isSelected: Boolean, isCorrect: Boolean?, dark: Boolean): Color = when {
        isCorrect == true -> if (dark) MaticaPalette.Night else MaticaPalette.Paper
        isCorrect == false -> if (dark) MaticaPalette.Night else MaticaPalette.Paper
        isSelected -> if (dark) MaticaPalette.Night else MaticaPalette.Paper
        else -> if (dark) MaticaPalette.BlueLight else MaticaPalette.BlueDeep
    }

    fun textColor(enabled: Boolean, dark: Boolean): Color = when {
        !enabled -> if (dark) MaticaPalette.MistTextSoft.copy(alpha = 0.6f) else MaticaPalette.InkSoft.copy(alpha = 0.6f)
        dark -> MaticaPalette.MistText
        else -> MaticaPalette.Ink
    }

    fun measuredPairs(): List<MaticaColorPair> {
        val pairs = mutableListOf<MaticaColorPair>()
        for (dark in listOf(false, true)) {
            val themeStr = if (dark) "oscuro" else "claro"
            // No seleccionado
            pairs += MaticaColorPair(textColor(true, dark), backgroundColor(false, null, dark), "Texto no seleccionado en $themeStr")
            pairs += MaticaColorPair(badgeTextColor(false, null, dark), badgeBackgroundColor(false, null, dark), "Insignia no seleccionada en $themeStr")
            // Seleccionado
            pairs += MaticaColorPair(textColor(true, dark), backgroundColor(true, null, dark), "Texto seleccionado en $themeStr")
            pairs += MaticaColorPair(badgeTextColor(true, null, dark), badgeBackgroundColor(true, null, dark), "Insignia seleccionada en $themeStr")
            // Correcto
            pairs += MaticaColorPair(textColor(true, dark), backgroundColor(true, true, dark), "Texto correcto en $themeStr")
            pairs += MaticaColorPair(badgeTextColor(true, true, dark), badgeBackgroundColor(true, true, dark), "Insignia correcta en $themeStr")
            // Incorrecto
            pairs += MaticaColorPair(textColor(true, dark), backgroundColor(true, false, dark), "Texto incorrecto en $themeStr")
            pairs += MaticaColorPair(badgeTextColor(true, false, dark), badgeBackgroundColor(true, false, dark), "Insignia incorrecta en $themeStr")
        }
        return pairs
    }
}

/**
 * Tarjeta interactiva de opción múltiple para andamiaje de pasos (Figma Screen 4, nodo 11:214).
 *
 * Cuenta con insignia de letra, animaciones de borde y fondo, sacudida sutil en respuesta incorrecta
 * y semántica accesible de botón de opción (Role.RadioButton).
 */
@Composable
fun StepChoiceCard(
    letter: String,
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalMaticaDarkTheme.current
    val motion = LocalMotionStyle.current
    val interaction = remember { MutableInteractionSource() }

    val targetBgColor = StepChoicePalette.backgroundColor(isSelected, isCorrect, dark)
    val targetBorderColor = StepChoicePalette.borderColor(isSelected, isCorrect, dark)
    val badgeBgColor = StepChoicePalette.badgeBackgroundColor(isSelected, isCorrect, dark)
    val badgeTextColor = StepChoicePalette.badgeTextColor(isSelected, isCorrect, dark)
    val contentTextColor = StepChoicePalette.textColor(enabled, dark)

    val backgroundColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 200),
        label = "step-choice-bg",
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 200),
        label = "step-choice-border-color",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected || isCorrect != null) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 150),
        label = "step-choice-border-width",
    )

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(isCorrect) {
        if (isCorrect == false && !motion.reduced) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 350
                    0f at 0
                    (-6f) at 50
                    6f at 100
                    (-6f) at 150
                    6f at 200
                    (-3f) at 250
                    3f at 300
                    0f at 350
                },
            )
        } else {
            shakeOffset.snapTo(0f)
        }
    }

    val shape = RoundedCornerShape(MaticaShapes.medium)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset(x = shakeOffset.value.dp)
            .heightIn(min = MaticaLayout.touchTarget)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .background(color = backgroundColor, shape = shape)
            .clip(shape)
            .selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick,
            )
            .semantics {
                this.selected = isSelected
                this.role = Role.RadioButton
            }
            .padding(horizontal = MaticaSpacing.md, vertical = MaticaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color = badgeBgColor, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = badgeTextColor,
            )
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = contentTextColor,
            modifier = Modifier.weight(1f),
        )

        if (isCorrect == true) {
            MaticaGlyph(
                icon = MaticaIcon.CHECK,
                tint = if (dark) MaticaPalette.Teal else MaticaPalette.GreenInk,
                modifier = Modifier.size(20.dp),
            )
        } else if (isCorrect == false) {
            MaticaGlyph(
                icon = MaticaIcon.WARNING,
                tint = if (dark) StepChoicePalette.WarningAmberDark else StepChoicePalette.WarningAmber,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
