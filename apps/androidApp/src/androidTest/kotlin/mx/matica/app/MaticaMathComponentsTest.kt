package mx.matica.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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
import mx.matica.presentation.math.figureCandidates
import mx.matica.presentation.math.fractionModel
import mx.matica.presentation.math.nearestTick
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los componentes matemáticos dibujados en el dispositivo a partir del plan de render que produce el
 * motor de ejercicios. Ningún valor de estas aserciones lo inventa la prueba: todos salen del mismo
 * `ExerciseRenderPlan` que consume la pantalla.
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
    fun laFiguraSeDibujaConLosLadosExactosDeLaEcuacion() {
        val session = session()
        val plan = requireNotNull(session.startPractice(GEOMETRY_TEMPLATE, 1L)) { "Sin plan geométrico" }
        val figure = requireNotNull(plan.figure) { "La plantilla geométrica no dejó figura: ${plan.equationText}" }
        rule.setContent {
            MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Practice(GEOMETRY_TEMPLATE, 1L)))
        }
        rule.waitForIdle()

        rule.onNode(hasText("Figura: ${figure.totalLabelEs} dibujado")).assertPresent("la sección de la figura")
        rule.onNode(
            hasText("Se pide ${figure.goalEs} · con lado ${plan.solution} tienes ${figure.totalLabelEs} ${figure.total}"),
        ).assertPresent("la lectura del lado y su total")
        rule.onNode(hasContentDescription("Rectángulo de lado conocido ${figure.knownSide}", substring = true))
            .assertPresent("la figura anunciada al lector de pantalla")
        rule.onNode(hasText("Coincide: ese lado es el que pide la ecuación", substring = true))
            .assertPresent("el aviso de que la hipótesis ya cuadra")
        assertTrue(
            engine.parse(plan.equationText) is ParseOutcome.Success && figure.totalAt(plan.solution) == figure.total,
            "La figura pintada no cierra con su ecuación: ${plan.equationText}",
        )
    }

    @Test
    fun lasFlechasDeLaFiguraMuevenElLadoPorCandidatosExactos() {
        val session = session()
        val plan = requireNotNull(session.startPractice(GEOMETRY_TEMPLATE, 1L)) { "Sin plan geométrico" }
        val figure = requireNotNull(plan.figure)
        val candidates = figureCandidates(plan.numberLine)
        rule.setContent {
            MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Practice(GEOMETRY_TEMPLATE, 1L)))
        }
        rule.waitForIdle()

        val start = candidates.indexOfFirst { it == plan.solution }
        assertTrue(start in 0 until candidates.lastIndex, "La solución $plan.solution no quedó en un candidato alcanzable: $candidates")

        rule.onNode(hasContentDescription("Lado siguiente")).performScrollTo().performClick()
        rule.waitForIdle()
        val advanced = candidates[start + 1]
        rule.onNode(hasText("Lado $advanced · ${figure.totalLabelEs} ${figure.totalAt(advanced)}"))
            .assertPresent("el lado siguiente, con su total exacto")
        rule.onNode(hasText("Coincide: ese lado es el que pide la ecuación", substring = true))
            .assertAbsentNow("la coincidencia con un lado que ya no es el de la ecuación")

        rule.onNode(hasContentDescription("Lado anterior")).performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Lado ${plan.solution} · ${figure.totalLabelEs} ${figure.total}"))
            .assertPresent("regresar al valor de la ecuación")
    }

    @Test
    fun unaEcuacionQueNoSeLeeComoRectanguloNoDibujaFigura() {
        val session = session()
        val plan = requireNotNull(session.startPractice("integer-ops", 1L)) { "Sin plan de enteros" }
        assertNull(plan.figure, "${plan.equationText} no es un rectángulo y aun así salió figura.")
        rule.setContent {
            MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Practice("integer-ops", 1L)))
        }
        rule.waitForIdle()
        rule.onNode(hasText("Figura:", substring = true)).assertAbsentNow("una sección de geometría sin datos que la sostengan")
    }

    @Test
    fun arrastrarElPlanoMueveElPuntoSobreLaRectaConValoresExactos() {
        val session = session()
        val plan = requireNotNull(session.startPractice(TEMPLATE, 1L)) { "Sin plan con plano" }
        assertTrue(plan.hasGraph, "$TEMPLATE tenía que traer plano para poder arrastrar su punto.")
        val line = plan.lines.first()
        val candidates = plan.numberLine.orderedTicks
        rule.setContent {
            MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Practice(TEMPLATE, 1L)))
        }
        rule.waitForIdle()

        val graph = rule.onNode(hasContentDescription("Gráfica en el plano", substring = true))
        graph.assertPresent("el plano")
        rule.onNode(hasText("${plan.variable} = ${plan.solution} · y = ${line.slope * plan.solution + line.intercept}"))
            .assertPresent("el punto arrancando en la intersección de las dos rectas")

        graph.performScrollTo()
        graph.performTouchInput {
            down(Offset(left + width * 0.2f, center.y))
            moveTo(Offset(left + width * 0.75f, center.y))
            up()
        }
        rule.waitForIdle()

        val readout = textWith("· y =")
        val movedX = valueOf(readout.substringAfter("${plan.variable} =").substringBefore("·").trim())
        assertNotEquals(plan.solution, movedX, "El arrastre dejó el punto donde estaba.")
        assertTrue(movedX in candidates, "El arrastre terminó en un valor que no es marca del plano: «$readout»")
        assertEquals(
            "${plan.variable} = $movedX · y = ${line.slope * movedX + line.intercept}",
            readout,
            "El punto se salió de la recta: el lecturado no coincide con la función.",
        )
        rule.onNode(hasContentDescription("Punto en la recta: ${plan.variable} = $movedX", substring = true))
            .assertPresent("el anuncio del punto movido")
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

    private fun markedValue(): Rational = valueOf(textWith("Marcado:").substringAfter("Marcado:").trim())

    /** El motor lee lo que la pantalla escribió: una aserción sobre texto que nadie sabe parsear no prueba nada. */
    private fun valueOf(expression: String): Rational =
        (engine.evaluate(expression) as? EvalOutcome.Value)?.value
            ?: throw AssertionError("La pantalla no muestra un valor legible: «$expression»")

    private fun textWith(prefix: String): String {
        val raw = rule.onNode(hasText(prefix, substring = true)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)?.first()?.text.orEmpty()
        assertTrue(raw.contains(prefix), "La pantalla no muestra ningún texto con «$prefix»: «$raw»")
        return raw
    }

    private fun SemanticsNodeInteraction.assertPresent(what: String) {
        val found = runCatching { assertExists() }.isSuccess
        assertTrue(found, "$what no se pintó en el dispositivo.")
    }

    private fun SemanticsNodeInteraction.assertAbsent(what: String) {
        val leaked = runCatching { assertExists() }.isSuccess
        assertTrue(!leaked, "$what apareció sin que el estudiante la pidiera.")
    }

    private fun SemanticsNodeInteraction.assertAbsentNow(what: String) {
        val leaked = runCatching { assertExists() }.isSuccess
        assertTrue(!leaked, "$what seguía en pantalla aunque el estado ya no la sostiene.")
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
        const val GEOMETRY_TEMPLATE = "rectangle-perimeter"
    }
}
