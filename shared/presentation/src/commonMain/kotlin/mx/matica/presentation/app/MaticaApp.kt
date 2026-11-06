package mx.matica.presentation.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MaticaMotion
import mx.matica.design.motion.MotionStyle
import mx.matica.design.theme.MaticaTheme
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute

/**
 * Host de la app: tema en vigor, pila de rutas y transiciones entre pantallas.
 *
 * Vive en `:shared:presentation` y no en `:apps:androidApp` para que las 18 pantallas, su navegación
 * y su movimiento se puedan ejercitar sin emulador ni dispositivo.
 */
@Composable
fun MaticaApp(
    session: MaticaSession = remember { MaticaSession() },
    navigator: MaticaNavigator = rememberMaticaNavigator(),
) {
    val settings = session.settings
    MaticaTheme(
        darkTheme = darkThemeOf(settings.themeMode, isSystemInDarkTheme()),
        motionScale = settings.motionScale,
    ) {
        MaticaNavHost(session = session, navigator = navigator, reduceTransitions = settings.reduceTransitions)
    }
}

/**
 * `SYSTEM` es el único modo que mira al dispositivo; forzar claro u oscuro nunca deja la app sin un
 * tema medido, así que la resolución es total: no hay camino que caiga en el tema por defecto.
 */
fun darkThemeOf(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Navegador que sobrevive a la rotación y a la muerte del proceso serializando la pila como lista de
 * `path`. Si el sistema restaura un historial corrupto, [MaticaNavigator.restore] conserva las rutas
 * válidas y descarta el resto en vez de reiniciar al estudiante en la pantalla de bienvenida.
 */
@Composable
fun rememberMaticaNavigator(start: MaticaRoute = MaticaRoute.Welcome): MaticaNavigator {
    val saver = Saver<MaticaNavigator, List<String>>(
        save = { it.asPaths() },
        restore = { paths -> MaticaNavigator(start).also { it.restore(paths) } },
    )
    return rememberSaveable(saver = saver) { MaticaNavigator(start) }
}

@Composable
private fun MaticaNavHost(session: MaticaSession, navigator: MaticaNavigator, reduceTransitions: Boolean) {
    val style = LocalMotionStyle.current
    // El ajuste manual y el movimiento reducido del sistema dan el mismo efecto: fundido corto, sin desplazamiento.
    val quiet = reduceTransitions || style.reduced
    val overlay = if (navigator.current.isOverlay) navigator.current else null
    // Un enlace profundo puede traer sólo la hoja (`lesson/X/1/hints`) sin pantalla debajo. Entonces la
    // hoja ya es el `base` y dibujarla además como capa de fondo la pinta dos veces encima de sí misma.
    val baseRoute = if (overlay != null && navigator.base == overlay) null else navigator.base
    // El fondo oscuro sólo anima su alpha, no su posición: metido dentro del `AnimatedContent` de la
    // hoja bajaba deslizando con ella, y así el scrim se funde mientras la hoja sube desde el borde.
    val scrimAlpha by animateFloatAsState(
        targetValue = if (overlay != null) SCRIM_ALPHA else 0f,
        animationSpec = tween(if (quiet) 0 else style.fadeMs(MaticaMotion.durations.short)),
        label = "matica-scrim",
    )
    // `Surface` y no un `Column` suelto: sin él el fondo que se ve es el de la ventana del sistema,
    // no el token medido del tema, y cambiar de tema en Ajustes no pinta nada (así lo detectó la
    // prueba de instrumentación sobre el píxel).
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        // Los insets van en el host y no en cada pantalla: son 18 pantallas y un solo host, así que el
        // notch, la barra de navegación y el teclado se resuelven una vez y ninguna ruta puede olvidarlos.
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            AnimatedContent(
                targetState = RouteTarget(baseRoute, navigator.baseDepth),
                transitionSpec = {
                    val (enter, exit) = screenSpec(quiet, style, forward = targetState.depth >= initialState.depth)
                    enter togetherWith exit
                },
                contentKey = { it.route?.path },
                modifier = Modifier.fillMaxSize(),
            ) { target ->
                target.route?.let { MaticaRouteContent(it, session, navigator) }
            }
            // Capa independiente, debajo de la hoja: tocando el negro también se cierra, sin depender
            // del botón «Regresa». Al cerrarse `enabled` se apaga antes de que termine de fundirse.
            if (scrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                        .clickable(enabled = overlay != null, onClickLabel = SCRIM_ON_CLICK_LABEL) {
                            navigator.goBack()
                        },
                )
            }
            // La capa modal se dibuja encima de la pantalla que la abrió: cerrarla no vuelve a montar el reproductor.
            AnimatedContent(
                targetState = RouteTarget(overlay, if (overlay == null) 0 else 1),
                transitionSpec = {
                    val (enter, exit) = sheetSpec(quiet, style, opening = targetState.route != null)
                    enter togetherWith exit
                },
                contentKey = { it.route?.path },
                modifier = Modifier.fillMaxSize(),
            ) { target ->
                val route = target.route ?: return@AnimatedContent
                MaticaRouteContent(route, session, navigator)
            }
        }
    }
}

private data class RouteTarget(val route: MaticaRoute?, val depth: Int)

/** Las pantallas entran de lado: la dirección sigue a la profundidad, así «atrás» deshace el gesto. */
private fun screenSpec(quiet: Boolean, style: MotionStyle, forward: Boolean): Pair<EnterTransition, ExitTransition> {
    if (quiet) return fadePair(style)
    val ms = style.ms(MaticaMotion.durations.medium)
    val off = if (forward) 1 else -1
    return (slideInHorizontally(tween(ms)) { off * it / 5 } + fadeIn(tween(ms))) to
        (slideOutHorizontally(tween(ms)) { -off * it / 5 } + fadeOut(tween(ms / 2)))
}

/** La hoja de pistas sube desde el borde inferior y baja al mismo sitio al cerrarse. */
private fun sheetSpec(quiet: Boolean, style: MotionStyle, opening: Boolean): Pair<EnterTransition, ExitTransition> {
    if (quiet) return fadePair(style)
    val ms = style.ms(MaticaMotion.durations.medium)
    return if (opening) {
        (slideInVertically(tween(ms)) { it } + fadeIn(tween(ms))) to
            (slideOutVertically(tween(ms)) { -it / 4 } + fadeOut(tween(ms)))
    } else {
        (slideInVertically(tween(ms)) { -it / 4 } + fadeIn(tween(ms))) to
            (slideOutVertically(tween(ms)) { it } + fadeOut(tween(ms)))
    }
}

private fun fadePair(style: MotionStyle): Pair<EnterTransition, ExitTransition> {
    val fade = style.fadeMs(MaticaMotion.durations.short)
    return fadeIn(tween(fade)) to fadeOut(tween(fade))
}

private const val SCRIM_ALPHA = 0.45f

/** Lo que dice TalkBack al activar la capa oscura: sin etiqueta, el toque sería un misterio accesible. */
private const val SCRIM_ON_CLICK_LABEL = "Cerrar las pistas"

/** Desempeño de las 18 rutas. Cada pantalla recibe el navegador para decidir hacia dónde empuja. */
@Composable
fun MaticaRouteContent(route: MaticaRoute, session: MaticaSession, navigator: MaticaNavigator) {
    when (route) {
        MaticaRoute.Welcome -> WelcomeScreen(session = session, navigator = navigator)
        MaticaRoute.Diagnosis -> DiagnosisScreen(session = session, navigator = navigator)
        MaticaRoute.DiagnosisResult -> DiagnosisResultScreen(session = session, navigator = navigator)
        MaticaRoute.Login -> LoginScreen(session = session, navigator = navigator)
        MaticaRoute.Home -> HomeScreen(session = session, navigator = navigator)
        MaticaRoute.RouteMap -> RouteMapScreen(session = session, navigator = navigator)
        is MaticaRoute.SkillDetail -> SkillDetailScreen(route.skill, session, navigator)
        is MaticaRoute.LessonList -> LessonListScreen(route.skill, session, navigator)
        is MaticaRoute.LessonPlayer -> LessonPlayerScreen(route.skill, route.lesson, session, navigator)
        is MaticaRoute.HintSheet -> HintSheetScreen(route.skill, route.lesson, session, navigator)
        is MaticaRoute.LessonComplete -> LessonCompleteScreen(route.skill, route.lesson, session, navigator)
        is MaticaRoute.AttemptReview -> AttemptReviewScreen(route.skill, route.lesson, route.attempt, session, navigator)
        is MaticaRoute.Practice -> PracticeScreen(route.templateId, route.seed, session, navigator)
        MaticaRoute.ReviewQueue -> ReviewQueueScreen(session = session, navigator = navigator)
        MaticaRoute.Rewards -> RewardsScreen(session = session, navigator = navigator)
        MaticaRoute.Profile -> ProfileScreen(session = session, navigator = navigator)
        MaticaRoute.Settings -> SettingsScreen(session = session, navigator = navigator)
        MaticaRoute.Classroom -> ClassroomScreen(session = session, navigator = navigator)
    }
}
