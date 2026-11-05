package mx.matica.math.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.ast.Expr
import mx.matica.math.ast.variables
import mx.matica.math.eval.EvalOutcome
import mx.matica.math.eval.Evaluator
import mx.matica.math.numeric.Rational
import mx.matica.math.printer.ExprPrinter

class ExprParserTest {

    private fun parse(text: String): Expr {
        val outcome = ExprParser(text).parse()
        assertIs<ParseOutcome.Success>(outcome, "Debería poder leer '$text': ${(outcome as? ParseOutcome.Failure)?.reason}")
        return outcome.expression
    }

    private fun fallo(text: String): ParseOutcome.Failure {
        val outcome = ExprParser(text).parse()
        assertIs<ParseOutcome.Failure>(outcome, "'$text' no debía ser válido")
        return outcome
    }

    @Test
    fun leeNumerosVariablesYOperacionesBasicas() {
        assertEquals(Expr.Const(Rational.fromLong(14)), parse("14"))
        assertEquals(Expr.Var("x"), parse("x"))
        assertIs<Expr.Sum>(parse("x + 3"))
        assertIs<Expr.Quotient>(parse("1/2"))
        assertEquals(setOf("x"), parse("x + 3").variables())
    }

    @Test
    fun respetaLaJerarquiaDeOperaciones() {
        assertEquals("2 + 3*4", ExprPrinter.render(parse("2 + 3 * 4")))
        assertEquals("(2 + 3)*4", ExprPrinter.render(parse("(2 + 3) * 4")))
        assertEquals("2*3^2", ExprPrinter.render(parse("2 * 3^2")))
        assertEquals("2^3^2", ExprPrinter.render(parse("2^3^2")))
        assertEquals("x^2^3", ExprPrinter.render(parse("x^2^3")))
    }

    @Test
    fun laPotenciaVenceAlProductoImplicito() {
        assertEquals("2x^2", ExprPrinter.render(parse("2x^2")))
        assertEquals("-x^2", ExprPrinter.render(parse("-x^2")))
    }

    @Test
    fun reconoceMultiplicacionImplicita() {
        assertIs<Expr.Equals>(parse("2(x+3)=14"))
        assertEquals("2(x + 3) = 14", ExprPrinter.render(parse("2(x+3)=14")))
        assertEquals("2x", ExprPrinter.render(parse("2x")))
        assertEquals("x(x + 1)", ExprPrinter.render(parse("x(x+1)")))
    }

    @Test
    fun leePotenciasYPorcentajes() {
        assertIs<Expr.Power>(parse("x^2"))
        val exponenteDecimal = parse("2^1.5") as Expr.Power
        assertEquals(Expr.Const(Rational.of(3, 2)), exponenteDecimal.exponent)
        assertIs<Expr.Percent>(parse("20%"))
        assertEquals("20%", ExprPrinter.render(parse("20%")))
    }

    @Test
    fun manejaSignosUnarios() {
        assertEquals("-3", ExprPrinter.render(parse("-3")))
        assertEquals("x - 3", ExprPrinter.render(parse("x-3")))
        assertEquals("-x + 2", ExprPrinter.render(parse("-x+2")))
        assertEquals("2 - -3", ExprPrinter.render(parse("2--3")))
    }

    @Test
    fun detectaSintaxisInvalidaConPosicionYSugerencia() {
        val parentesis = fallo("2(x+3")
        assertTrue(parentesis.reason.contains("paréntesis"), parentesis.reason)
        assertTrue(parentesis.suggestion.isNotBlank())

        val dosNumeros = fallo("1 1/2")
        assertTrue(dosNumeros.reason.contains("Dos números"), dosNumeros.reason)

        assertTrue(fallo("2 + + +").reason.isNotBlank())
        assertTrue(fallo("=14").reason.isNotBlank())
        assertTrue(fallo("").reason.contains("vacía"))
        assertTrue(fallo("2(x+3)=14=7").reason.contains("igual"))
        assertTrue(fallo("2 3").reason.contains("Dos números"))
    }

    @Test
    fun rechazaExpresionesIncompletas() {
        assertTrue(fallo("x +").reason.isNotBlank())
        assertTrue(fallo("(x+2))").reason.isNotBlank())
        assertTrue(fallo("2**3").reason.isNotBlank())
    }

    @Test
    fun preservaDecimalesSinPerderPrecision() {
        val suma = parse("0.1 + 0.2")
        assertIs<Expr.Sum>(suma)
        assertEquals(2, suma.terms.size)
        val resultado = Evaluator.evaluate(suma)
        assertIs<EvalOutcome.Value>(resultado)
        assertEquals(Rational.of(3, 10), resultado.value)
    }

    @Test
    fun leeIdentificadoresConSubrayado() {
        assertEquals("x_1", ExprPrinter.render(parse("x_1")))
    }

    @Test
    fun admiteVariablesConEnie() {
        assertEquals("año", ExprPrinter.render(parse("año")))
    }

    @Test
    fun reportaLaPosicionDelError() {
        assertEquals(6, fallo("2 + 3 @").position)
        assertTrue(fallo("2 + 3 @").reason.contains("Carácter no soportado"))
    }
}
