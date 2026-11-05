package mx.matica.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.analysis.StepVerdict
import mx.matica.math.domain.Restriction
import mx.matica.math.eval.EvalOutcome
import mx.matica.math.hints.HintLevel
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ParseOutcome
import mx.matica.math.solve.SolveOutcome
import mx.matica.math.transform.RuleId
import mx.matica.math.verify.VerificationOutcome

/** La fachada es la única puerta que ve la app: aquí se prueba el flujo completo de un intento. */
class MathEngineTest {

    private val motor = MathEngine()

    @Test
    fun analizaElProblemaCompletoDelPromptPasoAPaso() {
        val distribuir = motor.checkStep("2(x+3)=14", "2x+6=14")
        assertIs<StepVerdict.Valid>(distribuir)
        assertEquals(RuleId.DISTRIBUTE, distribuir.rule)

        val despejar = motor.checkStep("2x+6=14", "2x=8")
        assertIs<StepVerdict.Valid>(despejar)
        assertEquals(RuleId.SUBTRACT_FROM_BOTH_SIDES, despejar.rule)

        val aislar = motor.checkStep("2x=8", "x=4")
        assertIs<StepVerdict.Valid>(aislar)
        assertEquals(RuleId.DIVIDE_BOTH_SIDES_BY, aislar.rule)

        assertIs<StepVerdict.Invalid>(motor.checkStep("2(x+3)=14", "x+3=14"))
    }

    @Test
    fun resuelveVerificaYRegistraHabilidades() {
        val solucion = motor.solve("2(x+3)=14")
        assertIs<SolveOutcome.Unique>(solucion)
        assertEquals(Rational.of(4, 1), solucion.value)

        val verificacion = motor.verifySolution("2(x+3)=14", "x", solucion.value)
        assertIs<VerificationOutcome.Satisfies>(verificacion)

        val paso = motor.checkStep("2(x+3)=14", "2x+6=14")
        assertIs<StepVerdict.Valid>(paso)
        assertContains(paso.skills, mx.matica.math.skills.SkillId.DISTRIBUTIVE_PROPERTY)
    }

    @Test
    fun evalúaYReportaRestriccionesDesdeTexto() {
        assertEquals(EvalOutcome.Value(Rational.of(5, 6)), motor.evaluate("1/2 + 1/3"))
        assertIs<EvalOutcome.Undefined>(motor.evaluate("1/0"))
        assertIs<ParseOutcome.Failure>(motor.parse("2(x+3"))

        val restricciones = motor.restrictions("x/(x-2)=1")
        assertContains(restricciones, Restriction.NotEqualTo("x", Rational.of(2, 1)))
    }

    @Test
    fun daPistasSinRevelarLaRespuestaYLuegoLaLinea() {
        val pistas = motor.hints("3x + 5 = 20")
        assertEquals(3, pistas.size)
        assertTrue(pistas.none { "x = 5" in it.text })

        val conLinea = motor.hints("3x + 5 = 20", upTo = HintLevel.SHOW_LINE)
        assertEquals(4, conLinea.size)
        assertContains(conLinea.last().text, "3x = 15")
    }

    @Test
    fun noAceptaEntradasAmbiguasComoSiFueranValidas() {
        assertIs<ParseOutcome.Failure>(motor.parse("2 3"))
        assertIs<ParseOutcome.Failure>(motor.parse("1.2.3"))
        assertIs<ParseOutcome.Failure>(motor.parse("x = = 2"))
    }
}
