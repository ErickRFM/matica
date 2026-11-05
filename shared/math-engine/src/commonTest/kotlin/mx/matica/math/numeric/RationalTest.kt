package mx.matica.math.numeric

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RationalTest {

    @Test
    fun guardaFormaCanonica() {
        val media = Rational.of(4, 8)
        assertEquals(BigInteger.ONE, media.numerator)
        assertEquals(BigInteger.fromLong(2), media.denominator)
    }

    @Test
    fun normalizaElSignoAlNumerador() {
        val valor = Rational.of(3, -6)
        assertEquals(Rational.of(-1, 2), valor)
        assertEquals(BigInteger.fromLong(2), valor.denominator)
    }

    @Test
    fun sumaFraccionesSinRedondeo() {
        assertEquals(Rational.of(5, 6), Rational.of(1, 2) + Rational.of(1, 3))
        assertEquals(Rational.of(1, 6), Rational.of(1, 2) - Rational.of(1, 3))
    }

    @Test
    fun productoYCocienteExactos() {
        assertEquals(Rational.of(1, 6), Rational.of(1, 2) * Rational.of(1, 3))
        assertEquals(Rational.of(3, 2), Rational.of(1, 2) / Rational.of(1, 3))
    }

    @Test
    fun divisionEntreCeroFalla() {
        assertFailsWith<IllegalArgumentException> { Rational.of(1, 2) / Rational.ZERO }
        assertFailsWith<IllegalArgumentException> { Rational.of(5, 0) }
        assertFailsWith<IllegalArgumentException> { Rational.ZERO.reciprocal() }
    }

    @Test
    fun desdeDecimalesExactos() {
        assertEquals(Rational.of(3, 4), Rational.fromDecimal("0.75"))
        assertEquals(Rational.of(-3, 2), Rational.fromDecimal("-1.5"))
        assertEquals(Rational.of(123456, 100000), Rational.fromDecimal("1.23456"))
        assertFailsWith<IllegalArgumentException> { Rational.fromDecimal("1.2.3") }
    }

    @Test
    fun potenciasConExponenteNegativo() {
        assertEquals(Rational.of(1, 8), Rational.of(2, 1).pow(-3))
        assertEquals(Rational.ONE, Rational.of(7, 3).pow(0))
        assertFailsWith<IllegalArgumentException> { Rational.ZERO.pow(-1) }
    }

    @Test
    fun comparaYOrdena() {
        assertEquals(-1, Rational.of(1, 3).compareTo(Rational.of(1, 2)))
        assertEquals(Rational.of(1, 2), Rational.of(2, 4))
        assertEquals(Rational.of(1, 2).hashCode(), Rational.of(2, 4).hashCode())
    }

    @Test
    fun representaComoTextoLegible() {
        assertEquals("3/4", Rational.of(3, 4).toString())
        assertEquals("7", Rational.of(7, 1).toString())
        assertEquals("-1", Rational.MINUS_ONE.toString())
    }

    @Test
    fun mcmDeDenominadores() {
        val mcm = Rational.lcmOfDenominators(listOf(Rational.of(1, 4), Rational.of(1, 6), Rational.of(1, 10)))
        assertEquals(BigInteger.fromLong(60), mcm)
    }
}
