package mx.matica.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.theme.LocalMaticaDarkTheme
import mx.matica.design.theme.MaticaColorPair
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaPalette
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.presentation.math.equationAnnouncement
import mx.matica.presentation.math.prettyMath

/**
 * Paleta y tokens de contraste para el componente [StepFillInBlankCard],
 * verificados contra las pautas WCAG AA (mínimo 4.5:1 para texto).
 */
object StepFillInBlankPalette {
    val WarningAmber = Color(0xFFB45309)
    val WarningAmberDark = Color(0xFFFBBF24)
    val WarningBgLight = Color(0xFFFFFBEB)

    fun cardBackgroundColor(dark: Boolean): Color =
        if (dark) MaticaPalette.NightSurface else MaticaPalette.Paper

    fun cardBorderColor(isCorrect: Boolean?, dark: Boolean): Color = when (isCorrect) {
        true -> if (dark) MaticaPalette.Teal else MaticaPalette.GreenInk
        false -> if (dark) WarningAmberDark else WarningAmber
        null -> if (dark) MaticaPalette.BlueDeep else MaticaPalette.BlueSoft
    }

    fun slotBackgroundColor(hasToken: Boolean, isCorrect: Boolean?, dark: Boolean): Color = when (isCorrect) {
        true -> if (dark) MaticaPalette.NightRaised else MaticaPalette.Mint
        false -> if (dark) MaticaPalette.NightRaised else WarningBgLight
        null -> when {
            hasToken -> if (dark) MaticaPalette.BlueDeep else MaticaPalette.BlueMist
            else -> if (dark) MaticaPalette.NightRaised else MaticaPalette.Mist
        }
    }

    fun slotBorderColor(isCorrect: Boolean?, dark: Boolean, glowAlpha: Float = 1f): Color = when (isCorrect) {
        true -> if (dark) MaticaPalette.Teal else MaticaPalette.GreenInk
        false -> if (dark) WarningAmberDark else WarningAmber
        null -> {
            val base = if (dark) MaticaPalette.BlueLight else MaticaPalette.Blue
            base.copy(alpha = glowAlpha)
        }
    }

    fun slotTextColor(hasToken: Boolean, isCorrect: Boolean?, dark: Boolean): Color = when (isCorrect) {
        true -> if (dark) MaticaPalette.Teal else MaticaPalette.GreenInk
        false -> if (dark) WarningAmberDark else WarningAmber
        null -> when {
            hasToken -> if (dark) MaticaPalette.MistText else MaticaPalette.Ink
            else -> if (dark) MaticaPalette.BlueLight else MaticaPalette.BlueDeep
        }
    }

    fun chipBackgroundColor(isSelected: Boolean, dark: Boolean): Color = when {
        isSelected -> if (dark) MaticaPalette.BlueDeep else MaticaPalette.BlueMist
        else -> if (dark) MaticaPalette.NightRaised else MaticaPalette.Paper
    }

    fun chipBorderColor(isSelected: Boolean, dark: Boolean): Color = when {
        isSelected -> if (dark) MaticaPalette.BlueLight else MaticaPalette.Blue
        else -> if (dark) MaticaPalette.BlueDeep else MaticaPalette.BlueSoft
    }

    fun chipTextColor(dark: Boolean): Color =
        if (dark) MaticaPalette.MistText else MaticaPalette.Ink

    fun measuredPairs(): List<MaticaColorPair> {
        val pairs = mutableListOf<MaticaColorPair>()
        for (dark in listOf(false, true)) {
            val theme = if (dark) "oscuro" else "claro"
            // Texto general sobre la tarjeta
            pairs += MaticaColorPair(
                if (dark) MaticaPalette.MistText else MaticaPalette.Ink,
                cardBackgroundColor(dark),
                "Texto de tarjeta en $theme",
            )
            // Fichas de opción
            pairs += MaticaColorPair(
                chipTextColor(dark),
                chipBackgroundColor(false, dark),
                "Ficha reposo en $theme",
            )
            pairs += MaticaColorPair(
                chipTextColor(dark),
                chipBackgroundColor(true, dark),
                "Ficha seleccionada en $theme",
            )
            // Hueco rellenado neutral
            pairs += MaticaColorPair(
                slotTextColor(true, null, dark),
                slotBackgroundColor(true, null, dark),
                "Hueco lleno neutral en $theme",
            )
            // Hueco correcto
            pairs += MaticaColorPair(
                slotTextColor(true, true, dark),
                slotBackgroundColor(true, true, dark),
                "Hueco acierto en $theme",
            )
            // Hueco incorrecto
            pairs += MaticaColorPair(
                slotTextColor(true, false, dark),
                slotBackgroundColor(true, false, dark),
                "Hueco fallo en $theme",
            )
            // Hueco placeholder vacío
            pairs += MaticaColorPair(
                slotTextColor(false, null, dark),
                slotBackgroundColor(false, null, dark),
                "Hueco placeholder en $theme",
            )
        }
        return pairs
    }
}

/**
 * Genera la lectura en voz alta para el estado del ejercicio de rellenar el hueco.
 */
fun blankAnnouncement(
    prefix: String,
    suffix: String,
    selectedToken: String?,
    placeholder: String = "?",
    isCorrect: Boolean? = null,
): String {
    val stateText = when (isCorrect) {
        true -> ", resultado correcto"
        false -> ", resultado incorrecto"
        null -> ""
    }
    val slotText = if (selectedToken != null) selectedToken else "hueco vacío"
    val raw = "$prefix $slotText $suffix$stateText"
    return equationAnnouncement(raw)
}

/**
 * Tarjeta interactiva de andamiaje 'Rellenar el hueco' (Fill in the blank).
 *
 * Ofrece un nivel intermedio de andamiaje entre la selección simple (A/B/C)
 * y la entrada con teclado matemático libre.
 *
 * Características:
 * - Muestra la estructura de la expresión o paso algebraico con un slot interactivo `[ ? ]`.
 * - Borde parpadeante sutil en azul primario cuando el slot está vacío (desactivado en modo reducido).
 * - Fichas rápidas de opciones para insertar directamente con un solo toque (touch target >= 48dp).
 * - Micro-interacción: sacudida horizontal (-6dp -> +6dp -> 0dp) ante fallo y pulso de escala ante acierto.
 * - Soporte para entrada personalizada adicional o teclado libre.
 * - Accesibilidad auditada WCAG AA con anuncios en español para lectores de pantalla.
 *
 * @param equationPrefix Expresión matemática que precede al hueco (ej. "2x + 4 - 4 = 12 - ").
 * @param equationSuffix Expresión matemática que sigue al hueco (ej. "").
 * @param selectedToken Término actualmente insertado en el hueco, o null si está vacío.
 * @param options Fichas rápidas disponibles para rellenar el espacio.
 * @param onTokenSelected Callback al seleccionar una ficha o ingresar un término.
 * @param modifier Modificador visual.
 * @param prompt Instrucción o pregunta que guía al estudiante.
 * @param blankPlaceholder Texto que se muestra cuando el hueco está vacío (por defecto "?").
 * @param isCorrect Estado de validación: null (en curso), true (acierto), false (error).
 * @param enabled Si los controles interactivos están habilitados.
 * @param onClear Callback opcional para limpiar el término insertado.
 * @param onSlotClick Callback opcional al presionar directamente el hueco.
 * @param customInputContent Contenido composable opcional para entrada personalizada (p. ej. teclado).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepFillInBlankCard(
    equationPrefix: String,
    modifier: Modifier = Modifier,
    equationSuffix: String = "",
    selectedToken: String? = null,
    options: List<String> = emptyList(),
    onTokenSelected: (String) -> Unit = {},
    prompt: String? = null,
    blankPlaceholder: String = "?",
    isCorrect: Boolean? = null,
    enabled: Boolean = true,
    onClear: (() -> Unit)? = null,
    onSlotClick: (() -> Unit)? = null,
    customInputContent: (@Composable () -> Unit)? = null,
) {
    val dark = LocalMaticaDarkTheme.current
    val motion = LocalMotionStyle.current

    // Animación de pulso sutil para el borde del hueco cuando está vacío
    val infiniteTransition = rememberInfiniteTransition(label = "slot-glow")
    val glowAlpha by if (!motion.reduced && selectedToken == null && isCorrect == null) {
        infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "slot-border-glow",
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // Micro-interacción: Sacudida horizontal (shake) ante respuesta incorrecta (-6dp -> +6dp -> 0dp)
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(isCorrect) {
        if (isCorrect == false && !motion.reduced) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 350
                    0f at 0
                    (-6f) at 50
                    6f at 100
                    (-6f) at 150
                    6f at 200
                    (-3f) at 250
                    3f at 300
                    0f at 350
                },
            )
        } else {
            shakeOffset.snapTo(0f)
        }
    }

    // Micro-interacción: Pulso de éxito (scale) ante acierto
    val successScale = remember { Animatable(1f) }
    LaunchedEffect(isCorrect) {
        if (isCorrect == true && !motion.reduced) {
            successScale.snapTo(1f)
            successScale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 350
                    1f at 0
                    1.06f at 150
                    1f at 350
                },
            )
        } else {
            successScale.snapTo(1f)
        }
    }

    val cardBg = StepFillInBlankPalette.cardBackgroundColor(dark)
    val cardBorder = StepFillInBlankPalette.cardBorderColor(isCorrect, dark)
    val slotBg = StepFillInBlankPalette.slotBackgroundColor(selectedToken != null, isCorrect, dark)
    val slotBorder = StepFillInBlankPalette.slotBorderColor(isCorrect, dark, glowAlpha)
    val slotText = StepFillInBlankPalette.slotTextColor(selectedToken != null, isCorrect, dark)

    val animatedBorderColor by animateColorAsState(
        targetValue = cardBorder,
        animationSpec = tween(durationMillis = 200),
        label = "card-border-color",
    )
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isCorrect != null) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 150),
        label = "card-border-width",
    )

    val cardShape = RoundedCornerShape(MaticaShapes.large)
    val slotShape = RoundedCornerShape(MaticaShapes.medium)

    val fullAnnouncement = blankAnnouncement(
        prefix = equationPrefix,
        suffix = equationSuffix,
        selectedToken = selectedToken,
        placeholder = blankPlaceholder,
        isCorrect = isCorrect,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset(x = shakeOffset.value.dp)
            .graphicsLayer {
                scaleX = successScale.value
                scaleY = successScale.value
            }
            .border(width = animatedBorderWidth, color = animatedBorderColor, shape = cardShape)
            .background(color = cardBg, shape = cardShape)
            .clip(cardShape)
            .semantics {
                contentDescription = fullAnnouncement
            }
            .padding(MaticaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaticaSpacing.md),
    ) {
        // Enunciado pedagógico opcional
        if (prompt != null) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dark) MaticaPalette.MistTextSoft else MaticaPalette.InkSoft,
            )
        }

        // Fila principal de la ecuación con el hueco interactivo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaticaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (equationPrefix.isNotEmpty()) {
                Text(
                    text = prettyMath(equationPrefix),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (dark) MaticaPalette.MistText else MaticaPalette.Ink,
                )
            }

            Spacer(Modifier.size(MaticaSpacing.sm))

            // Slot interactivo del hueco [ ? ]
            val slotInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                    .border(width = 2.dp, color = slotBorder, shape = slotShape)
                    .background(color = slotBg, shape = slotShape)
                    .clip(slotShape)
                    .clickable(
                        enabled = enabled && (onSlotClick != null || onClear != null),
                        interactionSource = slotInteraction,
                        indication = ripple(color = MaterialTheme.colorScheme.primary),
                        onClick = {
                            if (selectedToken != null && onClear != null) {
                                onClear()
                            } else {
                                onSlotClick?.invoke()
                            }
                        },
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = if (selectedToken != null) {
                            "Hueco completado con $selectedToken, pulsa para borrar"
                        } else {
                            "Hueco interactivo vacío, selecciona una ficha para insertar"
                        }
                    }
                    .padding(horizontal = MaticaSpacing.md, vertical = MaticaSpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = prettyMath(selectedToken ?: blankPlaceholder),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = slotText,
                )
            }

            if (equationSuffix.isNotEmpty()) {
                Spacer(Modifier.size(MaticaSpacing.sm))
                Text(
                    text = prettyMath(equationSuffix),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (dark) MaticaPalette.MistText else MaticaPalette.Ink,
                )
            }

            // Indicador de estado (Check o Alerta)
            if (isCorrect == true) {
                Spacer(Modifier.size(MaticaSpacing.md))
                MaticaGlyph(
                    icon = MaticaIcon.CHECK,
                    tint = if (dark) MaticaPalette.Teal else MaticaPalette.GreenInk,
                    modifier = Modifier.size(24.dp),
                )
            } else if (isCorrect == false) {
                Spacer(Modifier.size(MaticaSpacing.md))
                MaticaGlyph(
                    icon = MaticaIcon.WARNING,
                    tint = if (dark) StepFillInBlankPalette.WarningAmberDark else StepFillInBlankPalette.WarningAmber,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Fichas rápidas de opciones para insertar en el hueco
        if (options.isNotEmpty()) {
            Text(
                text = "Fichas para rellenar el paso:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = if (dark) MaticaPalette.MistTextSoft else MaticaPalette.InkSoft,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MaticaSpacing.sm),
            ) {
                options.forEach { option ->
                    val isSelected = (selectedToken == option)
                    val chipBg = StepFillInBlankPalette.chipBackgroundColor(isSelected, dark)
                    val chipBorder = StepFillInBlankPalette.chipBorderColor(isSelected, dark)
                    val chipTextColor = StepFillInBlankPalette.chipTextColor(dark)
                    val chipInteraction = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .heightIn(min = MaticaLayout.touchTarget)
                            .defaultMinSize(minWidth = 48.dp)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = chipBorder,
                                shape = RoundedCornerShape(MaticaShapes.medium),
                            )
                            .background(
                                color = chipBg,
                                shape = RoundedCornerShape(MaticaShapes.medium),
                            )
                            .clip(RoundedCornerShape(MaticaShapes.medium))
                            .clickable(
                                enabled = enabled,
                                role = Role.Button,
                                interactionSource = chipInteraction,
                                indication = ripple(color = MaterialTheme.colorScheme.primary),
                                onClick = { onTokenSelected(option) },
                            )
                            .semantics {
                                role = Role.Button
                                contentDescription = "Ficha ${prettyMath(option)}" +
                                    if (isSelected) ", seleccionada" else ""
                            }
                            .padding(horizontal = MaticaSpacing.md, vertical = MaticaSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = prettyMath(option),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            ),
                            color = chipTextColor,
                        )
                    }
                }
            }
        }

        // Contenido de entrada personalizado (por ejemplo, barra de teclado matemático)
        if (customInputContent != null) {
            Spacer(Modifier.height(MaticaSpacing.xs))
            customInputContent()
        }
    }
}
