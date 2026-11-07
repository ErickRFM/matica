package mx.matica.presentation.math

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.theme.LocalMaticaDarkTheme
import mx.matica.design.theme.MaticaColorPair
import mx.matica.design.tokens.MaticaPalette

/**
 * Dirección del trazo de cancelación o tachado algebraico.
 */
enum class StrikeDirection {
    /** Diagonal de abajo a la izquierda a arriba a la derecha (cancelación algebraica estándar). */
    DIAGONAL_UP,

    /** Diagonal de arriba a la izquierda a abajo a la derecha. */
    DIAGONAL_DOWN,

    /** Línea horizontal recta (tachado continuo o simplificación). */
    HORIZONTAL,
}

/**
 * Paleta y tokens de contraste para el componente [StepStrikeThroughView],
 * auditados contra las pautas WCAG AA (4.5:1 para texto / 3:1 gráfico).
 */
object StepStrikePalette {
    val SoftRedLight = Color(0xFFB3001B)
    val SoftRedDark = Color(0xFFFFB4AB)
    val AmberLight = Color(0xFFB45309)
    val AmberDark = Color(0xFFFBBF24)

    fun cancelColor(dark: Boolean, isAmber: Boolean = false): Color = when {
        isAmber -> if (dark) AmberDark else AmberLight
        else -> if (dark) SoftRedDark else SoftRedLight
    }

    fun measuredPairs(): List<MaticaColorPair> = listOf(
        MaticaColorPair(SoftRedLight, MaticaPalette.Paper, "Tachado rojo en tema claro"),
        MaticaColorPair(SoftRedDark, MaticaPalette.NightSurface, "Tachado rojo en tema oscuro"),
        MaticaColorPair(AmberLight, MaticaPalette.Paper, "Tachado ámbar en tema claro"),
        MaticaColorPair(AmberDark, MaticaPalette.NightSurface, "Tachado ámbar en tema oscuro"),
    )
}

/**
 * Calcula los puntos de inicio y fin del trazo de tachado dentro de las dimensiones dadas.
 */
fun calculateStrikeEndpoints(
    direction: StrikeDirection,
    width: Float,
    height: Float,
    padding: Float = 0f,
): Pair<Offset, Offset> {
    val start = when (direction) {
        StrikeDirection.DIAGONAL_UP -> Offset(padding, (height - padding).coerceAtLeast(0f))
        StrikeDirection.DIAGONAL_DOWN -> Offset(padding, padding)
        StrikeDirection.HORIZONTAL -> Offset(padding, height / 2f)
    }
    val targetEnd = when (direction) {
        StrikeDirection.DIAGONAL_UP -> Offset((width - padding).coerceAtLeast(0f), padding)
        StrikeDirection.DIAGONAL_DOWN -> Offset((width - padding).coerceAtLeast(0f), (height - padding).coerceAtLeast(0f))
        StrikeDirection.HORIZONTAL -> Offset((width - padding).coerceAtLeast(0f), height / 2f)
    }
    return Pair(start, targetEnd)
}

/**
 * Interpola linealmente un punto entre `start` y `end` en base al progreso de 0.0 a 1.0.
 */
fun interpolateOffset(start: Offset, end: Offset, progress: Float): Offset {
    val clamped = progress.coerceIn(0f, 1f)
    return Offset(
        x = start.x + (end.x - start.x) * clamped,
        y = start.y + (end.y - start.y) * clamped,
    )
}

/**
 * Genera la descripción semántica accesible para lectores de pantalla.
 */
fun strikeAnnouncement(term: String, isCancelled: Boolean = true): String =
    if (isCancelled) "Término cancelado: $term" else term

/**
 * Componente visual para animación de tachado / cancelación algebraica.
 *
 * Utilizado cuando un paso cancela términos opuestos (ej. `+4` y `-4` suman cero)
 * o simplifica factores comunes en fracciones algebraicas.
 *
 * Características:
 * - Dibuja progresivamente una línea recta o diagonal de 0% a 100% con [StrokeCap.Round].
 * - Respeta [LocalMotionStyle.current.reduced] mostrando el tachado estático sin animación
 *   en caso de movimiento reducido.
 * - Incluye accesibilidad semántica (`contentDescription = "Término cancelado: ..."`).
 * - Calibración visual de 2 a 3 dp de grosor con colores semánticos de alto contraste.
 *
 * @param term Expresión o término matemático a tachar (ej. `+ 4`, `- 4`, `2`).
 * @param modifier Modificador de diseño.
 * @param isCancelled Si el término está efectivamente cancelado (activa el tachado).
 * @param direction Dirección geométrica de la línea de cancelación.
 * @param color Color del trazo (por defecto usa [StepStrikePalette.cancelColor]).
 * @param strokeWidth Grosor de la línea (2 a 3 dp).
 * @param textStyle Estilo tipográfico si se renderiza el texto por defecto.
 * @param content Contenido composable opcional si se desea envolver un layout complejo.
 */
@Composable
fun StepStrikeThroughView(
    term: String,
    modifier: Modifier = Modifier,
    isCancelled: Boolean = true,
    direction: StrikeDirection = StrikeDirection.DIAGONAL_UP,
    color: Color? = null,
    strokeWidth: Dp = 2.5.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
    content: (@Composable () -> Unit)? = null,
) {
    val dark = LocalMaticaDarkTheme.current
    val motion = LocalMotionStyle.current
    val strokeColor = color ?: StepStrikePalette.cancelColor(dark)
    val strokeWidthPx = with(LocalDensity.current) { strokeWidth.toPx() }

    // Progreso de animación (0.0 a 1.0)
    val progress = remember {
        Animatable(if (motion.reduced || !isCancelled) (if (isCancelled) 1f else 0f) else 0f)
    }

    LaunchedEffect(isCancelled, motion.reduced) {
        if (motion.reduced) {
            progress.snapTo(if (isCancelled) 1f else 0f)
        } else if (isCancelled) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            )
        } else {
            progress.snapTo(0f)
        }
    }

    val accessibleDesc = strikeAnnouncement(term, isCancelled)

    Box(
        modifier = modifier
            .semantics {
                contentDescription = accessibleDesc
            },
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            Text(
                text = prettyMath(term),
                style = textStyle,
                color = if (dark) MaticaPalette.MistText else MaticaPalette.Ink,
            )
        }

        Canvas(modifier = Modifier.matchParentSize()) {
            if (progress.value > 0f) {
                val pad = strokeWidthPx / 2f
                val (start, targetEnd) = calculateStrikeEndpoints(
                    direction = direction,
                    width = size.width,
                    height = size.height,
                    padding = pad,
                )
                val currentEnd = interpolateOffset(start, targetEnd, progress.value)
                drawLine(
                    color = strokeColor,
                    start = start,
                    end = currentEnd,
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
