package mx.matica.presentation.math

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import mx.matica.design.feedback.rememberMaticaFeedback
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.motion.rememberReveal
import mx.matica.design.theme.LocalMaticaSemanticColors
import mx.matica.design.theme.MaticaColorPair
import mx.matica.design.tokens.MaticaPalette
import mx.matica.learning.mastery.MasteryLevel
import mx.matica.math.skills.SkillId
import mx.matica.presentation.components.MaticaGlyph
import mx.matica.presentation.components.MaticaIcon

/**
 * Altura estándar asignada a cada estación a lo largo del mapa mundo.
 */
val WorldStationHeight: Dp = 118.dp

/**
 * Modelo de datos para un cofre de hito / cofre misterioso en el mapa.
 */
data class ChestNode(
    val id: String,
    val milestoneIndex: Int,
    val rewardGems: Int = 20,
    val rewardXp: Int = 50,
    val unlocked: Boolean = false,
    val opened: Boolean = false,
    val title: String = "Cofre Misterioso",
    val description: String = "Recompensa del hito del mundo",
)

/**
 * Estaciones que componen el sendero sinuoso del mundo.
 */
sealed interface WorldStation {
    val index: Int
    val normalizedX: Float
    val unlocked: Boolean

    /** Estación de aprendizaje para una habilidad matemática. */
    data class SkillStation(
        override val index: Int,
        override val normalizedX: Float,
        val node: RouteNode,
    ) : WorldStation {
        override val unlocked: Boolean get() = node.unlocked
    }

    /** Estación de cofre de hito misterioso con recompensa. */
    data class ChestStation(
        override val index: Int,
        override val normalizedX: Float,
        val chest: ChestNode,
    ) : WorldStation {
        override val unlocked: Boolean get() = chest.unlocked
    }
}

/**
 * Calcula la coordenada horizontal normalizada [0f, 1f] para el índice de estación dado.
 * Genera una oscilación suave en S (Centro -> Derecha -> Centro -> Izquierda -> Centro).
 *
 * Función matemática pura para pruebas unitarias.
 */
fun sCurveNormalizedX(index: Int): Float {
    // Oscilación senoidal: pi/2 por paso alterna entre 0, 1, 0, -1
    val sine = sin(index.toDouble() * (PI / 2.0)).toFloat()
    val amplitude = 0.28f
    return (0.50f + amplitude * sine).coerceIn(0.15f, 0.85f)
}

/**
 * Construye la secuencia combinada de estaciones de habilidad y cofres misteriosos.
 * Cada [chestInterval] estaciones de habilidad, se introduce un cofre de hito.
 */
fun buildWorldStations(
    nodes: List<RouteNode>,
    chestInterval: Int = 4,
    customChests: List<ChestNode> = emptyList(),
): List<WorldStation> {
    if (nodes.isEmpty()) return emptyList()

    val stations = mutableListOf<WorldStation>()
    var stationCounter = 0
    var skillCounterInCycle = 0
    var milestoneCounter = 1

    nodes.forEach { node ->
        stations.add(
            WorldStation.SkillStation(
                index = stationCounter,
                normalizedX = sCurveNormalizedX(stationCounter),
                node = node,
            ),
        )
        stationCounter++
        skillCounterInCycle++

        if (chestInterval > 0 && skillCounterInCycle >= chestInterval) {
            val chestId = "chest_milestone_$milestoneCounter"
            val existing = customChests.firstOrNull { it.id == chestId || it.milestoneIndex == milestoneCounter }
            // Desbloqueado si el nodo anterior o actual está desbloqueado
            val isUnlocked = existing?.unlocked ?: node.unlocked
            val chest = existing ?: ChestNode(
                id = chestId,
                milestoneIndex = milestoneCounter,
                rewardGems = 15 + milestoneCounter * 5,
                rewardXp = 30 + milestoneCounter * 10,
                unlocked = isUnlocked,
                opened = false,
                title = "Cofre del Hito $milestoneCounter",
            )
            stations.add(
                WorldStation.ChestStation(
                    index = stationCounter,
                    normalizedX = sCurveNormalizedX(stationCounter),
                    chest = chest,
                ),
            )
            stationCounter++
            milestoneCounter++
            skillCounterInCycle = 0
        }
    }

    return stations
}

/**
 * Genera el trazado en curva spline continua que conecta suavemente todas las estaciones del mapa.
 */
fun worldSplinePath(
    stations: List<WorldStation>,
    widthPx: Float,
    stepHeightPx: Float,
    reveal: Float = 1f,
): Path? {
    if (stations.size < 2 || widthPx <= 0f || stepHeightPx <= 0f || reveal <= 0f) return null

    val fullPath = Path()
    stations.forEachIndexed { i, station ->
        val x = station.normalizedX * widthPx
        val y = (station.index + 0.5f) * stepHeightPx
        if (i == 0) {
            fullPath.moveTo(x, y)
        } else {
            val prevStation = stations[i - 1]
            val prevX = prevStation.normalizedX * widthPx
            val prevY = (prevStation.index + 0.5f) * stepHeightPx
            val dy = y - prevY

            // Spline cúbico Bézier continuo
            fullPath.cubicTo(
                prevX, prevY + dy * 0.5f,
                x, y - dy * 0.5f,
                x, y,
            )
        }
    }

    if (reveal >= 0.999f) return fullPath

    val measure = PathMeasure()
    measure.setPath(fullPath, false)
    val visible = Path()
    return if (measure.getSegment(0f, measure.length * reveal.coerceIn(0f, 1f), visible, true)) visible else null
}

/**
 * Mapa del mundo de aprendizaje con sendero sinuoso (S-curve) y cofres de hito misteriosos.
 *
 * Características:
 * - Sendero orgánico sinuoso en función senoidal / spline cúbico.
 * - Nodos de habilidad circulares ("piedras de salto") con indicación de nivel y halo activo.
 * - Nodos de Cofre Misterioso ([ChestNode]) cada N estaciones con recompensas de gemas y XP.
 * - Revelado progresivo o estático respetando [LocalMotionStyle.current.reduced].
 * - Accesibilidad completa con roles semánticos claros para cada estación.
 */
@Composable
fun RouteMapWorldView(
    nodes: List<RouteNode>,
    modifier: Modifier = Modifier,
    chestInterval: Int = 4,
    chests: List<ChestNode> = emptyList(),
    onNodeClick: (SkillId) -> Unit = {},
    onChestClick: (ChestNode) -> Unit = {},
) {
    val semantic = LocalMaticaSemanticColors.current
    val scheme = MaterialTheme.colorScheme
    val motion = LocalMotionStyle.current
    val stations = remember(nodes, chestInterval, chests) {
        buildWorldStations(nodes, chestInterval, chests)
    }

    val totalStations = stations.size
    val totalHeight = WorldStationHeight * totalStations.coerceAtLeast(1)

    // Clave de revelado
    val shapeKey = RouteRevealKey(
        nodeCount = stations.size,
        gridIdentity = stations.fold(0) { acc, st -> 31 * acc + st.index },
    )
    val reveal = rememberReveal(shapeKey)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .semantics {
                contentDescription = buildString {
                    append("Mapa del mundo de aprendizaje de Mática con ${stations.size} estaciones. ")
                    val suggested = nodes.firstOrNull { it.suggested }
                    if (suggested != null) {
                        append("Sugerencia actual: ${suggested.label}.")
                    }
                }
            },
    ) {
        // Lienzo para el sendero sinuoso
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepHeightPx = WorldStationHeight.toPx()
            val spline = worldSplinePath(
                stations = stations,
                widthPx = size.width,
                stepHeightPx = stepHeightPx,
                reveal = reveal.value,
            ) ?: return@Canvas

            // Trazo base del camino (lecho)
            drawPath(
                path = spline,
                color = scheme.surfaceVariant.copy(alpha = 0.7f),
                style = Stroke(
                    width = 12.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )

            // Trazo punteado activo
            drawPath(
                path = spline,
                color = scheme.outlineVariant,
                style = Stroke(
                    width = 3.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                ),
            )
        }

        // Estaciones colocadas en sus posiciones exactas a lo largo de la S-curve
        stations.forEach { station ->
            val topOffset = WorldStationHeight * station.index
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WorldStationHeight)
                    .offset(y = topOffset),
                contentAlignment = Alignment.Center,
            ) {
                // Cálculo del desplazamiento horizontal según el factor normalizado
                // Centro = 0.5f -> offset = 0 dp.
                // Left = 0.22f -> offset < 0 dp.
                // Right = 0.78f -> offset > 0 dp.
                val xFraction = station.normalizedX - 0.5f
                val horizontalBias = (xFraction * 2.2f).coerceIn(-0.85f, 0.85f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = BiasAlignment(horizontalBias, 0f),
                ) {
                    when (station) {
                        is WorldStation.SkillStation -> {
                            WorldSkillChip(
                                node = station.node,
                                pair = MaticaLevelPalette.pairOf(station.node.level, scheme, semantic),
                                onClick = { onNodeClick(station.node.skill) },
                            )
                        }

                        is WorldStation.ChestStation -> {
                            WorldChestChip(
                                chest = station.chest,
                                onClick = { onChestClick(station.chest) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Chip interactivo de estación de habilidad en el mapa del mundo.
 */
@Composable
private fun WorldSkillChip(
    node: RouteNode,
    pair: MaticaColorPair,
    onClick: () -> Unit,
) {
    val maticaFeedback = rememberMaticaFeedback()
    val motion = LocalMotionStyle.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Halo animado para nodo sugerido
    val haloTransition = rememberInfiniteTransition(label = "world-skill-halo")
    val haloScale by haloTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "worldHaloScale",
    )
    val haloAlpha by haloTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "worldHaloAlpha",
    )

    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && node.unlocked) 0.90f else 1f,
        animationSpec = MaticaMotion.popSpec(motion),
        label = "world-press-scale",
    )

    Column(
        modifier = Modifier
            .width(88.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = buildString {
                    append("${node.label}. Nivel: ${node.level.labelEs}.")
                    if (!node.unlocked) append(" Bloqueada.")
                    if (node.suggested) append(" Sugerida para continuar.")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Anillo pulsante si está sugerido
            if (node.suggested && !motion.reduced) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            scaleX = haloScale
                            scaleY = haloScale
                            alpha = haloAlpha
                        }
                        .clip(CircleShape)
                        .border(width = 2.5.dp, color = MaterialTheme.colorScheme.primary, shape = CircleShape),
                )
            }

            Surface(
                onClick = {
                    maticaFeedback.nodeClick()
                    onClick()
                },
                enabled = node.unlocked,
                shape = CircleShape,
                color = pair.background,
                contentColor = pair.foreground,
                interactionSource = interactionSource,
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                        alpha = if (node.unlocked) 1f else 0.55f
                    }
                    .border(
                        width = if (node.suggested) 2.5.dp else 1.5.dp,
                        color = if (node.suggested) MaterialTheme.colorScheme.primary else pair.foreground.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    MaticaGlyph(
                        icon = if (node.unlocked) levelGlyphIcon(node.level) else MaticaIcon.LOCK,
                        tint = pair.foreground,
                        modifier = Modifier.size(24.dp),
                        strokeWidthDp = if (node.suggested) 2.4f else 2f,
                    )
                }
            }
        }

        Text(
            text = node.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (node.suggested) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (node.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Chip interactivo para los cofres de hito misteriosos en el sendero del mundo.
 */
@Composable
private fun WorldChestChip(
    chest: ChestNode,
    onClick: () -> Unit,
) {
    val motion = LocalMotionStyle.current
    val isReadyToOpen = chest.unlocked && !chest.opened

    // Rebote sutil de llamada a la acción cuando está listo para abrir
    val chestBounce = if (isReadyToOpen && !motion.reduced) {
        val bounceTransition = rememberInfiniteTransition(label = "chest-bounce")
        val offset by bounceTransition.animateFloat(
            initialValue = 0f,
            targetValue = -4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "chestBounceOffset",
        )
        offset
    } else {
        0f
    }

    val chestBg = when {
        chest.opened -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        chest.unlocked -> MaticaPalette.VioletSoft
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val chestBorder = when {
        chest.opened -> MaterialTheme.colorScheme.outlineVariant
        chest.unlocked -> MaticaPalette.Violet
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    val iconTint = when {
        chest.opened -> MaterialTheme.colorScheme.onSurfaceVariant
        chest.unlocked -> MaticaPalette.VioletInk
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Column(
        modifier = Modifier
            .width(92.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = buildString {
                    append("${chest.title}. Recompensa: ${chest.rewardGems} gemas y ${chest.rewardXp} XP. ")
                    when {
                        chest.opened -> append("Ya abierto.")
                        chest.unlocked -> append("¡Listo para abrir!")
                        else -> append("Bloqueado: completa las estaciones anteriores.")
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            onClick = onClick,
            enabled = chest.unlocked,
            shape = RoundedCornerShape(16.dp),
            color = chestBg,
            border = androidx.compose.foundation.BorderStroke(2.dp, chestBorder),
            modifier = Modifier
                .size(52.dp)
                .offset(y = chestBounce.dp)
                .graphicsLayer {
                    alpha = if (chest.unlocked) 1f else 0.5f
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                MaticaGlyph(
                    icon = when {
                        chest.opened -> MaticaIcon.CHECK
                        chest.unlocked -> MaticaIcon.REWARD
                        else -> MaticaIcon.LOCK
                    },
                    tint = iconTint,
                    modifier = Modifier.size(26.dp),
                    strokeWidthDp = 2.2f,
                )
            }
        }

        Text(
            text = if (chest.opened) "Reclamado" else "+${chest.rewardGems} gemas",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (chest.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private fun levelGlyphIcon(level: MasteryLevel): MaticaIcon = when (level) {
    MasteryLevel.NOT_INTRODUCED -> MaticaIcon.LESSON
    MasteryLevel.DEVELOPING -> MaticaIcon.XP
    MasteryLevel.PROFICIENT -> MaticaIcon.CHECK
    MasteryLevel.FLUENT -> MaticaIcon.GEM
}
