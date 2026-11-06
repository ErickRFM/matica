package mx.matica.presentation.math

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.theme.LocalMaticaSemanticColors
import mx.matica.design.theme.MaticaColorPair
import mx.matica.design.theme.MaticaSemanticColors
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.learning.mastery.MasteryLevel
import mx.matica.learning.skills.SkillGraph
import mx.matica.math.skills.SkillId
import mx.matica.presentation.components.MaticaGlyph
import mx.matica.presentation.components.MaticaIcon

/**
 * Columnas del mapa. Cuatro dejaba etiquetas recortadas («Operacione…», «Reglas de p…») en la
 * captura del emulador: en español los nombres de habilidad caben en dos líneas sólo con tres
 * columnas, y el nombre es lo que permite decidir, así que manda sobre la compacidad.
 */
const val ROUTE_COLUMNS = 3

/**
 * Altura de cada banda. Es un valor fijo porque el canvas y la fila tienen que calcular el mismo
 * centro. No baja de 92 dp: con 76 la etiqueta quedaba sin dos líneas disponibles y el nudo se
 * leía «Operaciones con …» en vez de «Operaciones con enteros» (medido en el emulador).
 */
private val RouteBandHeight = 92.dp

/** Un nudo de la ruta: dónde se dibuja y qué se puede hacer con él. */
data class RouteNode(
    val skill: SkillId,
    val label: String,
    val level: MasteryLevel,
    val unlocked: Boolean,
    val suggested: Boolean,
    val row: Int,
    val column: Int,
)

/**
 * Nodos en serpiente: la fila par avanza de izquierda a derecha y la impar al revés, para que el
 * camino no salte de un extremo al otro en cada renglón.
 *
 * La sugerencia es el primer nodo disponible del orden topológico, y sólo uno: dos sugerencias a la
 * vez le dirían al estudiante que tiene dos caminos obligatorios cuando el grafo sólo garantiza uno.
 *
 * `openable` descarta lo que el catálogo todavía no puede abrir. Sugerir una habilidad sin
 * ejercicios deja el botón principal apagado y sin explicación, que es exactamente lo que mostró
 * la primera caminata por el emulador.
 */
fun routeNodes(
    graph: SkillGraph,
    levels: (SkillId) -> MasteryLevel,
    mastered: Set<SkillId>,
    openable: (SkillId) -> Boolean = { true },
): List<RouteNode> {
    val ordered = graph.order()
    val isAvailable = { skill: SkillId -> graph.prerequisitesOf(skill).all { it in mastered } }
    val suggested = ordered.firstOrNull { isAvailable(it) && openable(it) && levels(it) != MasteryLevel.FLUENT }
    return ordered.mapIndexed { index, skill ->
        val row = index / ROUTE_COLUMNS
        val position = index % ROUTE_COLUMNS
        RouteNode(
            skill = skill,
            label = skill.labelEs,
            level = levels(skill),
            unlocked = isAvailable(skill),
            suggested = skill == suggested,
            row = row,
            column = if (row % 2 == 0) position else ROUTE_COLUMNS - 1 - position,
        )
    }
}

/**
 * Colores por dominio. Cada par ya está medido por las pruebas de contraste: los dos primeros los
 * expone el tema Material y los dos últimos los colores semánticos, así que la ruta no introduce
 * ninguna combinación nueva sin probar.
 */
object MaticaLevelPalette {

    fun pairOf(level: MasteryLevel, scheme: ColorScheme, semantic: MaticaSemanticColors): MaticaColorPair = when (level) {
        MasteryLevel.NOT_INTRODUCED ->
            MaticaColorPair(scheme.onSurfaceVariant, scheme.surfaceVariant, "habilidad sin introducir")
        MasteryLevel.DEVELOPING ->
            MaticaColorPair(semantic.onHighlightContainer, semantic.highlightContainer, "habilidad en desarrollo")
        MasteryLevel.PROFICIENT ->
            MaticaColorPair(semantic.onTealContainer, semantic.tealContainer, "habilidad competente")
        MasteryLevel.FLUENT ->
            MaticaColorPair(semantic.onSuccessContainer, semantic.successContainer, "habilidad fluida")
    }

    fun levels(): List<MasteryLevel> = MasteryLevel.entries.toList()
}

/**
 * Mapa de la ruta matemática. El trazo punteado se recorre solo y el nudo sugerido crece apenas al
 * aparecer; con movimiento reducido el mapa aparece completo y estático.
 */
@Composable
fun RouteMapView(
    nodes: List<RouteNode>,
    modifier: Modifier = Modifier,
    onNodeClick: (SkillId) -> Unit = {},
) {
    val motion = LocalMotionStyle.current
    val semantic = LocalMaticaSemanticColors.current
    val scheme = MaterialTheme.colorScheme
    val rows = if (nodes.isEmpty()) 1 else (nodes.maxOf { it.row }) + 1
    val reveal = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(nodes, motion.reduced) {
        if (motion.reduced) reveal.snapTo(1f) else reveal.animateTo(1f, MaticaMotion.contentSpec(motion))
    }
    val suggestion = nodes.firstOrNull { it.suggested }
    val measured = remember { PathMeasure() }
    // Una sola caja: la banda de cada fila mide exactamente `bandHeight`, así que el centro del
    // nudo dibujado en el canvas y el centro de la tarjeta coinciden. Dos layouts distintos
    // producirían una ruta que no pasa por las habilidades que dice conectar.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RouteBandHeight * rows)
            .semantics {
                contentDescription =
                    nodes.joinToString(". ") { "${it.label}: ${it.level.labelEs}" } +
                        ". " + (suggestion?.let { "Sugerencia para continuar: ${it.label}" } ?: "Sin sugerencia")
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.height / rows.toFloat()
            val columnWidth = size.width / ROUTE_COLUMNS.toFloat()
            val ordered = nodes.sortedWith(compareBy({ it.row }, { it.column }))
            if (ordered.size > 1) {
                val path = Path()
                ordered.forEachIndexed { index, node ->
                    val x = columnWidth * (node.column + 0.5f)
                    val y = step * (node.row + 0.5f)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                measured.setPath(path, false)
                val visible = Path()
                if (measured.getSegment(0f, measured.length * reveal.value, visible, true)) {
                    drawPath(
                        path = visible,
                        color = scheme.outlineVariant,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                        ),
                    )
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(rows) { rowIndex ->
                val rowNodes = nodes.filter { it.row == rowIndex }.sortedBy { it.column }
                Row(
                    modifier = Modifier.fillMaxWidth().height(RouteBandHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(ROUTE_COLUMNS) { slot ->
                        val node = rowNodes.getOrNull(slot)
                        Box(
                            modifier = Modifier.weight(1f).padding(MaticaSpacing.xs),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (node != null) {
                                RouteChip(
                                    node = node,
                                    pair = MaticaLevelPalette.pairOf(node.level, scheme, semantic),
                                    onClick = { onNodeClick(node.skill) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteChip(node: RouteNode, pair: MaticaColorPair, onClick: () -> Unit) {
    val motion = LocalMotionStyle.current
    val pulse by animateFloatAsState(
        targetValue = if (node.suggested && !motion.reduced) 1.04f else 1f,
        animationSpec = MaticaMotion.popSpec(motion),
        label = "matica-route-node",
    )
    Surface(
        onClick = onClick,
        enabled = node.unlocked,
        shape = RoundedCornerShape(MaticaSpacing.lg),
        color = pair.background,
        contentColor = pair.foreground,
        modifier = Modifier
            .widthIn(max = 150.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                alpha = if (node.unlocked) 1f else 0.55f
            }
            .semantics {
                role = Role.Button
                contentDescription = buildString {
                    append("${node.label}. ${node.level.labelEs}.")
                    if (!node.unlocked) append(" Bloqueada: faltan prerrequisitos.")
                    if (node.suggested) append(" Sugerida para continuar.")
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(MaticaSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs),
        ) {
            MaticaGlyph(
                icon = if (node.unlocked) levelGlyph(node.level) else MaticaIcon.LOCK,
                tint = pair.foreground,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = node.label,
                style = MaterialTheme.typography.labelSmall,
                color = pair.foreground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** El icono refuerza el nivel, nunca lo comunica solo: la etiqueta de texto siempre está presente. */
private fun levelGlyph(level: MasteryLevel): MaticaIcon = when (level) {
    MasteryLevel.NOT_INTRODUCED -> MaticaIcon.LESSON
    MasteryLevel.DEVELOPING -> MaticaIcon.XP
    MasteryLevel.PROFICIENT -> MaticaIcon.CHECK
    MasteryLevel.FLUENT -> MaticaIcon.GEM
}
