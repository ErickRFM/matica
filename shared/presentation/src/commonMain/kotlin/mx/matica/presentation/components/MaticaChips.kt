package mx.matica.presentation.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.theme.MaticaColorPair
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing

/** Chip de recuento animado: el número sube solo, sin redibujar la fila entera. */
@Composable
fun MaticaCountChip(
    value: Int,
    suffix: String,
    glyph: MaticaIcon,
    pair: MaticaColorPair,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionStyle.current
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = if (motion.reduced) snap() else MaticaMotion.popSpec(motion),
        label = "matica-count",
    )
    val shape = MaticaShapes.pill
    Row(
        modifier = modifier
            .background(pair.background, shape)
            .padding(horizontal = MaticaSpacing.md, vertical = MaticaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp)) { MaticaGlyph(icon = glyph, tint = pair.foreground, strokeWidthDp = 2.2f) }
        Text(
            text = "$animated $suffix",
            style = MaterialTheme.typography.labelLarge,
            color = pair.foreground,
        )
    }
}

/** Pares de chips usados por las pantallas, expuestos aquí para que la prueba de contraste los mida. */
object MaticaChipPalette {
    val streak = MaticaColorPair(MaticaPalette.Ink, MaticaPalette.Lime, "chip de racha")
    val xp = MaticaColorPair(MaticaPalette.VioletInk, MaticaPalette.VioletSoft, "chip de experiencia")

    fun measuredPairs(): List<MaticaColorPair> = listOf(streak, xp)
}

/**
 * Título de apartado. Su sangría coincide con el texto que vive dentro de la tarjeta (16 del
 * contenedor + 16 del relleno de la tarjeta), así que el título no queda flotando sobre el borde.
 * No lleva relleno vertical: el ritmo lo ponen `MaticaSection` y el contenedor de la pantalla, y
 * mientras este `Column` tuvo 8 dp por lado, la cabecera quedaba más lejos de su propia tarjeta
 * (20 dp) que una tarjeta de la siguiente (12 dp): el agrupamiento salía invertido.
 */
@Composable
private fun MaticaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = MaticaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        action?.invoke()
    }
}

/**
 * Apartado: su título pegado a lo que titula, y el hueco entre apartados más amplio que el de
 * dentro. Sin esta agrupación la jerarquía visual la sostenía sólo el tipo de letra.
 */
@Composable
fun MaticaSection(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm)) {
        MaticaSectionHeader(title, action = action)
        content()
    }
}
