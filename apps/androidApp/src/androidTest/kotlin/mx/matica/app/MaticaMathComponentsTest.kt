package mx.matica.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.design.theme.MaticaTheme
import mx.matica.math.MathEngine
import mx.matica.math.eval.EvalOutcome
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ParseOutcome
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.exercise.ExerciseSlot
import mx.matica.presentation.math.EquationView
import mx.matica.presentation.math.FractionView
import mx.matica.presentation.math.NumberLineModel
import mx.matica.presentation.math.NumberLineView
import mx.matica.presentation.math.equationAnnouncement
import mx.matica.presentation.math.fractionModel
import mx.matica.presentation.math.nearestTick
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los cuatro componentes matemáticos dibujados en el dispositivo a partir del plan de render que
 * produce el motor de ejercicios. Ningún valor de estas aserciones lo inventa la prueba: todos salen
 * del mismo `ExerciseRenderPlan` que consume la pantalla.
 */
@RunWith(AndroidJUnit4::class)
class MaticaMathComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private val engine = MathEngine()

    @Test
    fun laPracticaPintaEcuacionRectaYPlanoYSuFraccionSoloCuandoSePide() {
        val session = session()
        val plan = requireNotNull(session.startPractice(TEMPLATE, 1L))
        rule.setContent {
            MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Practice(TEMPLATE, 1L)))
        }
        rule.waitForIdle()

        rule.onNode(hasContentDescription(equationAnnouncement(plan.equationText))).assertIsDisplayed()
        rule.onNode(hasContentDescription("Marcador en ${plan.solution}", substring = true))
            .assertPresent("la recta numérica")
        rule.onNode(hasContentDescription("Gráfica en el plano de ${plan.variable} contra y", substring = true))
            .assertPresent("el plano cartesiano")

        // Antes de pedir la solución no puede haber ninguna fracción del resultado en pantalla.
        val mixedAnswer = fractionModel(plan.solution, asMixed = true).accessibleText
        rule.onNode(hasContentDescription(mixedAnswer)).assertAbsent("la solución antes de pedirla")

        revealSolution()
        rule.onNode(hasContentDescription(mixedAnswer)).assertShownAfterScroll()
    }

    @Test
    fun lasFlechasDeLaRectaNumericaSaltanDeMarcaEnMarcaExacta() {
        val session = session()
        val plan = requireNotNull(session.startPractice(TEMPLATE, 1L))
        rule.setContent {
            MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Practice(TEMPLATE, 1L)))
        }
        rule.waitForIdle()

        val marcado = markedValue()
        val ticks = plan.numberLine.orderedTicks
        val index = ticks.indexOf(nearestTick(marcado, ticks))
        assertTrue(index in 0 until ticks.lastIndex, "El marcador arrancó en la última marca: $marcado.")

        rule.onNode(hasContentDescription("Marca siguiente")).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(ticks[index + 1], markedValue(), "La flecha no cayó en la marca exacta de la derecha.")

        rule.onNode(hasContentDescription("Marca anterior")).performClick()
        rule.waitForIdle()
        assertEquals(marcado, markedValue(), "Regresar no devolvió el valor original.")
    }

    @Test
    fun lasPlantillasDelCatalogoSeDibujanConSusComponentes() {
        var route by mutableStateOf<MaticaRoute>(MaticaRoute.Practice(TEMPLATE, 1L))
        val session = session()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(route)) }

        MaticaRoute.KNOWN_TEMPLATES.sorted().forEach { template ->
            val plan = requireNotNull(session.startPractice(template, 1L)) { "Sin plan para $template" }
            rule.runOnIdle { route = MaticaRoute.Practice(template, 1L) }
            rule.waitForIdle()

            rule.onNode(hasContentDescription(equationAnnouncement(plan.equationText))).assertIsDisplayed()
            rule.onNode(hasText("Recta: dónde cae la solución")).assertPresent("la sección de la recta")
            if (plan.hasGraph) {
                rule.onNode(hasContentDescription("Gráfica en el plano", substring = true))
                    .assertPresent("el plano de $template")
            } else {
                rule.onNode(hasText("el plano se omite", substring = true, ignoreCase = true))
                    .assertPresent("el aviso de omisión")
            }
            // La ecuación que se pinta tiene que volver a parsear: la UI no dibuja basura.
            assertTrue(
                engine.parse(plan.equationText) is ParseOutcome.Success,
                "$template pintó una ecuación que el motor no reconoce: ${plan.equationText}",
            )

            val mixedAnswer = fractionModel(plan.solution, asMixed = true).accessibleText
            if (ExerciseSlot.ANSWER_FRACTION in plan.slots) {
                rule.onNode(hasContentDescription(mixedAnswer)).assertAbsent("la respuesta de $template")
                revealSolution()
                rule.onNode(hasContentDescription(mixedAnswer)).assertShownAfterScroll()
            } else {
                // El plan no pide fracción: la solución se comprueba en la recta, donde el marcador
                // tiene que caer en el valor exacto que el motor leyó del campo, y la fracción mixta
                // no aparece ni siquiera después de revelarla.
                revealSolution()
                rule.onNode(hasText("Marcado: ${plan.solution}")).assertPresent("la recta marcada en $template")
                rule.onNode(hasContentDescription(mixedAnswer)).assertAbsent("la fracción que $template no pide")
            }
        }
    }

    @Test
    fun laFraccionMixtaSeAnunciaDistintoQueLaImpropia() {
        val value = Rational.of(7, 2)
        val mixed = fractionModel(value, asMixed = true)
        val improper = fractionModel(value, asMixed = false)
        assertNotEquals(mixed.accessibleText, improper.accessibleText)

        rule.setContent {
            MaticaTheme(darkTheme = false) {
                Column {
                    Text("mixta")
                    FractionView(value = value, asMixed = true)
                    Text("impropia")
                    FractionView(value = value, asMixed = false)
                }
            }
        }
        rule.waitForIdle()
        rule.onNode(hasContentDescription(mixed.accessibleText)).assertIsDisplayed()
        rule.onNode(hasContentDescription(improper.accessibleText)).assertIsDisplayed()
    }

    @Test
    fun laRectaNumericaAnunciaSuModeloYSuMarcador() {
        val model = NumberLineModel(
            from = Rational.of(-1, 1),
            to = Rational.ONE,
            ticks = listOf(Rational.of(-1, 1), Rational.ZERO, Rational.ONE),
            markers = listOf(Rational.of(1, 2)),
        )
        rule.setContent {
            MaticaTheme(darkTheme = false) { NumberLineView(model = model, selected = Rational.ZERO) }
        }
        rule.waitForIdle()

        rule.onNode(hasContentDescription("Recta numérica de -1 a 1, con 3 marcas", substring = true))
            .assertPresent("el modelo de la recta")
        rule.onNode(hasContentDescription("Marcador en 0", substring = true)).assertPresent("el marcador")
        rule.onNode(hasText("De -1 a 1")).assertIsDisplayed()
    }

    @Test
    fun laEcuacionSeAnunciaComoLaLeeUnLectorDePantalla() {
        val session = session()
        val plan = requireNotNull(session.startPractice(TEMPLATE, 1L))
        rule.setContent {
            MaticaTheme(darkTheme = false) { EquationView(text = plan.equationText, variable = plan.variable) }
        }
        rule.waitForIdle()
        rule.onNode(hasContentDescription(equationAnnouncement(plan.equationText))).assertIsDisplayed()
    }

    private fun session(): MaticaSession = MaticaSession(today = LocalDate(2026, 11, 5))

    private fun markedValue(): Rational {
        val node = rule.onNode(hasText("Marcado:", substring = true)).fetchSemanticsNode()
        val raw = node.config.getOrNull(SemanticsProperties.Text)?.first()?.text.orEmpty()
        val value = engine.evaluate(raw.substringAfter("Marcado:").trim())
        return (value as? EvalOutcome.Value)?.value
            ?: throw AssertionError("La pantalla no muestra un valor legible: «$raw»")
    }

    private fun SemanticsNodeInteraction.assertPresent(what: String) {
        val found = runCatching { assertExists() }.isSuccess
        assertTrue(found, "$what no se pintó en el dispositivo.")
    }

    private fun SemanticsNodeInteraction.assertAbsent(what: String) {
        val leaked = runCatching { assertExists() }.isSuccess
        assertTrue(!leaked, "$what apareció sin que el estudiante la pidiera.")
    }

    /** El componente está montado pero puede quedar bajo el pliegue: se acerca a la vista y ahí se pinta. */
    private fun SemanticsNodeInteraction.assertShownAfterScroll() {
        performScrollTo()
        assertIsDisplayed()
    }

    private fun revealSolution() {
        val button = rule.onNode(hasText("Mostrar la solución"))
        button.performScrollTo()
        button.performClick()
        rule.waitForIdle()
    }

    private companion object {
        const val TEMPLATE = "two-step-linear"
    }
}
