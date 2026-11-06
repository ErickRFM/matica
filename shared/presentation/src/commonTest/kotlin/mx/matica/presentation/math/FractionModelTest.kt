package mx.matica.presentation.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import mx.matica.math.numeric.Rational

/**
 * El mapeo `Rational` → lo que se pinta es la parte fácil de equivocar del componente: una barra
 * sobre un entero o un signo comiéndose el numerador enseñan una notación que no existe.
 */
class FractionModelTest {

    @Test
    fun fraccionPropiaSeSeparaEnDosEnteros() {
        val model = fractionModel(Rational.of(3, 4))
        assertEquals("3", model.numeratorText)
        assertEquals("4", model.denominatorText)
        assertEquals("", model.signText)
        assertEquals(null, model.wholeText)
        assertTrue(model.isFraction)
    }

    @Test
    fun enteroNoDibujaBarra() {
        val model = fractionModel(Rational.of(6, 3))
        assertFalse(model.isFraction)
        assertEquals("2", model.numeratorText)
        assertEquals(null, model.denominatorText)
        assertEquals("2", model.accessibleText)
    }

    @Test
    fun ceroSeEscribeComoCero() {
        val model = fractionModel(Rational.ZERO)
        assertEquals("0", model.numeratorText)
        assertEquals(null, model.denominatorText)
    }

    @Test
    fun elSignoVaFueraDelNumerador() {
        val model = fractionModel(Rational.of(-3, 4))
        assertEquals("−", model.signText)
        assertEquals("3", model.numeratorText)
        assertEquals("negativo 3 sobre 4", model.accessibleText)
    }

    @Test
    fun numeroMixtoSeparaEnteroYFraccion() {
        val model = fractionModel(Rational.of(7, 3), asMixed = true)
        assertEquals("2", model.wholeText)
        assertEquals("1", model.numeratorText)
        assertEquals("3", model.denominatorText)
        assertEquals("2 1 sobre 3", model.accessibleText)
    }

    @Test
    fun numeroMixtoNoEscribeUnCeroDelante() {
        val model = fractionModel(Rational.of(1, 3), asMixed = true)
        assertEquals(null, model.wholeText)
        assertEquals("1 sobre 3", model.accessibleText)
    }

    @Test
    fun elNumeroMixtoRepresentaElMismoValor() {
        val sevenThirds = Rational.of(7, 3)
        val mixed = fractionModel(sevenThirds, asMixed = true)
        val whole = checkNotNull(mixed.wholeText).toLong()
        val numerator = checkNotNull(mixed.numeratorText).toLong()
        val denominator = checkNotNull(mixed.denominatorText).toLong()
        assertEquals(sevenThirds, Rational.of(whole, 1) + Rational.of(numerator, denominator))
    }

    @Test
    fun celdasDeLaBarraUsanElDenominadorReducido() {
        val cells = stripCells(Rational.of(2, 4))
        assertEquals(2, cells.parts)
        assertEquals(1, cells.filledParts)
        assertFalse(cells.improper)
        assertTrue(cells.drawable)
    }

    @Test
    fun fraccionImpropiaNoSeDibujaComoBarraDeUnEntero() {
        val cells = stripCells(Rational.of(5, 4))
        assertTrue(cells.improper)
        assertFalse(cells.drawable)
    }

    @Test
    fun denominadorGrandeSaleDeLaBarra() {
        assertFalse(stripCells(Rational.of(1, 60), maxParts = 12).drawable)
        assertTrue(stripCells(Rational.of(1, 6), maxParts = 12).drawable)
    }

    @Test
    fun ceroDibujaUnaBarraVacia() {
        val cells = stripCells(Rational.ZERO)
        assertEquals(1, cells.parts)
        assertEquals(0, cells.filledParts)
        assertTrue(cells.drawable)
    }
}
