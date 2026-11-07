package mx.matica.design.manipulatives

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.tokens.MaticaPalette

/**
 * Estado que describe la carga física de una balanza de dos platillos.
 */
data class BalanceScaleState(
    val unknownsLeft: Int,
    val constantsLeft: Int,
    val unknownsRight: Int,
    val constantsRight: Int,
    val unknownValue: Float,
) {
    val massLeft: Float get() = unknownsLeft * unknownValue + constantsLeft * 1f
    val massRight: Float get() = unknownsRight * unknownValue + constantsRight * 1f
    val isBalanced: Boolean get() = kotlin.math.abs(massLeft - massRight) < 0.001f

    val tiltDegrees: Float
        get() {
            val diff = massRight - massLeft
            return (diff * 3.5f).coerceIn(-18f, 18f)
        }
}

/**
 * Balanza algebraica interactiva basada en el modelo Enactive/Iconic de Jerome Bruner.
 *
 * En lugar de presentar símbolos descontextualizados, muestra una balanza analógica de dos platillos
 * cuya física responde directamente al equilibrio de masas. Permite experimentar intuitivamente
 * el principio de igualdad: toda operación en un platillo debe replicarse en el otro para conservar
 * el balance horizontal.
 */
@Composable
fun BalanceScaleCanvas(
    state: BalanceScaleState,
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
    accentColor: Color = MaticaPalette.Blue,
    balancedColor: Color = MaticaPalette.Lime,
    inkColor: Color = MaticaPalette.Ink,
) {
    val motion = LocalMotionStyle.current
    val animatedTilt by animateFloatAsState(
        targetValue = if (motion.reduced) state.tiltDegrees else state.tiltDegrees,
        animationSpec = tween(durationMillis = if (motion.reduced) 0 else 450, easing = FastOutSlowInEasing),
        label = "scale-tilt",
    )

    val description = if (state.isBalanced) {
        "Balanza en perfecto equilibrio. Masa izquierda: ${state.massLeft}, masa derecha: ${state.massRight}."
    } else if (state.massLeft > state.massRight) {
        "Balanza inclinada a la izquierda. Lado izquierdo más pesado (${state.massLeft}) que el derecho (${state.massRight})."
    } else {
        "Balanza inclinada a la derecha. Lado derecho más pesado (${state.massRight}) que el izquierdo (${state.massLeft})."
    }

    Canvas(
        modifier = modifier.semantics { contentDescription = description },
    ) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val centerY = h * 0.42f
        val beamLength = w * 0.72f
        val halfBeam = beamLength / 2f

        // 1. Base y fulcro central (triángulo)
        drawFulcrum(centerX = centerX, centerY = centerY, bottomY = h * 0.88f, inkColor = inkColor)

        // 2. Travesaño giratorio
        val rad = animatedTilt * (PI.toFloat() / 180f)
        val leftTipX = centerX - halfBeam * cos(rad)
        val leftTipY = centerY - halfBeam * sin(rad)
        val rightTipX = centerX + halfBeam * cos(rad)
        val rightTipY = centerY + halfBeam * sin(rad)

        // Dibujar el haz / viga principal
        drawLine(
            color = inkColor,
            start = Offset(leftTipX, leftTipY),
            end = Offset(rightTipX, rightTipY),
            strokeWidth = 7f,
            cap = StrokeCap.Round,
        )

        // Pivote circular central
        drawCircle(
            color = if (state.isBalanced) balancedColor else accentColor,
            radius = 11f,
            center = Offset(centerX, centerY),
        )
        drawCircle(
            color = inkColor,
            radius = 11f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3f),
        )

        // 3. Indicador de nivel (flecha de aguja central)
        val needleLength = 32f
        val needleAngle = animatedTilt + 90f
        val needleRad = needleAngle * (PI.toFloat() / 180f)
        drawLine(
            color = if (state.isBalanced) balancedColor else MaticaPalette.Danger,
            start = Offset(centerX, centerY),
            end = Offset(centerX - needleLength * cos(needleRad), centerY - needleLength * sin(needleRad)),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )

        // 4. Platillo izquierdo y cuerdas
        drawPan(
            tipX = leftTipX,
            tipY = leftTipY,
            panWidth = w * 0.26f,
            panHeight = 14f,
            dropDistance = 64f,
            unknowns = state.unknownsLeft,
            constants = state.constantsLeft,
            inkColor = inkColor,
            accentColor = accentColor,
            balancedColor = balancedColor,
        )

        // 5. Platillo derecho y cuerdas
        drawPan(
            tipX = rightTipX,
            tipY = rightTipY,
            panWidth = w * 0.26f,
            panHeight = 14f,
            dropDistance = 64f,
            unknowns = state.unknownsRight,
            constants = state.constantsRight,
            inkColor = inkColor,
            accentColor = accentColor,
            balancedColor = balancedColor,
        )
    }
}

private fun DrawScope.drawFulcrum(centerX: Float, centerY: Float, bottomY: Float, inkColor: Color) {
    // Poste vertical
    drawLine(
        color = inkColor,
        start = Offset(centerX, centerY),
        end = Offset(centerX, bottomY),
        strokeWidth = 8f,
        cap = StrokeCap.Round,
    )

    // Base horizontal de apoyo
    val baseWidth = 120f
    drawLine(
        color = inkColor,
        start = Offset(centerX - baseWidth / 2f, bottomY),
        end = Offset(centerX + baseWidth / 2f, bottomY),
        strokeWidth = 10f,
        cap = StrokeCap.Round,
    )

    // Cuña triangular en el fulcro
    val trianglePath = Path().apply {
        moveTo(centerX, centerY)
        lineTo(centerX - 18f, centerY + 28f)
        lineTo(centerX + 18f, centerY + 28f)
        close()
    }
    drawPath(trianglePath, color = inkColor, style = Fill)
}

private fun DrawScope.drawPan(
    tipX: Float,
    tipY: Float,
    panWidth: Float,
    panHeight: Float,
    dropDistance: Float,
    unknowns: Int,
    constants: Int,
    inkColor: Color,
    accentColor: Color,
    balancedColor: Color,
) {
    val panCenterY = tipY + dropDistance
    val leftPanEdge = tipX - panWidth / 2f
    val rightPanEdge = tipX + panWidth / 2f

    // Cuerdas colgantes que unen la punta del travesaño a los extremos del platillo
    drawLine(
        color = inkColor.copy(alpha = 0.65f),
        start = Offset(tipX, tipY),
        end = Offset(leftPanEdge, panCenterY),
        strokeWidth = 2.5f,
    )
    drawLine(
        color = inkColor.copy(alpha = 0.65f),
        start = Offset(tipX, tipY),
        end = Offset(rightPanEdge, panCenterY),
        strokeWidth = 2.5f,
    )

    // Plato curvo
    val platePath = Path().apply {
        moveTo(leftPanEdge - 6f, panCenterY)
        quadraticTo(tipX, panCenterY + panHeight + 4f, rightPanEdge + 6f, panCenterY)
        lineTo(rightPanEdge + 4f, panCenterY + 4f)
        quadraticTo(tipX, panCenterY + panHeight + 8f, leftPanEdge - 4f, panCenterY + 4f)
        close()
    }
    drawPath(platePath, color = inkColor, style = Fill)

    // Dibujar elementos sobre el platillo:
    // Cajas incógnita 'x' (cubos redondeados violetas/azules)
    val boxSize = 22f
    val weightRadius = 9f
    var currentX = leftPanEdge + 12f
    val baseY = panCenterY - 2f

    // Cajas x
    repeat(unknowns) {
        if (currentX + boxSize <= rightPanEdge - 6f) {
            drawRoundRect(
                color = MaticaPalette.Violet,
                topLeft = Offset(currentX, baseY - boxSize),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(4f, 4f),
            )
            drawRoundRect(
                color = inkColor,
                topLeft = Offset(currentX, baseY - boxSize),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 2f),
            )
            // Trazo cruzado 'x'
            drawLine(
                color = Color.White,
                start = Offset(currentX + 5f, baseY - boxSize + 5f),
                end = Offset(currentX + boxSize - 5f, baseY - 5f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(currentX + boxSize - 5f, baseY - boxSize + 5f),
                end = Offset(currentX + 5f, baseY - 5f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round,
            )
            currentX += boxSize + 6f
        }
    }

    // Pesas unitarias '1' (cilindros / círculos dorados o teal)
    repeat(constants) {
        if (currentX + weightRadius * 2 <= rightPanEdge - 4f) {
            val center = Offset(currentX + weightRadius, baseY - weightRadius)
            drawCircle(
                color = MaticaPalette.Teal,
                radius = weightRadius,
                center = center,
            )
            drawCircle(
                color = inkColor,
                radius = weightRadius,
                center = center,
                style = Stroke(width = 2f),
            )
            // Punto central que indica unidad
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = center,
            )
            currentX += weightRadius * 2 + 5f
        }
    }
}
