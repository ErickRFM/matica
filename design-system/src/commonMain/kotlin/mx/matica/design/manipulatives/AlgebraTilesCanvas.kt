package mx.matica.design.manipulatives

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing

/**
 * Tipo de azulejo algebraico según el modelo CPA de Bruner.
 */
enum class TileKind(val isPositive: Boolean, val isVariable: Boolean, val symbol: String) {
    POS_VAR(isPositive = true, isVariable = true, symbol = "+x"),
    NEG_VAR(isPositive = false, isVariable = true, symbol = "-x"),
    POS_UNIT(isPositive = true, isVariable = false, symbol = "+1"),
    NEG_UNIT(isPositive = false, isVariable = false, symbol = "-1"),
}

/**
 * Instancia de un azulejo en el tablero.
 */
data class AlgebraTile(
    val id: String,
    val kind: TileKind,
)

/**
 * Tablero interactivo de Azulejos de Álgebra (Algebra Tiles).
 *
 * Facilita la comprensión del álgebra visualizando términos como objetos físicos:
 * - Verde claro / Teal: términos positivos (+1, +x)
 * - Rojo / Coral: términos negativos (-1, -x)
 *
 * La interacción táctil permite tocar un término positivo y uno negativo para eliminarlos
 * conjuntamente mediante el principio de "Par Cero" (+1 + (-1) = 0).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlgebraTilesBoard(
    tiles: List<AlgebraTile>,
    selectedTileId: String?,
    onTileClicked: (AlgebraTile) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val posTiles = tiles.filter { it.kind.isPositive }
    val negTiles = tiles.filter { !it.kind.isPositive }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MaticaShapes.large))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(MaticaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
    ) {
        // Zona positiva
        Column(verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs)) {
            Text(
                text = "Términos positivos (+)",
                style = MaterialTheme.typography.labelMedium,
                color = MaticaPalette.Teal,
                fontWeight = FontWeight.Bold,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
            ) {
                if (posTiles.isEmpty()) {
                    Text("0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                posTiles.forEach { tile ->
                    TileChip(
                        tile = tile,
                        isSelected = tile.id == selectedTileId,
                        onClick = { onTileClicked(tile) },
                    )
                }
            }
        }

        // Zona negativa
        Column(verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs)) {
            Text(
                text = "Términos negativos (-)",
                style = MaterialTheme.typography.labelMedium,
                color = MaticaPalette.Danger,
                fontWeight = FontWeight.Bold,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
            ) {
                if (negTiles.isEmpty()) {
                    Text("0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                negTiles.forEach { tile ->
                    TileChip(
                        tile = tile,
                        isSelected = tile.id == selectedTileId,
                        onClick = { onTileClicked(tile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TileChip(
    tile: AlgebraTile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isVar = tile.kind.isVariable
    val isPos = tile.kind.isPositive

    val bgColor = if (isPos) {
        if (isVar) MaticaPalette.Teal else MaticaPalette.Lime
    } else {
        if (isVar) MaticaPalette.Danger else MaticaPalette.DangerLight
    }
    val contentColor = if (isPos) {
        if (isVar) Color.White else MaticaPalette.GreenInk
    } else {
        if (isVar) Color.White else MaticaPalette.Ink
    }

    val width = if (isVar) 64.dp else 40.dp
    val height = 40.dp

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(MaticaShapes.small))
            .background(bgColor)
            .border(
                width = if (isSelected) 3.dp else 1.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(MaticaShapes.small),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Azulejo de álgebra ${tile.kind.symbol}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tile.kind.symbol,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (isVar) 16.sp else 14.sp,
        )
    }
}
