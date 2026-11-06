package mx.matica.presentation.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mx.matica.math.MathEngine

/**
 * La tokenización y la lectura en voz alta son lógica pura: si se rompe, la pantalla sigue
 * compilando y muestra una ecuación mal dicha. Estas pruebas son la única red que queda.
 */
class EquationModelTest {

    private val samples = listOf(
        "2*x + 5 = 11",
        "3*(x - 2) = 6",
        "x/2 + 1/3 = 5/6",
        "x^2 - 4 = 0",
        "20%*x = 7",
        "-(x + 1) = 3",
        "a*b*c*d",
        "12.5 + x",
        "",
        "   ",
    )

    @Test
    fun losFragmentosReconstruyenElTextoSinPerderNada() {
        samples.forEach { text ->
            assertEquals(text, equationRuns(text, "x").joinToString(separator = "") { it.text }, "en «$text»")
        }
    }

    @Test
    fun laVariableSeMarcaSoloCuandoEsLaPalabraCompleta() {
        val runs = equationRuns("2*x + 5 = 11", "x")
        assertEquals(1, runs.count { it.role == EquationRunRole.VARIABLE })
        assertTrue(runs.none { it.role == EquationRunRole.VARIABLE && it.text != "x" })
    }

    @Test
    fun unaXQueFormaOtroNombreNoEsLaIncognita() {
        assertEquals(0, equationRuns("2*xy = 6", "x").count { it.role == EquationRunRole.VARIABLE })
        assertEquals(1, equationRuns("2*xy = 6", "xy").count { it.role == EquationRunRole.VARIABLE })
    }

    @Test
    fun sinVariableNadaSeResalta() {
        assertTrue(equationRuns("2*x + 5", null).none { it.role == EquationRunRole.VARIABLE })
    }

    @Test
    fun operadoresYParentesisTienenSuRol() {
        val roles = equationRuns("(x + 1) = 2", "x").map { it.role }
        assertTrue(EquationRunRole.GROUPING in roles)
        assertTrue(EquationRunRole.OPERATOR in roles)
    }

    @Test
    fun multiplicacionSeVeComoPuntoMedio() {
        assertEquals("2·x + 5", prettyMath("2*x + 5"))
    }

    @Test
    fun exponentesSeVenComoSuperindices() {
        assertEquals("x² + 1", prettyMath("x^2 + 1"))
        assertEquals("x⁻³", prettyMath("x^-3"))
        assertEquals("x¹⁰", prettyMath("x^10"))
    }

    @Test
    fun unPotenciaSinDigitosNoSeComeElSimbolo() {
        assertEquals("x^(2)", prettyMath("x^(2)"))
        assertEquals("x^", prettyMath("x^"))
    }

    @Test
    fun laLecturaEnVozAltaDiceLaOracionCompleta() {
        assertEquals("2 por x más 5 es igual a 11", equationAnnouncement("2*x + 5 = 11"))
        assertEquals("x elevado a 2 menos 4 es igual a 0", equationAnnouncement("x^2 - 4 = 0"))
        assertEquals("x dividido entre 2 más 1 dividido entre 3", equationAnnouncement("x/2 + 1/3"))
    }

    @Test
    fun laLecturaNoDejaHuecosDobles() {
        val spoken = equationAnnouncement(" 2 * x  =  11 ")
        assertEquals(spoken.trim(), spoken)
        assertTrue("  " !in spoken)
    }

    @Test
    fun loQueSeAnunciaCorrespondeConLoQueImprimeElMotor() {
        val engine = MathEngine()
        val expression = checkNotNull(engine.parseOrNull("3*(x - 2) = 6"))
        val spoken = equationAnnouncement(engine.render(expression))
        assertTrue("es igual a" in spoken, "sin el verbo igual: $spoken")
        assertTrue("3" in spoken && "2" in spoken && "6" in spoken)
    }
}
