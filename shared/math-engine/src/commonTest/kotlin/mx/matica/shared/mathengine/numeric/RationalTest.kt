package mx.matica.shared.mathengine.numeric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RationalTest {
    @Test
    fun `of normaliza signo y mcd`() {
        assertEquals(Rational(1, 2), Rational.of(2, 4))
        assertEquals(Rational(-1, 2), Rational.of(1, -2))
        assertEquals(Rational(0, 1), Rational.of(0, 7))
    }

    @Test
    fun `denominador cero es rechazado`() {
        assertFailsWith<IllegalArgumentException> { Rational.of(1, 0) }
    }

    @Test
    fun `suma fracciones con resultado exacto`() {
        assertEquals(Rational(5, 6), Rational.of(1, 2) + Rational.of(1, 3))
        assertEquals(Rational(1, 6), Rational.of(1, 2) - Rational.of(1, 3))
    }

    @Test
    fun `producto y cociente exactos`() {
        assertEquals(Rational(1, 6), Rational.of(1, 2) * Rational.of(1, 3))
        assertEquals(Rational(3, 2), Rational.of(1, 2) / Rational.of(1, 3))
        assertFailsWith<IllegalArgumentException> { Rational.of(1, 2) / Rational.of(0, 3) }
    }

    @Test
    fun `comparacion respeta valor racional`() {
        assertEquals(true, Rational.of(3, 4) > Rational.of(2, 3))
        assertEquals(0, Rational.of(2, 4).compareTo(Rational.of(1, 2)))
    }
}
