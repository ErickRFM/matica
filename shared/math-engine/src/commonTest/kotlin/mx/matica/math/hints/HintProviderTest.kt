package mx.matica.math.hints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.eq
import mx.matica.math.transform.RuleId

class HintProviderTest {

    private val proveedor = HintProvider()

    @Test
    fun laEscaleraCrecienteNoRevelaLaRespuesta() {
        val pistas = proveedor.hintsFor("2(x+3)=14")
        assertEquals(3, pistas.size, "Hasta NAME_RULE deben aparecer tres niveles")
        assertEquals(listOf(HintLevel.CONCEPT, HintLevel.LOCATE, HintLevel.NAME_RULE), pistas.map { it.level })
        assertTrue(pistas.none { "x = 4" in it.text }, "Ninguna pista previa debe dar la solución")
        assertContains(pistas.first().text, "balanza")
    }

    @Test
    fun elUltimoNivelSoloSeMuestraCuandoSePide() {
        val completa = proveedor.hintsFor("2(x+3)=14", upTo = HintLevel.SHOW_LINE)
        assertEquals(4, completa.size)
        val ultima = completa.last()
        assertEquals(HintLevel.SHOW_LINE, ultima.level)
        assertContains(ultima.text, "2x")
        assertContains(ultima.text, "14")
    }

    @Test
    fun identificaElObstaculoCorrecto() {
        val paso = proveedor.nextStep(eq("2(x+3)=14"))
        assertEquals(RuleId.DISTRIBUTE, paso?.rule)

        val conDenominadores = proveedor.nextStep(eq("x/2 + 1 = 5"))
        assertEquals(RuleId.MULTIPLY_BOTH_SIDES_BY, conDenominadores?.rule)

        val conTerminoIndependiente = proveedor.nextStep(eq("x + 3 = 7"))
        assertEquals(RuleId.SUBTRACT_FROM_BOTH_SIDES, conTerminoIndependiente?.rule)

        val conPendiente = proveedor.nextStep(eq("2x = 8"))
        assertEquals(RuleId.DIVIDE_BOTH_SIDES_BY, conPendiente?.rule)
    }

    @Test
    fun noInventaPistasCuandoNoHayEcuacion() {
        val texto = proveedor.hintsFor("x + 1").single()
        assertEquals(HintLevel.CONCEPT, texto.level)
        assertContains(texto.text, "'='")

        assertTrue(proveedor.hintsFor("@@@").single().text.contains("leer"))
    }

    @Test
    fun cuandoLaIncognitaYaEstaAisladaPideVerificar() {
        val pista = proveedor.hintsFor("x = 4").single()
        assertContains(pista.text, "comprueba")
    }
}
