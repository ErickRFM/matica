package mx.matica.presentation.state

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.motion.MotionStyle
import mx.matica.design.theme.LocalMaticaSemanticColors
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing

/**
 * Renderiza cualquier [MaticaViewState] con la misma jerarquía visual en toda la app.
 * `content` sólo se invoca en [MaticaViewState.Success]: una pantalla no puede mostrar datos
 * mientras el estado dice que no los hay.
 */
@Composable
fun <T> MaticaStateView(
    state: MaticaViewState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    val motion = LocalMotionStyle.current
    // El desplazamiento inicial lo decide `slideSpec`: con movimiento reducido es un `snap` y la
    // hoja aparece ya en su sitio, así que aquí no queda ninguna compensación que ajustar.
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(MaticaMotion.fadeSpec(motion)) +
                slideInVertically(MaticaMotion.slideSpec(motion)) { it / 3 } togetherWith
                fadeOut(MaticaMotion.fadeSpec(motion)) +
                slideOutVertically(MaticaMotion.slideSpec(motion)) { it / 3 }
        },
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        label = "matica-state",
    ) { current ->
        when (current) {
            is MaticaViewState.Loading -> LoadingBody()
            // Un `Empty` con `onRetry` recibía el callback y lo tiraba: la pantalla del player, que
            // ofrece «volver a la lección» cuando no hubo generador, se quedaba sin ninguna salida
            // visible además del botón de atrás.
            is MaticaViewState.Empty -> MessageBody(current.title, current.description, current.retryLabel, onRetry)
            is MaticaViewState.Error -> MessageBody(current.title, current.detail, current.retryLabel, onRetry)
            is MaticaViewState.Offline -> MessageBody(
                title = current.title,
                detail = current.detail + "\n" + OFFLINE_NOTE,
                actionLabel = onRetry?.let { MaticaViewState.RETRY_LABEL },
                onRetry = onRetry,
            )
            /**
             * `AnimatedContent` coloca en un `Box`, no en una columna: sin este `Column` dos
             * tarjetas de éxito se dibujaban una encima de otra (así apareció el mapa de ruta y
             * la lista «Para hoy» en la captura del emulador).
             */
            is MaticaViewState.Success -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rhythmOf(StateBody.Success).gap),
            ) { content(current.data) }
        }
    }
}

private const val OFFLINE_NOTE = "Puedes seguir practicando: el motor local funciona sin conexión."

/** Los cuatro cuerpos que el contenedor puede dibujar; cada uno pide su ritmo por este nombre. */
internal enum class StateBody { Loading, Message, Success, List }

/** Separación entre elementos y respiración interior de un cuerpo de estado. */
internal data class StateRhythm(val gap: Dp, val bodyPadding: Dp)

/**
 * Criterio de ritmo único: `md` entre elementos y `lg` de padding interior en los cuatro cuerpos.
 * Loading usaba `lg` con padding `xl` y MessageBody `md`, así que un estudiante veía tres tarjetas
 * distintas para el mismo contenedor. El parámetro se declara en el `when` para que añadir un cuerpo
 * nuevo obligue a escribir su ritmo, no para dar valores diferentes: si alguien vuelve a separar uno,
 * la prueba de ritmo uniforme se rompe.
 */
internal fun rhythmOf(body: StateBody): StateRhythm = when (body) {
    StateBody.Loading, StateBody.Message, StateBody.Success, StateBody.List ->
        StateRhythm(gap = MaticaSpacing.md, bodyPadding = MaticaSpacing.lg)
}

/**
 * Rango de alpha del esqueleto. Con movimiento reducido el rango es degenerado: la barra no cambia
 * nunca de aspecto y la pantalla conserva exactamente el mismo texto. Se anima alpha y no posición
 * porque desplazar barras dentro de una lista que ya se desplaza es justo lo que la bandera evita.
 */
internal fun skeletonAlphaRange(reduced: Boolean): ClosedRange<Float> =
    if (reduced) SKELETON_RESTING_ALPHA..SKELETON_RESTING_ALPHA else SKELETON_DIM_ALPHA..SKELETON_RESTING_ALPHA

/**
 * El spinner de Material gira en bucle infinito, así que con movimiento reducido se pinta un anillo
 * quieto. El cambio es sólo de dibujo: las dos variantes conservan el texto accesible «Cargando» y
 * la semántica de progreso indeterminado que comprueba la instrumentación.
 */
internal fun loadingIndicatorSpinsInfinitely(reduced: Boolean): Boolean = !reduced

/** Medida de dibujo del esqueleto y del indicador: no son espaciado, no son token. */
private val SkeletonBarHeight = 14.dp
private val IndicatorSize = 36.dp
private val IndicatorStrokeWidth = 4.dp
private const val SKELETON_DIM_ALPHA = 0.45f
private const val SKELETON_RESTING_ALPHA = 1f

@Composable
private fun LoadingBody() {
    val motion = LocalMotionStyle.current
    val rhythm = rhythmOf(StateBody.Loading)
    Column(
        modifier = Modifier.fillMaxWidth().padding(rhythm.bodyPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rhythm.gap),
    ) {
        LoadingIndicator(motion)
        Text(
            text = "Cargando",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(0.92f, 0.74f, 0.58f).forEach { fraction ->
            SkeletonLine(fraction)
        }
    }
}

@Composable
private fun LoadingIndicator(motion: MotionStyle) {
    val color = MaterialTheme.colorScheme.primary
    if (loadingIndicatorSpinsInfinitely(motion.reduced)) {
        CircularProgressIndicator(
            modifier = Modifier.size(IndicatorSize),
            color = color,
            strokeWidth = IndicatorStrokeWidth,
        )
        return
    }
    Canvas(modifier = Modifier.size(IndicatorSize).semantics {
        this[SemanticsProperties.ProgressBarRangeInfo] = ProgressBarRangeInfo.Indeterminate
    }) {
        val stroke = IndicatorStrokeWidth.toPx()
        drawCircle(
            color = color,
            radius = (size.minDimension - stroke) / 2f,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun SkeletonLine(fraction: Float) {
    val motion = LocalMotionStyle.current
    val semantic = LocalMaticaSemanticColors.current
    val range = skeletonAlphaRange(motion.reduced)
    val alpha = if (motion.reduced) {
        // Sin movimiento no se monta siquiera la transición infinita: la barra queda en reposo.
        range.endInclusive
    } else {
        val shimmer = rememberInfiniteTransition(label = "matica-skeleton")
        shimmer.animateFloat(
            initialValue = range.start,
            targetValue = range.endInclusive,
            animationSpec = infiniteRepeatable(
                animation = tween(motion.ms(MaticaMotion.durations.xLong), easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "matica-skeleton-alpha",
        ).value
    }
    Surface(
        color = semantic.equationSurface.copy(alpha = alpha),
        shape = MaticaShapes.pill,
        modifier = Modifier.fillMaxWidth(fraction).height(SkeletonBarHeight),
    ) {
        Box(Modifier.fillMaxSize())
    }
}

@Composable
private fun MessageBody(
    title: String,
    detail: String,
    actionLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val rhythm = rhythmOf(StateBody.Message)
    Column(
        modifier = Modifier.fillMaxSize().widthIn(max = MaticaLayout.messageWidth).padding(rhythm.bodyPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rhythm.gap, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onRetry != null) {
            Button(onClick = onRetry, shape = MaticaShapes.pill) {
                Text(actionLabel)
            }
        }
    }
}

/** Esqueleto de carga reutilizable para listas: nunca se muestra una lista vacía como si fuera "sin datos". */
@Composable
fun MaticaLoadingList(modifier: Modifier = Modifier, lines: Int = 3) {
    val rhythm = rhythmOf(StateBody.List)
    Column(
        modifier = modifier.fillMaxWidth().padding(rhythm.bodyPadding),
        verticalArrangement = Arrangement.spacedBy(rhythm.gap),
    ) {
        repeat(lines) { SkeletonLine(1f) }
    }
}

/** Contenedor redondeado del estado de éxito, con el radio grande del sistema. */
@Composable
fun MaticaSuccessBadge(text: String, modifier: Modifier = Modifier) {
    val semantic = LocalMaticaSemanticColors.current
    Surface(
        color = semantic.successContainer,
        shape = RoundedCornerShape(MaticaShapes.extraLarge),
        modifier = modifier.padding(MaticaSpacing.sm),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = semantic.onSuccessContainer,
            modifier = Modifier.padding(horizontal = MaticaSpacing.lg, vertical = MaticaSpacing.sm),
        )
    }
}
