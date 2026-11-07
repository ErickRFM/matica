package mx.matica.learning.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * El motor arma frases con conteos que el estudiante lee en voz alta. Estas tres reglas son las que
 * la auditoría de UI del 2026-11-06 encontró rotas en pantalla: cero y uno comparten la terminación
 * del plural, y ninguna de las dos lleva el sufijo «(s)» del programador.
 */
class PluralEsTest {

    @Test
    fun unoVaEnSingularYCeroYMultitudEnPlural() {
        assertEquals("día", pluralEs(1, "día", "días"))
        assertEquals("días", pluralEs(0, "día", "días"))
        assertEquals("días", pluralEs(2, "día", "días"))
        assertEquals("habilidad", pluralEs(1, "habilidad", "habilidades"))
        assertEquals("habilidades", pluralEs(5, "habilidad", "habilidades"))
    }

    @Test
    fun unaPalabraInvariableNoNecesitaSegundaForma() {
        assertEquals("XP", pluralEs(1, "XP"))
        assertEquals("XP", pluralEs(40, "XP"))
    }

    @Test
    fun laTerminacionDelProgramadorNoSaleDelHelper() {
        listOf(0, 1, 2).forEach { n ->
            assertFalse(
                pluralEs(n, "día", "días").contains("(s)"),
                "El texto con sufijo «(s)» llegaba a la pantalla; con n=$n sigue ahí.",
            )
        }
    }
}
