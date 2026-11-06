package mx.matica.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mx.matica.design.a11y.Contrast

/**
 * Botones y chips no heredan el color de Material: fijan su propio par. Por eso tienen que medirse
 * aquí; que el tema esté auditado no sirve si el par propio está mal elegido.
 */
class ComponentContrastTest {

    private val variants = MaticaButtonVariant.entries.toList()

    @Test
    fun lasCincoVariantesSeMidenEnAmbosTemas() {
        val pairs = MaticaButtonPalette.measuredPairs()
        assertEquals(variants.size * 2, pairs.size)
        pairs.forEach { pair ->
            assertTrue(
                Contrast.meetsAa(pair.foreground, pair.background),
                "${pair.usage}: ${Contrast.ratio(pair.foreground, pair.background)}",
            )
        }
    }

    @Test
    fun loQueSePintaEsLoQueSeMide() {
        listOf(false, true).forEach { dark ->
            variants.forEach { variant ->
                val painted = MaticaButtonPalette.pairOf(variant, dark)
                assertTrue(
                    painted in MaticaButtonPalette.measuredPairs(),
                    "$variant en ${if (dark) "oscuro" else "claro"} no está en la lista medida",
                )
            }
        }
    }

    @Test
    fun losChipsDeGamificacionCumplenAA() {
        assertEquals(2, MaticaChipPalette.measuredPairs().size)
        MaticaChipPalette.measuredPairs().forEach { pair ->
            assertTrue(
                Contrast.meetsAa(pair.foreground, pair.background),
                "${pair.usage}: ${Contrast.ratio(pair.foreground, pair.background)}",
            )
        }
    }

    @Test
    fun rachaYExperienciaNoCompartenPar() {
        assertEquals(2, MaticaChipPalette.measuredPairs().map { it.foreground to it.background }.toSet().size)
    }
}
