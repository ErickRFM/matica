package mx.matica.presentation.math
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ripple
import androidx.compose.ui.text.font.FontWeight


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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.motion.rememberReveal
import mx.matica.design.theme.LocalMaticaSemanticColors
import mx.matica.design.theme.MaticaColorPair
import mx.matica.design.theme.MaticaSemanticColors
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.design.feedback.rememberMaticaFeedback
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
private val RouteBandHeight = 110.dp

/** Un nudo de la ruta: dónde se dibuja y qué se puede hacer con él. */
data class RouteNode(
    val skill: SkillId,
    val label: String,
    val level: MasteryLevel,
    val unlocked: Boolean,
    val suggested: Boolean,
    val row: Int,
    val column: Int,
    val retention: Float = 1.0f,
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
    retention: (SkillId) -> Float = { 1.0f },
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
            retention = retention(skill),
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
        MasteryLevel.NOVICE ->
            MaticaColorPair(scheme.onSurfaceVariant, scheme.surfaceVariant, "habilidad novata")
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
 * Identidad de la forma del camino: cuántos nudos y en qué casilla de la rejilla está cada uno.
 * Es una clave barata y comparable, no la lista entera.
 */
data class RouteRevealKey(val nodeCount: Int, val gridIdentity: Int)

/**
 * Clave del revelado. Deliberadamente mira la forma del camino y no el nivel de dominio ni la
 * sugerencia: que un nudo madure recolorea la tarjeta, no debe volver a trazar la ruta desde cero.
 */
fun routeRevealKey(nodes: List<RouteNode>): RouteRevealKey = RouteRevealKey(
    nodeCount = nodes.size,
    gridIdentity = nodes.fold(0) { acc, node ->
        31 * acc + node.skill.ordinal + 7 * (node.row * ROUTE_COLUMNS + node.column)
    },
)

/**
 * Tramo visible del camino, ya en píxeles del canvas. Es pura: construye su propio `PathMeasure` y
 * su propio `Path` en cada llamada, así que se puede medir dentro de `draw` aunque Compose repita o
 * cancele el cuadro. Devuelve `null` cuando todavía no hay nada que trazar.
 */
internal fun routeVisiblePath(
    ordered: List<RouteNode>,
    rows: Int,
    sizePx: IntSize,
    reveal: Float,
): Path? {
    if (ordered.size < 2 || rows <= 0 || sizePx.width <= 0 || sizePx.height <= 0 || reveal <= 0f) return null
    val step = sizePx.height / rows.toFloat()
    val columnWidth = sizePx.width / ROUTE_COLUMNS.toFloat()
    val path = Path()
    ordered.forEachIndexed { index, node ->
        val x = columnWidth * (node.column + 0.5f)
        val y = step * (node.row + 0.5f)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val measure = PathMeasure()
    measure.setPath(path, false)
    val visible = Path()
    return if (measure.getSegment(0f, measure.length * reveal.coerceIn(0f, 1f), visible, true)) visible else null
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
    val semantic = LocalMaticaSemanticColors.current
    val scheme = MaterialTheme.colorScheme
    val rows = if (nodes.isEmpty()) 1 else (nodes.maxOf { it.row }) + 1
    // Clave estable derivada del contenido (cuántos nudos y en qué rejilla): la pantalla reconstruye
    // la lista en cada recomposición y usarla de clave volvía a trazar la ruta desde cero.
    val shape = routeRevealKey(nodes)
    val reveal = rememberReveal(shape)
    val ordered = remember(nodes) { nodes.sortedWith(compareBy({ it.row }, { it.column })) }
    val suggestion = nodes.firstOrNull { it.suggested }
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
            // Medir dentro de la fase de dibujo, pero con un `PathMeasure` nuevo en cada llamada:
            // sacarlo a un `remember(reveal.value)` recomponía el mapa entero —las 12 tarjetas de
            // habilidad— en cada cuadro del trazado y en cada cambio de sugerencia.
            val path = routeVisiblePath(
                ordered = ordered,
                rows = rows,
                sizePx = IntSize(size.width.toInt(), size.height.toInt()),
                reveal = reveal.value,
            ) ?: return@Canvas
            drawPath(
                path = path,
                color = scheme.outlineVariant,
                style = Stroke(
                    // 3 dp de trazo y 10/8 dp de raya: medida del dibujo del camino, no espaciado de layout.
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                ),
            )
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
    val maticaFeedback = rememberMaticaFeedback()
    val motion = LocalMotionStyle.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Halo animado de respiración / energía para el nodo sugerido (Figma 1:85)
    val haloTransition = rememberInfiniteTransition(label = "route-halo")
    val haloScale by haloTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.38f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "haloScale",
    )
    val haloAlpha by haloTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "haloAlpha",
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && node.unlocked) 0.90f else 1f,
        animationSpec = MaticaMotion.popSpec(motion),
        label = "route-press-scale",
    )

    val isDecayed = node.retention < 0.6f && node.level != MasteryLevel.NOT_INTRODUCED && node.unlocked

    // El chip completo es el botón, no sólo el medallón de 50 dp. Medido en el AVD con el
    // `mergeDescendants` del `Column` y el `onClick` del `Surface` por separado, el nodo que llevaba
    // el nombre no tenía acción y el que la tenía no tenía nombre: TalkBack anunciaba «botón, no
    // escrito` y `hasClickAction() and hasContentDescription(…)` no encontraba nada que pulsar,
    // mientras que tocar el rótulo —la mitad baja del chip, la que más se ve— no hacía nada.
    val descripcion = buildString {
        append("${node.label}. ${node.level.labelEs}.")
        if (!node.unlocked) append(" Bloqueada: faltan prerrequisitos.")
        if (node.suggested) append(" Sugerida para continuar.")
        if (isDecayed) append(" Memoria en decaimiento: repaso sugerido.")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = node.unlocked,
                onClickLabel = "Abrir $descripcion",
            ) {
                maticaFeedback.nodeClick()
                onClick()
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = descripcion
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Medallón circular interactivo (Figma: ellipse 73x73 px)
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Anillo pulsante de energía cuando es el nodo sugerido
            if (node.suggested && !motion.reduced) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .graphicsLayer {
                            scaleX = haloScale
                            scaleY = haloScale
                            alpha = haloAlpha
                        }
                        .clip(CircleShape)
                        .border(width = 2.5.dp, color = MaterialTheme.colorScheme.primary, shape = CircleShape),
                )
            }

            // Cuerpo del nodo circular tipo piedra de salto; lo interactivo es el chip completo.
            Surface(
                shape = CircleShape,
                color = if (isDecayed) pair.background.copy(alpha = 0.85f) else pair.background,
                contentColor = pair.foreground,
                modifier = Modifier
                    .size(50.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                        alpha = if (node.unlocked) 1f else 0.55f
                    }
                    .border(
                        width = if (node.suggested || isDecayed) 2.5.dp else 1.5.dp,
                        color = when {
                            isDecayed -> MaterialTheme.colorScheme.error
                            node.suggested -> MaterialTheme.colorScheme.primary
                            else -> pair.foreground.copy(alpha = 0.3f)
                        },
                        shape = CircleShape,
                    ),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    MaticaGlyph(
                        icon = when {
                            !node.unlocked -> MaticaIcon.LOCK
                            isDecayed -> MaticaIcon.REFRESH
                            else -> levelGlyph(node.level)
                        },
                        tint = if (isDecayed) MaterialTheme.colorScheme.error else pair.foreground,
                        modifier = Modifier.size(22.dp),
                        strokeWidthDp = if (node.suggested || isDecayed) 2.4f else 2f,
                    )
                }
            }
        }

        // Etiqueta legible de la habilidad debajo del círculo
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

/** El icono refuerza el nivel, nunca lo comunica solo: la etiqueta de texto siempre está presente. */
private fun levelGlyph(level: MasteryLevel): MaticaIcon = when (level) {
    MasteryLevel.NOT_INTRODUCED -> MaticaIcon.LESSON
    MasteryLevel.NOVICE -> MaticaIcon.LESSON
    MasteryLevel.DEVELOPING -> MaticaIcon.XP
    MasteryLevel.PROFICIENT -> MaticaIcon.CHECK
    MasteryLevel.FLUENT -> MaticaIcon.GEM
}
