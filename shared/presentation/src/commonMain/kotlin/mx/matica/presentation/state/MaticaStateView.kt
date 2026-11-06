package mx.matica.presentation.state

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.theme.LocalMaticaSemanticColors
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
    val shift = if (motion.reduced) 0 else 24
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(MaticaMotion.fadeSpec(motion)) +
                slideInVertically(MaticaMotion.contentSpec(motion)) { it / 3 + shift } togetherWith
                fadeOut(MaticaMotion.fadeSpec(motion)) +
                slideOutVertically(MaticaMotion.contentSpec(motion)) { it / 3 }
        },
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        label = "matica-state",
    ) { current ->
        when (current) {
            is MaticaViewState.Loading -> LoadingBody()
            is MaticaViewState.Empty -> MessageBody(current.title, current.description)
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
                verticalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
            ) { content(current.data) }
        }
    }
}

private const val OFFLINE_NOTE = "Puedes seguir practicando: el motor local funciona sin conexión."

@Composable
private fun LoadingBody() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MaticaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaticaSpacing.lg),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
        )
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
private fun SkeletonLine(fraction: Float) {
    val semantic = LocalMaticaSemanticColors.current
    Surface(
        color = semantic.equationSurface,
        shape = MaticaShapes.pill,
        modifier = Modifier.fillMaxWidth(fraction).height(14.dp),
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
    Column(
        modifier = Modifier.fillMaxSize().widthIn(max = 420.dp).padding(MaticaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaticaSpacing.md, Alignment.CenterVertically),
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
    Column(
        modifier = modifier.fillMaxWidth().padding(MaticaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
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
