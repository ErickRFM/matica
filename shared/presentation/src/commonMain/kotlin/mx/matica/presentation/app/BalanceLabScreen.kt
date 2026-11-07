package mx.matica.presentation.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.matica.design.brand.MascotEmotion
import mx.matica.design.brand.MaticaMascot
import mx.matica.design.feedback.rememberMaticaFeedback
import mx.matica.design.manipulatives.AlgebraTile
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import mx.matica.design.manipulatives.AlgebraTilesBoard
import mx.matica.design.manipulatives.BalanceScaleCanvas
import mx.matica.design.manipulatives.BalanceScaleState
import mx.matica.design.manipulatives.TileKind
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.math.skills.SkillId
import mx.matica.presentation.components.MaticaButton
import mx.matica.presentation.components.MaticaButtonVariant
import mx.matica.presentation.components.MaticaGlyph
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.components.MaticaSection
import mx.matica.presentation.math.EquationView
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute

private enum class LabTab {
    SCALE,
    TILES,
}

@Composable
fun BalanceLabScreen(
    session: MaticaSession,
    navigator: MaticaNavigator,
) {
    val feedback = rememberMaticaFeedback()
    var currentTab by remember { mutableStateOf(LabTab.SCALE) }

    // Estado de la Balanza: inicia con 2x + 3 = 9 (solución x = 3)
    var unknownsLeft by remember { mutableStateOf(2) }
    var constantsLeft by remember { mutableStateOf(3) }
    var unknownsRight by remember { mutableStateOf(0) }
    var constantsRight by remember { mutableStateOf(9) }
    val unknownValue = 3f

    val scaleState = BalanceScaleState(
        unknownsLeft = unknownsLeft,
        constantsLeft = constantsLeft,
        unknownsRight = unknownsRight,
        constantsRight = constantsRight,
        unknownValue = unknownValue,
    )

    // Estado de los Azulejos de Álgebra: x - 3 + 3 = 7 + 3
    var tiles by remember {
        mutableStateOf(
            listOf(
                AlgebraTile("v1", TileKind.POS_VAR),
                AlgebraTile("n1", TileKind.NEG_UNIT),
                AlgebraTile("n2", TileKind.NEG_UNIT),
                AlgebraTile("n3", TileKind.NEG_UNIT),
                AlgebraTile("p1", TileKind.POS_UNIT),
                AlgebraTile("p2", TileKind.POS_UNIT),
                AlgebraTile("p3", TileKind.POS_UNIT),
            ),
        )
    }
    var selectedTileId by remember { mutableStateOf<String?>(null) }
    var tilesResolvedMessage by remember { mutableStateOf<String?>(null) }

    val mascotEmotion = when {
        currentTab == LabTab.SCALE && scaleState.isBalanced && unknownsLeft == 1 && constantsLeft == 0 -> MascotEmotion.CELEBRATING
        currentTab == LabTab.SCALE && scaleState.isBalanced -> MascotEmotion.HAPPY
        currentTab == LabTab.SCALE -> MascotEmotion.THINKING
        currentTab == LabTab.TILES && tiles.none { !it.kind.isPositive } -> MascotEmotion.CELEBRATING
        else -> MascotEmotion.IDLE
    }

    MaticaScreen(
        title = "Laboratorio Táctil",
        subtitle = "Aprende el equilibrio manipulando objetos físicos",
        session = session,
        onBack = { navigator.goBack() },
    ) {
        // Selector de Pestaña (Balanza vs Azulejos)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MaticaShapes.medium))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val scaleSelected = currentTab == LabTab.SCALE
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(MaticaShapes.small))
                    .background(if (scaleSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { currentTab = LabTab.SCALE }
                    .padding(vertical = MaticaSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Balanza de Platillos",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (scaleSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (scaleSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val tilesSelected = currentTab == LabTab.TILES
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(MaticaShapes.small))
                    .background(if (tilesSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { currentTab = LabTab.TILES }
                    .padding(vertical = MaticaSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Azulejos (Par Cero)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (tilesSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (tilesSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Contexto Mascot Card
        MaticaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
            ) {
                MaticaMascot(
                    modifier = Modifier.size(64.dp),
                    emotion = mascotEmotion,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentTab == LabTab.SCALE) "Principio de la Balanza" else "Principio del Par Cero",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (currentTab == LabTab.SCALE) {
                            if (scaleState.isBalanced) {
                                if (unknownsLeft == 1 && constantsLeft == 0) {
                                    "¡Incógnita aislada con éxito! Cada caja 'x' pesa exactamente $constantsRight unidades."
                                } else {
                                    "¡La balanza está perfectamente nivelada! Todo cambio debe aplicarse a ambos lados."
                                }
                            } else {
                                "¡La balanza se ha inclinado! Para conservar la igualdad, la misma operación debe hacerse en los dos platillos."
                            }
                        } else {
                            if (tiles.none { !it.kind.isPositive }) {
                                "¡Todos los opuestos se neutralizaron! La variable x ha quedado completamente despejada."
                            } else {
                                "Un término positivo y un término negativo opuestos se neutralizan entre sí (+1 y -1 forman cero)."
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        when (currentTab) {
            LabTab.SCALE -> {
                // Lienzo de la Balanza
                MaticaCard {
                    BalanceScaleCanvas(state = scaleState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        Text(
                            text = "Platillo izq: ${scaleState.massLeft} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (scaleState.isBalanced) "EQUILIBRADA" else "INCLINADA",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (scaleState.isBalanced) MaticaPalette.GreenInk else MaticaPalette.Danger,
                        )
                        Text(
                            text = "Platillo der: ${scaleState.massRight} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Acciones sobre la Balanza
                MaticaSection("Operaciones en los platillos") {
                    Text(
                        text = "Ecuación representada: ${unknownsLeft}x + $constantsLeft = $constantsRight",
                        style = MaterialTheme.typography.titleSmall,
                    )

                    // Operaciones balanceadas (correctas)
                    MaticaButton(
                        label = "Restar 1 en AMBOS platillos (operación balanceada)",
                        onClick = {
                            if (constantsLeft > 0 && constantsRight > 0) {
                                constantsLeft -= 1
                                constantsRight -= 1
                                feedback.correct()
                            }
                        },
                        enabled = constantsLeft > 0 && constantsRight > 0,
                        fullWidth = true,
                    )

                    if (unknownsLeft > 1 && constantsLeft == 0 && constantsRight % unknownsLeft == 0) {
                        MaticaButton(
                            label = "Dividir ambos platillos entre $unknownsLeft (aislar x)",
                            onClick = {
                                constantsRight /= unknownsLeft
                                unknownsLeft = 1
                                feedback.correct()
                                session.awardMetacognitiveEvidence(SkillId.ISOLATING_VARIABLE)
                            },
                            fullWidth = true,
                        )
                    }

                    // Operaciones no balanceadas (para experimentar el error)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                    ) {
                        MaticaButton(
                            label = "Restar 1 solo izq",
                            onClick = {
                                if (constantsLeft > 0) {
                                    constantsLeft -= 1
                                    feedback.incorrect()
                                }
                            },
                            enabled = constantsLeft > 0,
                            variant = MaticaButtonVariant.OUTLINE,
                            modifier = Modifier.weight(1f),
                        )
                        MaticaButton(
                            label = "Restar 1 solo der",
                            onClick = {
                                if (constantsRight > 0) {
                                    constantsRight -= 1
                                    feedback.incorrect()
                                }
                            },
                            enabled = constantsRight > 0,
                            variant = MaticaButtonVariant.OUTLINE,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    MaticaButton(
                        label = "Reiniciar balanza a 2x + 3 = 9",
                        onClick = {
                            unknownsLeft = 2
                            constantsLeft = 3
                            unknownsRight = 0
                            constantsRight = 9
                        },
                        variant = MaticaButtonVariant.TEXT,
                        fullWidth = true,
                    )
                }
            }

            LabTab.TILES -> {
                MaticaCard {
                    Text(
                        text = "Expresión actual:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "x - 3 + 3  (los opuestos -3 y +3 deben neutralizarse)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                AlgebraTilesBoard(
                    tiles = tiles,
                    selectedTileId = selectedTileId,
                    onTileClicked = { clickedTile ->
                        val selected = selectedTileId
                        if (selected == null) {
                            selectedTileId = clickedTile.id
                        } else if (selected == clickedTile.id) {
                            selectedTileId = null
                        } else {
                            val first = tiles.firstOrNull { it.id == selected }
                            if (first != null && first.kind.isPositive != clickedTile.kind.isPositive && !first.kind.isVariable && !clickedTile.kind.isVariable) {
                                // Par cero encontrado: aniquilación mutua
                                tiles = tiles.filter { it.id != selected && it.id != clickedTile.id }
                                selectedTileId = null
                                feedback.correct()
                            } else {
                                selectedTileId = clickedTile.id
                            }
                        }
                    },
                )

                val hasZeroPairs = tiles.any { it.kind == TileKind.POS_UNIT } && tiles.any { it.kind == TileKind.NEG_UNIT }
                if (hasZeroPairs) {
                    MaticaButton(
                        label = "Aniquilar todos los pares cero (+1 y -1)",
                        onClick = {
                            tiles = tiles.filter { it.kind.isVariable }
                            feedback.correct()
                            session.awardMetacognitiveEvidence(SkillId.SIGN_HANDLING)
                        },
                        fullWidth = true,
                    )
                } else {
                    MaticaCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                        ) {
                            MaticaGlyph(icon = MaticaIcon.CHECK, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "¡Pares cero eliminados! Queda únicamente la variable 'x'.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    MaticaButton(
                        label = "Reiniciar azulejos",
                        onClick = {
                            tiles = listOf(
                                AlgebraTile("v1", TileKind.POS_VAR),
                                AlgebraTile("n1", TileKind.NEG_UNIT),
                                AlgebraTile("n2", TileKind.NEG_UNIT),
                                AlgebraTile("n3", TileKind.NEG_UNIT),
                                AlgebraTile("p1", TileKind.POS_UNIT),
                                AlgebraTile("p2", TileKind.POS_UNIT),
                                AlgebraTile("p3", TileKind.POS_UNIT),
                            )
                        },
                        variant = MaticaButtonVariant.TEXT,
                        fullWidth = true,
                    )
                }
            }
        }
    }
}
