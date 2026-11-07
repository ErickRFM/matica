package mx.matica.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.exercise.ExerciseRenderPlan
import mx.matica.presentation.math.figureCandidates
import mx.matica.presentation.math.fractionModel
import mx.matica.presentation.math.nearestTick
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El recorrido con teclado físico, medido con teclas inyectadas por la ventana y no con
 * `performKeyInput`: la inyección por nodo sólo llega a los manejadores de ese nodo, así que nunca
 * recorre el foco de la pantalla entera (así lo mostró la sonda del 2026-11-06, que dejaba el foco
 * clavado en el campo de la respuesta mientras la tecla real avanzaba control por control).
 */
@RunWith(AndroidJUnit4::class)
class MaticaKeyboardWalkTest {

    @get:Rule
    val rule = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun elTecladoRecorreTodosLosControlesTocablesDeLaPractica() {
        planEnPantalla(GEOMETRY)
        val esperados = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map(::labelOf)
        assertTrue(esperados.isNotEmpty(), "La práctica no dejó ningún control tocable que recorrer.")

        val visitados = esperados.indices.map { tab(); focusedLabel() }
        assertEquals(esperados, visitados, "La ronda de Tab no visita los controles en el orden en que están dibujados.")
        assertEquals(esperados.size, visitados.distinct().size, "El teclado se quedó dando vueltas sobre el mismo control.")
    }

    @Test
    fun laFlechaDeLaFiguraFocalizadaSeActivaConEnter() {
        val plan = planEnPantalla(GEOMETRY)
        val figure = requireNotNull(plan.figure) { "La plantilla geométrica no dejó figura." }
        val candidates = figureCandidates(plan.numberLine)
        val siguiente = candidates[candidates.indexOf(plan.solution) + 1]

        focusOn("Lado siguiente")
        pressKey(KeyEvent.KEYCODE_ENTER)

        rule.onNode(hasText("Lado $siguiente · ${figure.totalLabelEs} ${figure.totalAt(siguiente)}"))
            .assertPresent("el lado siguiente, alcanzado sólo con teclado")
    }

    @Test
    fun elTecladoRevelaLaSolucionSinTocarLaPantalla() {
        val plan = planEnPantalla(GEOMETRY)
        val mezcla = fractionModel(plan.solution, asMixed = true).accessibleText

        focusOn("Mostrar la solución")
        rule.onNode(hasContentDescription(mezcla)).assertNotExists("la solución antes de pedirla")

        pressKey(KeyEvent.KEYCODE_ENTER)
        rule.onNode(hasContentDescription(mezcla)).performScrollTo().assertIsDisplayedNow()
    }

    @Test
    fun lasFlechasDeLaRectaMuevenElPuntoDelPlano() {
        val plan = planEnPantalla(TWO_STEP)
        val line = plan.lines.first()
        val ticks = plan.numberLine.orderedTicks
        val siguiente = ticks[ticks.indexOf(nearestTick(plan.solution, ticks)) + 1]

        focusOn("Marca siguiente")
        pressKey(KeyEvent.KEYCODE_ENTER)

        // El punto del plano y el marcador de la recta hablan de la misma conjetura: si el teclado
        // mueve uno, el otro no puede quedarse contando la hipótesis vieja.
        rule.onNode(hasText("Marcado: $siguiente")).assertPresent("la recta marcada por el teclado")
        rule.onNode(hasText("${plan.variable} = $siguiente · y = ${line.slope * siguiente + line.intercept}"))
            .assertPresent("el punto del plano siguiendo la misma marca")
    }

    private fun session(): MaticaSession = MaticaSession(today = LocalDate(2026, 11, 5))

    /**
     * Monta la práctica y devuelve el plan de ESE MISMO `session`: las aserciones comparan contra lo
     * que la pantalla está mostrando, no contra un ejercicio regenerado con la misma semilla en otra
     * instancia.
     */
    private fun planEnPantalla(template: String): ExerciseRenderPlan {
        val session = session()
        val plan = requireNotNull(session.startPractice(template, 1L)) { "Sin plan para $template" }
        rule.setContent {
            MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Practice(template, 1L)))
        }
        rule.waitForIdle()
        return plan
    }

    private fun tab() = pressKey(KeyEvent.KEYCODE_TAB)

    private fun pressKey(code: Int) {
        instrumentation.sendKeyDownUpSync(code)
        instrumentation.waitForIdleSync()
        rule.waitForIdle()
    }

    private fun focusOn(desired: String) {
        var pasos = 0
        while (focusedLabel() != desired && pasos < MAX_TABS) {
            tab()
            pasos++
        }
        assertEquals(desired, focusedLabel(), "El Tab nunca llegó a «$desired» tras $pasos pulsaciones.")
    }

    private fun focusedLabel(): String =
        runCatching { rule.onAllNodes(isFocused()).fetchSemanticsNodes() }.getOrNull()
            ?.joinToString("+") { labelOf(it) }
            .orEmpty()

    private fun labelOf(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text }
            .orEmpty()

    private fun SemanticsNodeInteraction.assertPresent(what: String) {
        val found = runCatching { assertExists() }.isSuccess
        assertTrue(found, "$what no se pintó en el dispositivo.")
    }

    private fun SemanticsNodeInteraction.assertNotExists(what: String) {
        val leaked = runCatching { assertExists() }.isSuccess
        assertTrue(!leaked, "$what apareció sin que se la pidiera.")
    }

    private fun SemanticsNodeInteraction.assertIsDisplayedNow() {
        assertIsDisplayed()
    }

    private companion object {
        const val GEOMETRY = "rectangle-perimeter"
        const val TWO_STEP = "two-step-linear"

        /** Cifra de seguridad de la caminata: si el foco no llega, la aserción dice cuántos Tab se dieron. */
        const val MAX_TABS = 24
    }
}
