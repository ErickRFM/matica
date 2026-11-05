package mx.matica.math.poly

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.eq
import mx.matica.math.expr
import mx.matica.math.poly
import mx.matica.math.rational
import mx.matica.math.terms

class NormalizerTest {

    @Test
    fun colapsaTerminosSemejantes() {
        assertEquals(mapOf("x" to rational(4)), poly("2x + 3x - x").terms())
        assertEquals(mapOf("1" to rational(6)), poly("2 + 4").terms())
    }

    @Test
    fun expandeProductosYDistribucion() {
        assertEquals(mapOf("x" to rational(2), "1" to rational(6)), poly("2(x+3)").terms())
        assertEquals(
            mapOf("x^2" to rational(1), "x" to rational(5), "1" to rational(6)),
            poly("(x+2)(x+3)").terms(),
        )
    }

    @Test
    fun dosFormasIgualesDanElMismoPolinomio() {
        assertEquals(poly("2(x+3)"), poly("2x+6"))
        assertEquals(poly("(x+1)^2"), poly("x^2+2x+1"))
        assertEquals(poly("x^2 - 1"), poly("(x-1)(x+1)"))
        assertEquals(poly("a+b"), poly("b+a"))
    }

    @Test
    fun aplicaElSignoA_TodosLosTerminosDelParentesis() {
        assertEquals(mapOf("x" to rational(-1), "1" to rational(1)), poly("-(x-1)").terms())
        assertEquals(mapOf("x" to rational(-2), "1" to rational(-1)), poly("3 - 2(x+2)").terms())
    }

    @Test
    fun divideCuandoElDenominadorEsConstante() {
        assertEquals(mapOf("x" to rational(1), "1" to rational(3)), poly("(2x+6)/2").terms())
        assertEquals(mapOf("x" to rational(1, 2)), poly("x/2").terms())
        assertEquals(mapOf("x" to rational(1)), poly("x^3/x^2").terms())
    }

    @Test
    fun seBloqueaAnteUnaFraccionAlgebraicaNoExacta() {
        val blocked = Normalizer.of(expr("(x+1)/x"))
        assertIs<PolyOutcome.Blocked>(blocked)
        assertTrue(blocked.reason.contains("fracción algebraica"), blocked.reason)

        val sumaVariable = Normalizer.of(expr("(x+1)/(x+2)"))
        assertIs<PolyOutcome.Blocked>(sumaVariable)
        assertTrue(sumaVariable.reason.contains("factorizar"), sumaVariable.reason)
    }

    @Test
    fun seBloqueaEnDivisionEntreCero() {
        val blocked = Normalizer.of(expr("x/0"))
        assertIs<PolyOutcome.Blocked>(blocked)
        assertTrue(blocked.reason.contains("cero"), blocked.reason)
    }

    @Test
    fun seBloqueaConExponentesFueraDeAlcance() {
        assertIs<PolyOutcome.Blocked>(Normalizer.of(expr("x^0.5")))
        assertIs<PolyOutcome.Blocked>(Normalizer.of(expr("x^y")))
        assertIs<PolyOutcome.Blocked>(Normalizer.of(expr("x^-2")))
        assertIs<PolyOutcome.Blocked>(Normalizer.of(expr("(x+1)^9")))
    }

    @Test
    fun conviertePorcentajesAFactory() {
        assertEquals(mapOf("1" to rational(20, 100)), poly("20%").terms())
        assertEquals(rational(3, 10), poly("20%+10%").constantTerm)
    }

    @Test
    fun formaCanonicaDeEcuacionEsLMenosR() {
        val canonical = Normalizer.ofEquation(eq("2x+6=14"))
        assertIs<PolyOutcome.Value>(canonical)
        assertEquals(mapOf("x" to rational(2), "1" to rational(-8)), canonical.polynomial.terms())
    }

    @Test
    fun gradoVariablesYCeroCanónico() {
        assertEquals(2, poly("x^2+y").degree)
        assertEquals(setOf("x", "y"), poly("x^2+y").variables)
        assertTrue(poly("0x + 3").isConstant)
        assertTrue(poly("x").isMonomial)
        assertTrue(poly("x - x").isZero)
        assertEquals(rational(0), poly("x-x").constantTerm)
    }

    @Test
    fun proporcionalidadDetectaEcuacionesEquivalentes() {
        assertEquals(rational(2), poly("2x-4").proportionalityFactorTo(poly("4x-8")))
        assertEquals(rational(1), poly("x-3").proportionalityFactorTo(poly("3-x").let { -it }))
        assertEquals(null, poly("x-3").proportionalityFactorTo(poly("x-4")))
        assertEquals(null, poly("x").proportionalityFactorTo(poly("x+y")))
        assertEquals(rational(1), poly("0").proportionalityFactorTo(poly("0")))
    }
}
