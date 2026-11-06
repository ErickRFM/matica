package mx.matica.presentation.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.presentation.components.MaticaCountChip
import mx.matica.presentation.components.MaticaGlyph
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.components.MaticaIconButton
import mx.matica.presentation.components.MaticaChipPalette

/**
 * Cabecera + contenido desplazable compartida por las 18 pantallas. El título se marca como
 * `heading` y el botón de atrás tiene descripción propia: un lector de pantalla announce
 * «regresa a la ruta» y no «botón».
 */
@Composable
fun MaticaScreen(
    title: String,
    session: MaticaSession,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    showChips: Boolean = true,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MaticaTopBar(title = title, subtitle = subtitle, session = session, onBack = onBack, showChips = showChips, actions = actions)
        Column(
            modifier = Modifier
                .weight(1f)
                // El tope de ancho es lo que impide que en una tableta un párrafo cruce 900 dp y el
                // ojo pierda el renglón siguiente; en un teléfono no se nota porque no lo alcanza.
                .align(Alignment.CenterHorizontally)
                .widthIn(max = MaticaLayout.maxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaticaSpacing.lg, vertical = MaticaSpacing.md),
            // Un solo hueco entre apartados: `MaticaSection` aporta el agrupamiento por dentro.
            verticalArrangement = Arrangement.spacedBy(MaticaSpacing.lg),
        ) {
            content(PaddingValues())
        }
    }
}

@Composable
private fun MaticaTopBar(
    title: String,
    subtitle: String?,
    session: MaticaSession,
    onBack: (() -> Unit)?,
    showChips: Boolean,
    actions: @Composable () -> Unit,
) {
    /**
     * Dos filas y no una: las insignias miden su propio ancho, así que en un solo `Row` se
     * llevaban casi todo el 1080 px y el título quedaba reducido a una columna de tres palabras
     * por línea (así lo mostró la captura del emulador). Vacía, esta fila mide alto cero.
     */
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MaticaSpacing.md).padding(top = MaticaSpacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
        ) {
            if (onBack != null) {
                MaticaIconButton(icon = MaticaIcon.BACK, contentDescription = "Regresa", onClick = onBack)
            } else {
                MaticaGlyph(icon = MaticaIcon.HOME, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(MaticaLayout.glyphFrame))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    // Dos renglones y punto: «Lecciones de Equilibrio de la ecuación» a 320 dp de
                    // ancho era cinco renglones que empujaban toda la pantalla hacia abajo.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showChips) {
                MaticaCountChip(value = session.xp, suffix = "XP", glyph = MaticaIcon.XP, pair = MaticaChipPalette.xp)
                MaticaCountChip(value = session.streak, suffix = "días", glyph = MaticaIcon.FLAME, pair = MaticaChipPalette.streak)
            }
            actions()
        }
    }
}

/** Campo de texto del sistema: la misma forma en la respuesta, la pista y el enlace de aula. */
@Composable
fun MaticaField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            singleLine = keyboard != KeyboardType.Text,
            isError = error != null,
            enabled = enabled,
            shape = MaticaShapes.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        // El mensaje de validación se despliega en vez de aparecer de golpe: si no, la fila de abajo
        // salta de sitio sin que el estudiante vea de dónde salió. Con movimiento reducido la
        // duración es 0 ms y el cambio vuelve a ser instantáneo, no más brusco.
        val motion = LocalMotionStyle.current
        val ms = motion.ms(MaticaMotion.durations.short)
        AnimatedVisibility(
            visible = error != null,
            enter = expandVertically(tween(ms), expandFrom = Alignment.Top) + fadeIn(tween(ms)),
            exit = shrinkVertically(tween(ms)) + fadeOut(tween(ms)),
        ) {
            Text(text = error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Tarjeta de sección con el radio del sistema y la superficie de ecuaciones del tema. */
@Composable
fun MaticaCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(MaticaSpacing.lg),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaticaShapes.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm)) {
            content()
        }
    }
}

@Composable
fun MaticaCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}

/** Barra de progreso del diagnóstico: proporción real de ítems respondidos. */
@Composable
fun MaticaProgress(done: Int, total: Int, label: String, modifier: Modifier = Modifier) {
    val motion = LocalMotionStyle.current
    val fraction = if (total <= 0) 0f else done.toFloat() / total.toFloat()
    // Sin animación la barra saltaba de 6 % a 13 % en un cuadro y el estudiante no podía seguir con
    // la vista el avance que el rótulo anunciaba. Con movimiento reducido el cambio vuelve a ser
    // instantáneo, y el `progressBarRangeInfo` de abajo es lo que un lector de pantalla lee.
    val shown by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (motion.reduced) snap() else MaticaMotion.contentSpec(motion),
        label = "matica-progress",
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaticaSpacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = label },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 8.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = shown.coerceIn(0f, 1f),
                        range = 0f..1f,
                        steps = (total - 1).coerceAtLeast(0),
                    )
                },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 8.dp),
                shape = MaticaShapes.pill,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(modifier = Modifier.fillMaxWidth().fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(shown),
                        shape = MaticaShapes.pill,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        if (motion.reduced) {
            MaticaCaption("$done de $total")
        }
    }
}

