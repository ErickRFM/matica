package mx.matica.math.eval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import mx.matica.math.expr
import mx.matica.math.numeric.Rational
import mx.matica.math.rational

class EvaluatorTest {

    private fun valor(text: String, entorno: Map<String, Rational> = emptyMap()): Rational {
        val outcome = Evaluator.evaluate(expr(text), entorno)
        assertIs<EvalOutcome.Value>(outcome, "'$text' no evaluó a un número")
        return outcome.value
    }

    @Test
    fun evaluaAritmeticaExacta() {
        assertEquals(rational(14), valor("2 + 3 * 4"))
        assertEquals(rational(36), valor("(2 + 3 + 1)^2"))
        assertEquals(rational(1, 12), valor("1/3 - 1/4"))
        assertEquals(rational(-6), valor("2 - 2 * (1 + 3)"))
    }

    @Test
    fun ceroPuntoUnoNoEsCeroPuntoUnoMasCeroPuntoDos() {
        assertEquals(rational(3, 10), valor("0.1 + 0.2"))
    }

    @Test
    fun usaElEntornoParaLasVariables() {
        assertEquals(rational(14), valor("2(x+3)", mapOf("x" to rational(4))))
        assertEquals(rational(5), valor("x + y", mapOf("x" to rational(2), "y" to rational(3))))
    }

    @Test
    fun reportaVariableFaltante() {
        val outcome = Evaluator.evaluate(expr("x + 3"))
        assertIs<EvalOutcome.MissingVariable>(outcome)
        assertEquals("x", outcome.variable)
    }

    @Test
    fun senalaDivisionEntreCeroComoIndefinido() {
        val outcome = Evaluator.evaluate(expr("5/0"))
        assertIs<EvalOutcome.Undefined>(outcome)
        assertTrue(outcome.reason.contains("división entre cero"), outcome.reason)

        val anulaDenominador = Evaluator.evaluate(expr("x/(x-2)"), mapOf("x" to rational(2)))
        assertIs<EvalOutcome.Undefined>(anulaDenominador)
    }

    @Test
    fun ceroElevadoANegativoIndefinido() {
        assertIs<EvalOutcome.Undefined>(Evaluator.evaluate(expr("0^-1")))
    }

    @Test
    fun exponenteNoEnteroEsNoSoportado() {
        val outcome = Evaluator.evaluate(expr("2^0.5"))
        assertIs<EvalOutcome.Unsupported>(outcome)
        assertTrue(outcome.reason.contains("no es entero"), outcome.reason)
    }

    @Test
    fun potenciaExactaConBaseFraccionaria() {
        assertEquals(rational(27, 8), valor("(3/2)^3"))
        assertEquals(rational(4, 9), valor("(3/2)^-2"))
        assertEquals(rational(1), valor("7^0"))
    }

    @Test
    fun porcentajeComoDivisionEntreCien() {
        assertEquals(rational(1, 5), valor("20%"))
        assertEquals(rational(30), valor("20% * 150"))
        assertEquals("20%", mx.matica.math.printer.ExprPrinter.render(expr("20%")))
    }

    @Test
    fun juzgaIgualdades() {
        val ecuacion = mx.matica.math.eq("2(x+3)=14")
        assertEquals(true, Evaluator.equalityHolds(ecuacion, mapOf("x" to rational(4))))
        assertEquals(false, Evaluator.equalityHolds(ecuacion, mapOf("x" to rational(5))))
        assertNull(Evaluator.equalityHolds(ecuacion, emptyMap()))
    }

    @Test
    fun sustituyeSoloLaVariablePedida() {
        val sustituida = Evaluator.substitute(expr("x^2 + 2x"), "x", rational(3))
        assertEquals(rational(15), Evaluator.evaluate(sustituida).let { (it as EvalOutcome.Value).value })
        val mixta = Evaluator.substitute(expr("x + y"), "x", rational(1))
        assertIs<EvalOutcome.MissingVariable>(Evaluator.evaluate(mixta))
    }
}
