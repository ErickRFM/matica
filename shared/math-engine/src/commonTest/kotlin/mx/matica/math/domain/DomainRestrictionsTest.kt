package mx.matica.math.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import mx.matica.math.expr
import mx.matica.math.rational

class DomainRestrictionsTest {

    private fun restricciones(text: String): Set<Restriction> = DomainRestrictions.of(expr(text))

    @Test
    fun sinDenominadoresNoHayRestricciones() {
        assertTrue(restricciones("2(x+3)=14").isEmpty())
        assertTrue(restricciones("x^2 + 1").isEmpty())
    }

    @Test
    fun detectaVariableEnElDenominador() {
        val restricciones = restricciones("x/(x-2) = 3")
        assertContains(restricciones, Restriction.NotEqualTo("x", rational(2)))
        assertFalse(DomainRestrictions.allows(restricciones, "x", rational(2)))
        assertTrue(DomainRestrictions.allows(restricciones, "x", rational(0)))
    }

    @Test
    fun detectaMonomiosDelDenominador() {
        assertContains(restricciones("3/(2x) = 1"), Restriction.NotEqualTo("x", rational(0)))
        assertContains(restricciones("1/x^2"), Restriction.NotEqualTo("x", rational(0)))
    }

    @Test
    fun exponenteNegativoProhibeLaBase() {
        assertContains(restricciones("x^-2"), Restriction.NotEqualTo("x", rational(0)))
    }

    @Test
    fun unaFraccionNumericaCeroBloqueaTodo() {
        val restricciones = restricciones("5/0")
        assertEquals(1, restricciones.size)
        assertTrue(restricciones.single() is Restriction.Unresolved)
    }

    @Test
    fun dejaSinResolverLosDenominadoresQueNoPuedeAnular() {
        val restricciones = restricciones("1/(x^2+1) = 2")
        assertTrue(restricciones.single() is Restriction.Unresolved)
    }

    @Test
    fun cubreAmbosLadosDeLaEcuacion() {
        val restricciones = restricciones("(x+1)/x = 1/(x-3)")
        assertContains(restricciones, Restriction.NotEqualTo("x", rational(0)))
        assertContains(restricciones, Restriction.NotEqualTo("x", rational(3)))
    }
}
