package mx.matica.app

import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.math.skills.SkillId
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inventario de pantallas sobre el dispositivo: los 18 destinos se montan de verdad y cada uno pinta
 * su texto distintivo exactamente una vez. Los textos vienen de sondear la app en el emulador, no de
 * la lista de rutas, y el `count == 1` existe porque la sonda encontró una hoja modal dibujada dos
 * veces cuando el historial llegaba directo a ella.
 */
@RunWith(AndroidJUnit4::class)
class MaticaScreenInventoryTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun losDieciochoDestinosSeMontanUnaSolaVezConSuTextoDistintivo() {
        assertEquals(18, MaticaRoute.all.size)
        assertEquals("los paths deben distinguirse", 18, MaticaRoute.all.map { it.path }.distinct().size)

        var target by mutableStateOf<MaticaRoute>(MaticaRoute.Welcome)
        val session = MaticaSession(today = LocalDate(2026, 11, 5))
        rule.setContent {
            val route = target
            key(route.path) {
                val navigator = remember(route) { stackOf(route) }
                MaticaApp(session = session, navigator = navigator)
            }
        }

        markers().forEach { (path, marker) ->
            val route = requireNotNull(MaticaRoute.parse(path)) { "Ruta desconocida en la sonda: $path" }
            rule.runOnIdle { target = route }
            rule.waitForIdle()
            rule.onAllNodes(hasText(marker)).assertCountEquals(1)
            // Una pantalla sin nada alrededor es un título suelto. Cuatro textos es el mínimo real
            // de un estado vacío honesto (título, subtítulo, motivo y detalle); Repasos lo cumple
            // sin inventar un botón para una acción que el motor no puede ofrecer todavía.
            assertTrue(
                paintedTexts().size >= 4,
                "$path pintó ${paintedTexts().size} textos: demasiado hueca para contar como pantalla.",
            )
        }
    }

    @Test
    fun entrarEnUnaHabilidadEmpujaLaPilaYSalirLaRegresa() {
        val session = MaticaSession(today = LocalDate(2026, 11, 5))
        val navigator = MaticaNavigator(MaticaRoute.Home)
        rule.setContent { MaticaApp(session = session, navigator = navigator) }
        rule.waitForIdle()

        rule.onNodeWithText("Abrir mi ruta").performClick()
        rule.waitForIdle()
        assertEquals(MaticaRoute.RouteMap, navigator.current)
        assertEquals(2, navigator.depth)

        rule.runOnIdle { navigator.go(MaticaRoute.SkillDetail(SkillId.BALANCE_PRINCIPLE)) }
        rule.waitForIdle()
        rule.onNodeWithText("Por qué esta habilidad").assertPresent("el detalle de la habilidad")

        rule.runOnIdle { navigator.goBack() }
        rule.waitForIdle()
        assertEquals(MaticaRoute.RouteMap, navigator.current)
        rule.onNodeWithText("Mi ruta").assertPresent("el mapa de ruta")

        rule.runOnIdle { navigator.goBack() }
        rule.waitForIdle()
        assertEquals(MaticaRoute.Home, navigator.current)
        assertTrue(!navigator.canGoBack, "La pila quedó con fondo: el botón atrás seguiría activo.")
    }

    @Test
    fun unaHojaAbiertaPorEnlaceProfundoNoSeDuplicaDebajoDeSiMisma() {
        val session = MaticaSession(today = LocalDate(2026, 11, 5))
        // Historial que llega directo a la capa modal, sin reproductor debajo: es lo que produce un
        // enlace profundo o un salvado restaurado a medias.
        val navigator = MaticaNavigator(MaticaRoute.HintSheet(SkillId.BALANCE_PRINCIPLE, 1))
        rule.setContent { MaticaApp(session = session, navigator = navigator) }
        rule.waitForIdle()

        rule.onAllNodes(hasText("Mostrar una pista más")).assertCountEquals(1)
        rule.onNodeWithText("Pistas").assertPresent("el título de la hoja")
    }

    @Test
    fun unEnlaceRotoNoReiniciaAlEstudiante() {
        val session = MaticaSession(today = LocalDate(2026, 11, 5))
        val navigator = MaticaNavigator(MaticaRoute.Home)
        rule.setContent { MaticaApp(session = session, navigator = navigator) }
        rule.waitForIdle()
        rule.runOnIdle { navigator.go(MaticaRoute.Rewards) }
        rule.waitForIdle()
        rule.onNodeWithText("Marcas").assertPresent("la pantalla de marcas")

        // Un historial a medias conserva lo que aún existe y descarta lo corrupto, sin volver al inicio.
        assertTrue(navigator.restore(listOf("route/skill/NO_EXISTE", "review", "settings")))
        assertEquals(listOf("review", "settings"), navigator.asPaths())
        assertEquals(MaticaRoute.Settings, navigator.current)
        rule.waitForIdle()
        rule.onNodeWithText("Marcas").assertAbsent("la pantalla que quedó descartada")
        rule.onNodeWithText("Accesibilidad primero").assertPresent("el ajuste restaurado")

        // Un historial sin una sola ruta válida no debe aplastar la pila que el estudiante ya tenía.
        assertFalse(navigator.restore(listOf("ruta/inventada/2026")))
        assertEquals(listOf("review", "settings"), navigator.asPaths())
    }

    /** Pila tal como la arma la app: la hoja modal siempre entra encima de su reproductor. */
    private fun stackOf(target: MaticaRoute): MaticaNavigator {
        val underlying = when (target) {
            is MaticaRoute.HintSheet -> MaticaRoute.LessonPlayer(target.skill, target.lesson)
            else -> target
        }
        return MaticaNavigator(underlying).also { if (it.current != target) it.go(target) }
    }

    private fun markers(): Map<String, String> = linkedMapOf(
        MaticaRoute.Welcome.path to "Comenzar el diagnóstico",
        MaticaRoute.Diagnosis.path to "0 de 15 respondidas",
        MaticaRoute.DiagnosisResult.path to "Dónde quedas",
        MaticaRoute.Login.path to "Cuentas no disponibles en esta versión",
        MaticaRoute.Home.path to "Abrir mi ruta",
        MaticaRoute.RouteMap.path to "Mi ruta",
        MaticaRoute.SkillDetail(SkillId.BALANCE_PRINCIPLE).path to "Por qué esta habilidad",
        MaticaRoute.LessonList(SkillId.BALANCE_PRINCIPLE).path to "Lecciones de Equilibrio de la ecuación",
        MaticaRoute.LessonPlayer(SkillId.BALANCE_PRINCIPLE, 1).path to "Comprobar paso",
        MaticaRoute.HintSheet(SkillId.BALANCE_PRINCIPLE, 1).path to "Mostrar una pista más",
        MaticaRoute.LessonComplete(SkillId.BALANCE_PRINCIPLE, 1).path to "¡EXCELENTE!",
        MaticaRoute.AttemptReview(SkillId.BALANCE_PRINCIPLE, 1, 1).path to "Ese intento ya no está",
        MaticaRoute.Practice("two-step-linear", 1L).path to "Recta: dónde cae la solución",
        MaticaRoute.ReviewQueue.path to "Nada vencido hoy",
        MaticaRoute.Rewards.path to "Marcas",
        MaticaRoute.Profile.path to "Reiniciar el perfil local",
        MaticaRoute.Settings.path to "Transiciones reducidas: apagadas",
        MaticaRoute.Classroom.path to "Grupo del docente",
    )

    private fun paintedTexts(): List<String> =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { part -> part.text } }
            .filter { it.isNotBlank() }
            .distinct()

    private fun SemanticsNodeInteraction.assertPresent(what: String) {
        val found = runCatching { assertExists() }.isSuccess
        assertTrue(found, "$what no apareció en el dispositivo.")
    }

    private fun SemanticsNodeInteraction.assertAbsent(what: String) {
        val gone = runCatching { assertDoesNotExist() }.isSuccess
        assertTrue(gone, "$what siguió en pantalla aunque la pila ya no está ahí.")
    }
}
