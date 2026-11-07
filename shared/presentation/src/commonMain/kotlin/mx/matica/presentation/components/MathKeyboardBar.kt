package mx.matica.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.tokens.MaticaLayout
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing

/**
 * Tipo semántico de tecla dentro de la barra de acceso rápido matemático.
 */
enum class MathKeyType {
    VARIABLE,
    OPERATION,
    GROUPING,
    POWER,
    ACTION,
}

/**
 * Modelo de una tecla matemática. Desacoplado de la vista para permitir pruebas
 * unitarias exhaustivas sin necesidad de un host de Compose.
 */
data class MathKeyItem(
    val id: String,
    val label: String,
    val symbol: String? = null,
    val contentDescription: String,
    val type: MathKeyType,
    val isAction: Boolean = false,
    val isVariable: Boolean = false,
)

/**
 * Configuración y símbolos por defecto para la barra de acceso rápido matemático.
 */
object MathKeyboardDefaults {
    const val DEFAULT_VARIABLE = "x"
    const val DEFAULT_POWER = "²"
    const val PRESS_SCALE = 0.94f

    val OPERATIONS = listOf("+", "-", "*", "/", "=")
    val GROUPING = listOf("(", ")")

    /**
     * Construye la lista de teclas requeridas respetando la variable del ejercicio actual
     * y los símbolos clave que suelen estar ocultos tras submenús en teclados móviles.
     */
    fun defaultKeys(
        variable: String = DEFAULT_VARIABLE,
        powerSymbol: String = DEFAULT_POWER,
        hasClear: Boolean = false,
    ): List<MathKeyItem> = buildList {
        // Variable principal del problema (ej. "x")
        add(
            MathKeyItem(
                id = "var_$variable",
                label = variable,
                symbol = variable,
                contentDescription = "Insertar $variable",
                type = MathKeyType.VARIABLE,
                isVariable = true,
            ),
        )

        // Exponente común (cuadrado)
        add(
            MathKeyItem(
                id = "power",
                label = powerSymbol,
                symbol = powerSymbol,
                contentDescription = "Insertar $powerSymbol",
                type = MathKeyType.POWER,
            ),
        )

        // Agrupadores
        GROUPING.forEach { group ->
            add(
                MathKeyItem(
                    id = "group_$group",
                    label = group,
                    symbol = group,
                    contentDescription = "Insertar $group",
                    type = MathKeyType.GROUPING,
                ),
            )
        }

        // Operaciones aritméticas y relación
        OPERATIONS.forEach { op ->
            add(
                MathKeyItem(
                    id = "op_$op",
                    label = op,
                    symbol = op,
                    contentDescription = "Insertar $op",
                    type = MathKeyType.OPERATION,
                ),
            )
        }

        // Tecla de retroceso / borrado
        add(
            MathKeyItem(
                id = "delete",
                label = "⌫",
                symbol = null,
                contentDescription = "Borrar",
                type = MathKeyType.ACTION,
                isAction = true,
            ),
        )

        // Tecla de limpieza opcional. «AC» y no «C»: en el reproductor las alternativas de primer
        // paso ya se rotulan A, B y C, y una tecla con la misma letra se lee como otra opción.
        if (hasClear) {
            add(
                MathKeyItem(
                    id = "clear",
                    label = "AC",
                    symbol = null,
                    contentDescription = "Borrar todo",
                    type = MathKeyType.ACTION,
                    isAction = true,
                ),
            )
        }
    }
}

/**
 * Concatena un símbolo al texto existente.
 */
fun appendMathSymbol(current: String, symbol: String): String = current + symbol

/**
 * Elimina el último carácter del borrador de la ecuación si no está vacío.
 */
fun deleteLastMathSymbol(current: String): String =
    if (current.isNotEmpty()) current.dropLast(1) else current

/**
 * Botón individual de tecla para la barra de matemáticas.
 * Cumple con tamaño táctil >= 44-48dp, redondeo [MaticaShapes.small], colores de alto contraste
 * de [MaterialTheme.colorScheme], animación de resorte con escala 0.94 al presionar
 * y semántica accesible [Role.Button].
 */
@Composable
fun MathKeyButton(
    key: MathKeyItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = if (key.isAction) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    },
    contentColor: Color = if (key.isAction) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
) {
    val motion = LocalMotionStyle.current
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduced) MathKeyboardDefaults.PRESS_SCALE else 1f,
        animationSpec = MaticaMotion.popSpec(motion),
        label = "matica-math-key-press",
    )
    val shape = RoundedCornerShape(MaticaShapes.small)

    Box(
        modifier = modifier
            .height(MaticaLayout.touchTarget)
            .defaultMinSize(minWidth = 44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(color = backgroundColor, shape = shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = contentColor),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.role = Role.Button
                this.contentDescription = key.contentDescription
            }
            .padding(horizontal = MaticaSpacing.sm, vertical = MaticaSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (key.isVariable || key.isAction) FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (key.isVariable) FontStyle.Italic else FontStyle.Normal,
        )
    }
}

/**
 * Barra de acceso rápido para escribir operaciones y signos matemáticos en pantallas táctiles.
 * Facilita a los estudiantes ingresar '=', '+', '-', '(', ')', variables y potencias sin luchar
 * contra los submenús de símbolos del teclado virtual.
 *
 * @param onInsert Invocado al presionar una tecla de símbolo o variable.
 * @param onDelete Invocado al presionar la tecla de retroceso (⌫).
 * @param onClear Opcional: invocado al presionar la tecla de borrar todo (AC). Si no es nulo, se muestra la tecla.
 * @param modifier Modificador visual para posicionar la barra.
 * @param variable Símbolo de la incógnita del ejercicio (por defecto "x").
 * @param powerSymbol Símbolo de la potencia cuadrática (por defecto "²").
 */
@Composable
fun MathKeyboardBar(
    onInsert: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    variable: String = MathKeyboardDefaults.DEFAULT_VARIABLE,
    powerSymbol: String = MathKeyboardDefaults.DEFAULT_POWER,
) {
    val scrollState = rememberScrollState()
    val keys = remember(variable, powerSymbol, onClear != null) {
        MathKeyboardDefaults.defaultKeys(
            variable = variable,
            powerSymbol = powerSymbol,
            hasClear = onClear != null,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = MaticaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MaticaSpacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { key ->
            MathKeyButton(
                key = key,
                onClick = {
                    when (key.id) {
                        "delete" -> onDelete()
                        "clear" -> onClear?.invoke()
                        else -> key.symbol?.let { onInsert(it) }
                    }
                },
            )
        }
    }
}
