package mx.matica.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.theme.LocalMaticaDarkTheme
import mx.matica.design.theme.MaticaColorPair
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing

/** Los `Boton` del archivo de Figma más la variante destructiva, con los pares exactos que se prueban por contraste. */
enum class MaticaButtonVariant { PRIMARY, ACCENT, NEUTRAL, OUTLINE, TEXT, DESTRUCTIVE }

object MaticaButtonPalette {

    fun pairOf(variant: MaticaButtonVariant, dark: Boolean): MaticaColorPair = when (variant) {
        MaticaButtonVariant.PRIMARY ->
            if (dark) MaticaColorPair(MaticaPalette.Night, MaticaPalette.BlueLight, "CTA en oscuro")
            else MaticaColorPair(MaticaPalette.Paper, MaticaPalette.Blue, "CTA principal")
        MaticaButtonVariant.ACCENT ->
            MaticaColorPair(MaticaPalette.Ink, MaticaPalette.Lime, "CTA de continuar (lima)")
        MaticaButtonVariant.NEUTRAL ->
            if (dark) MaticaColorPair(MaticaPalette.MistText, MaticaPalette.NightRaised, "botón neutro en oscuro")
            else MaticaColorPair(MaticaPalette.VioletInk, MaticaPalette.BlueMist, "botón secundario")
        MaticaButtonVariant.OUTLINE ->
            if (dark) MaticaColorPair(MaticaPalette.BlueLight, MaticaPalette.NightSurface, "borde sobre superficie oscura")
            else MaticaColorPair(MaticaPalette.Blue, MaticaPalette.Paper, "borde sobre tarjeta")
        MaticaButtonVariant.TEXT ->
            if (dark) MaticaColorPair(MaticaPalette.BlueLight, MaticaPalette.Night, "enlace en oscuro")
            // Blue sobre Mist se queda en 4.36: bajo el umbral de texto normal. BlueDeep mantiene
            // el matiz azul y sube a 9.77.
            else MaticaColorPair(MaticaPalette.BlueDeep, MaticaPalette.Mist, "enlace sobre fondo de pantalla")
        // confirmación de una acción que no se deshace: el rojo es el aviso, no un adorno.
        MaticaButtonVariant.DESTRUCTIVE ->
            if (dark) MaticaColorPair(MaticaPalette.Night, MaticaPalette.DangerLight, "confirmar borrado en oscuro")
            else MaticaColorPair(MaticaPalette.Paper, MaticaPalette.Danger, "confirmar borrado")
    }

    /** Pares que las pruebas de accesibilidad recorren: todas las variantes por tema. */
    fun measuredPairs(): List<MaticaColorPair> =
        listOf(false, true).flatMap { dark -> MaticaButtonVariant.entries.map { pairOf(it, dark) } }
}

/**
 * Botón del sistema. No delega el color a Material: cada variante fija su par primer plano /
 * fondo para que la prueba de contraste mida exactamente lo que se pinta en pantalla.
 */
@Composable
fun MaticaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MaticaButtonVariant = MaticaButtonVariant.PRIMARY,
    enabled: Boolean = true,
    leadingGlyph: MaticaIcon? = null,
    fullWidth: Boolean = false,
) {
    val pair = MaticaButtonPalette.pairOf(variant, LocalMaticaDarkTheme.current)
    val motion = LocalMotionStyle.current
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduced) 0.97f else 1f,
        animationSpec = MaticaMotion.popSpec(motion),
        label = "matica-button-press",
    )
    val shape = RoundedCornerShape(MaticaShapes.large)
    val solidBackground = variant != MaticaButtonVariant.OUTLINE && variant != MaticaButtonVariant.TEXT

    val widthModifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier
    val borderModifier =
        if (variant == MaticaButtonVariant.OUTLINE) Modifier.border(2.dp, pair.foreground, shape) else Modifier

    Row(
        // El `modifier` del llamador va primero: es el que trae el `weight(1f)` de las filas de
        // opciones. Se estaba ignorando (la fila construía su propio `widthModifier` desde cero), así
        // que cuatro botones pesados se repartían el ancho por su contenido y el último, «Rápida»,
        // terminaba escrito una letra por renglón en el teléfono de 392 dp.
        modifier = modifier
            .then(widthModifier)
            // Con el relleno propio del rótulo el botón medía ~44 dp de alto: por debajo del objetivo
            // táctil de Material. El `heightIn` lo sube a 48 sin cambiar el grosor visual del botón,
            // porque el texto sigue centrado.
            .heightIn(min = MaticaLayout.touchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .background(color = if (solidBackground) pair.background else Color.Transparent, shape = shape)
            .then(borderModifier)
            .clip(shape)
            // El único feedback que había era la escala de 0.97, y ésa se apaga con movimiento
            // reducido: en un teléfono con «animaciones desactivadas» el botón no decía nada al
            // tocarlo. El ripple es el feedback táctil que Material no considera movimiento
            // decorativo, así que sigue ahí cuando el resto se queda quieto.
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(color = pair.foreground),
                onClick = onClick,
            )
            // El relleno pasa del rótulo al `Row`: dentro del Text sumaba `xl` (24 dp) por lado al
            // propio `spacedBy` de la fila, así que el glifo de «Abrir mi ruta» quedaba separado del
            // texto 8 dp pero pegado al borde del botón 24 dp, y un rótulo corto como «Abrir» medía
            // ~83 dp de ancho a costa de la columna de texto de su propia fila. Acá el aire es parejo
            // por los cuatro costados y la etiqueta no decide el ancho del botón.
            .padding(horizontal = MaticaSpacing.lg, vertical = MaticaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingGlyph != null) {
            Box(Modifier.size(20.dp)) {
                MaticaGlyph(icon = leadingGlyph, tint = pair.foreground)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = pair.foreground,
        )
    }
}

@Composable
fun MaticaIconButton(
    icon: MaticaIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val motion = LocalMotionStyle.current
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduced) 0.94f else 1f,
        animationSpec = MaticaMotion.popSpec(motion),
        label = "matica-icon-button-press",
    )
    Box(
        modifier = modifier
            // 44 dp era el hueco que el dedo encontraba a medias: Material fija 48 como objetivo
            // táctil mínimo y el botón de atrás es el más pulsado de la app.
            .size(MaticaLayout.touchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = tint),
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        MaticaGlyph(icon = icon, tint = tint)
    }
}
