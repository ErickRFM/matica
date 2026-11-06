package mx.matica.presentation.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.matica.design.brand.MaticaMark
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.motion.MotionStyle
import mx.matica.design.motion.rememberMotionStyle
import mx.matica.design.theme.LocalMaticaDarkTheme
import mx.matica.design.theme.MaticaTheme
import mx.matica.design.tokens.MaticaShapes
import mx.matica.design.tokens.MaticaSpacing
import mx.matica.presentation.route.MaticaNavigator

/**
 * Pantalla de arranque de Mática.
 *
 * No finge una descarga: la app local no habla con ningún servidor. Lo que hay detrás es el primer
 * cuadro del motor y el currículo, y esta capa existe para que ese arranque tenga la marca de la app
 * en vez de un destello del fondo del sistema. Por eso termina sola en algo más de un segundo y no
 * depende de ninguna petición.
 *
 * Toda la animación sale de un único progreso: el trazo de la marca, la mota que lo recorre, el
 * rótulo y la línea de avance son funciones de ese mismo valor. Así no hay relojes paralelos que se
 * desincronicen al cambiar la escala de movimiento del teléfono.
 */
@Composable
fun MaticaSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    motion: MotionStyle = LocalMotionStyle.current,
) {
    val duration = splashDurationMs(motion)
    val progress = remember(motion) { Animatable(if (motion.reduced) 1f else 0f) }
    val finished = rememberUpdatedState(onFinished)
    LaunchedEffect(motion) {
        if (!motion.reduced) progress.animateTo(1f, tween(durationMillis = duration, easing = LinearEasing))
        // Una pausa mínima con la marca completa: sin ella, el último cuadro del splash y el primero
        // de la app se relevan en el mismo frame y el gesto termina en seco.
        delay(SPLASH_HOLD_MS)
        finished.value()
    }

    val phase = splashPhases(progress.value)
    val scheme = MaterialTheme.colorScheme
    val dark = LocalMaticaDarkTheme.current
    // El degradado se mezcla con `lerp` y no con `copy(alpha = ...)`: un color translúcido deja ver
    // la pantalla que ya está compuesta debajo y el splash se lee como una capa sucia en vez de como
    // una pantalla. La mezcla conserva el tinte y tapa por completo.
    val tint = lerp(scheme.background, scheme.primaryContainer, if (dark) 0.5f else 0.7f)
    val backdrop = Brush.verticalGradient(listOf(scheme.background, tint, scheme.background))

    Box(modifier = modifier.fillMaxSize().background(backdrop)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Halo de marca: decorativo, sin semántica. Se ancla arriba del centro para que la marca
            // quede sobre la parte más luminosa del degradado y no sobre el borde de la pantalla.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.primary.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.38f),
                    radius = size.width * 0.62f,
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width / 2f, size.height * 0.38f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = MaticaSpacing.lg, vertical = MaticaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            MaticaMark(
                modifier = Modifier.size(SPLASH_MARK_SIZE),
                drawn = phase.markDraw,
                travelingDot = if (motion.reduced) null else phase.travel,
            )
            Spacer(modifier = Modifier.height(MaticaSpacing.xl))
            Text(
                text = "Mática",
                style = MaterialTheme.typography.displayMedium,
                color = scheme.onBackground,
                modifier = Modifier.graphicsLayer {
                    alpha = phase.wordAlpha
                    translationY = (1f - phase.wordAlpha) * 12.dp.toPx()
                },
            )
            Spacer(modifier = Modifier.height(MaticaSpacing.xs))
            Text(
                text = "Primero se entiende, después se practica.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = phase.taglineAlpha
                        translationY = (1f - phase.taglineAlpha) * 8.dp.toPx()
                    },
            )
            Spacer(modifier = Modifier.weight(1f))
            SplashProgress(progress = phase.progress)
            Spacer(modifier = Modifier.height(MaticaSpacing.sm))
            Text(
                text = "Preparando tu ruta",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Host de arranque: la app se compone por debajo desde el primer frame y el splash va encima. Así el
 * relevo es un fundido sobre una pantalla ya lista, no un salto a una pantalla vacía.
 *
 * No se pinta en cada recomposición ni en cada rotación: `startWithSplash` sólo es verdadero en un
 * arranque frío, y lo que se recuerda es que ya terminó, no que empezó.
 */
@Composable
fun MaticaLaunch(
    session: MaticaSession,
    navigator: MaticaNavigator,
    startWithSplash: Boolean = true,
) {
    var splashVisible by rememberSaveable { mutableStateOf(startWithSplash) }
    // `MaticaLaunch` vive fuera del tema de la app, así que el movimiento se resuelve con el ajuste
    // guardado y, si no hay ajuste manual, con la escala real del sistema.
    val systemMotion = rememberMotionStyle()
    val motion = MotionStyle(session.settings.motionScale ?: systemMotion.scale)
    val fade = if (motion.reduced) MotionStyle.FADE_REDUCED_MAX else motion.ms(MaticaMotion.durations.medium)
    Box(modifier = Modifier.fillMaxSize()) {
        MaticaApp(session = session, navigator = navigator)
        AnimatedVisibility(
            visible = splashVisible,
            enter = EnterTransition.None,
            exit = fadeOut(tween(fade)),
        ) {
            MaticaTheme(
                darkTheme = darkThemeOf(session.settings.themeMode, isSystemInDarkTheme()),
                motionScale = session.settings.motionScale,
            ) {
                MaticaSplash(onFinished = { splashVisible = false })
            }
        }
    }
}

@Composable
private fun SplashProgress(progress: Float) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .width(SPLASH_PROGRESS_WIDTH)
            .height(SPLASH_PROGRESS_HEIGHT)
            .clip(MaticaShapes.pill)
            .background(scheme.surfaceVariant)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress.coerceIn(0f, 1f),
                    range = 0f..1f,
                    steps = 0,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(MaticaShapes.pill)
                .background(scheme.primary),
        )
    }
}

/** Fases del splash para un progreso 0..1. Es una función pura: la prueba la recorre sin pantalla. */
internal data class SplashPhases(
    val progress: Float,
    val markDraw: Float,
    val travel: Float,
    val wordAlpha: Float,
    val taglineAlpha: Float,
)

internal fun splashPhases(progress: Float): SplashPhases {
    val clamped = progress.coerceIn(0f, 1f)
    return SplashPhases(
        progress = clamped,
        markDraw = smoothstep(clamped, from = 0f, to = 0.55f),
        travel = smoothstep(clamped, from = 0.25f, to = 0.85f),
        wordAlpha = smoothstep(clamped, from = 0.42f, to = 0.72f),
        taglineAlpha = smoothstep(clamped, from = 0.60f, to = 0.90f),
    )
}

/**
 * Duración total de la animación. La escala del sistema la acorta o alarga, pero nunca por debajo de
 * [SPLASH_MIN_MS]: el splash no es contenido que se pueda saltar, así que una escala absurda no debe
 * dejarlo en un parpadeo. Con movimiento reducido se queda en [SPLASH_REDUCED_MS] y no viaja la mota.
 */
internal fun splashDurationMs(motion: MotionStyle): Int = when {
    motion.reduced -> SPLASH_REDUCED_MS
    else -> (SPLASH_BASE_MS * motion.scale).toInt().coerceIn(SPLASH_MIN_MS, SPLASH_MAX_MS)
}

private fun smoothstep(value: Float, from: Float, to: Float): Float {
    if (to <= from) return if (value >= to) 1f else 0f
    val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private val SPLASH_MARK_SIZE = 132.dp
private val SPLASH_PROGRESS_WIDTH = 168.dp
private val SPLASH_PROGRESS_HEIGHT = 4.dp

internal const val SPLASH_BASE_MS = 1_000
internal const val SPLASH_MIN_MS = 500
internal const val SPLASH_MAX_MS = 1_400
internal const val SPLASH_REDUCED_MS = 350
internal const val SPLASH_HOLD_MS = 100L
