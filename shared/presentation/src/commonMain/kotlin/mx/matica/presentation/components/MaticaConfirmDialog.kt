package mx.matica.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing

/** El mismo velo que usa la hoja de pistas: el aviso no tapa la pantalla más de lo que la tapa una hoja. */
private const val DIALOG_SCRIM_ALPHA = 0.45f

/**
 * Confirmación de una acción que no se puede deshacer.
 *
 * Se dibuja dentro de la propia pantalla y no en una ventana `Dialog`: el `Dialog` monta otra
 * ventana que el framework de pruebas instrumentadas no recorre, y el scrim, los insets y el teclado
 * ya los resuelve el host —una ventana aparte los volvería a improvisar.
 */
@Composable
fun MaticaConfirmDialog(
    title: String,
    detail: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = DIALOG_SCRIM_ALPHA))
                .clickable(onClickLabel = "Cerrar la confirmación", onClick = onDismissRequest),
        )
        Surface(
            shape = RoundedCornerShape(MaticaShapes.large),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(MaticaSpacing.lg)
                .widthIn(max = MaticaLayout.messageWidth)
                .semantics { liveRegion = LiveRegionMode.Assertive },
        ) {
            Column(
                modifier = Modifier.padding(MaticaSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm, Alignment.End),
                ) {
                    MaticaButton(
                        label = dismissLabel,
                        onClick = onDismissRequest,
                        variant = MaticaButtonVariant.OUTLINE,
                        modifier = Modifier.weight(1f),
                    )
                    MaticaButton(
                        label = confirmLabel,
                        onClick = onConfirm,
                        variant = MaticaButtonVariant.DESTRUCTIVE,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
