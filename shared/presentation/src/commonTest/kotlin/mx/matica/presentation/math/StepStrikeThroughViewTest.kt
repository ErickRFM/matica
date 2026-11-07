package mx.matica.presentation.math

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mx.matica.design.a11y.Contrast
import mx.matica.design.tokens.MaticaPalette

class StepStrikeThroughViewTest {

    @Test
    fun todosLosParesDeColoresCumplenAccesibilidadAA() {
        val pairs = StepStrikePalette.measuredPairs()
        assertTrue(pairs.isNotEmpty(), "Debe haber pares medidos para claro y oscuro.")
        pairs.forEach { pair ->
            assertTrue(
                Contrast.meetsAa(pair.foreground, pair.background),
                "Fallo de contraste en ${pair.usage}: ratio ${Contrast.ratio(pair.foreground, pair.background)}",
            )
        }
    }

    @Test
    fun calculoDeExtremosDeTachadoCorrectoParaTodasLasDirecciones() {
        val width = 120f
        val height = 60f
        val padding = 4f

        // Diagonal ascendente (típico tachado algebraico escolar)
        val (upStart, upEnd) = calculateStrikeEndpoints(
            direction = StrikeDirection.DIAGONAL_UP,
            width = width,
            height = height,
            padding = padding,
        )
        assertEquals(Offset(4f, 56f), upStart, "Inicio diagonal ascendente en esquina inferior izquierda")
        assertEquals(Offset(116f, 4f), upEnd, "Fin diagonal ascendente en esquina superior derecha")

        // Diagonal descendente
        val (downStart, downEnd) = calculateStrikeEndpoints(
            direction = StrikeDirection.DIAGONAL_DOWN,
            width = width,
            height = height,
            padding = padding,
        )
        assertEquals(Offset(4f, 4f), downStart, "Inicio diagonal descendente en esquina superior izquierda")
        assertEquals(Offset(116f, 56f), downEnd, "Fin diagonal descendente en esquina inferior derecha")

        // Horizontal recta
        val (hStart, hEnd) = calculateStrikeEndpoints(
            direction = StrikeDirection.HORIZONTAL,
            width = width,
            height = height,
            padding = padding,
        )
        assertEquals(Offset(4f, 30f), hStart, "Inicio horizontal en centro vertical izquierdo")
        assertEquals(Offset(116f, 30f), hEnd, "Fin horizontal en centro vertical derecho")
    }

    @Test
    fun interpolacionDeProgresoEsLinealYAcotada() {
        val start = Offset(10f, 90f)
        val end = Offset(110f, 10f)

        // Inicio (0%)
        val at0 = interpolateOffset(start, end, 0f)
        assertEquals(start, at0)

        // Punto medio (50%)
        val at50 = interpolateOffset(start, end, 0.5f)
        assertEquals(Offset(60f, 50f), at50)

        // Fin (100%)
        val at100 = interpolateOffset(start, end, 1f)
        assertEquals(end, at100)

        // Fuera de rango debe acotarse a [0, 1]
        val below0 = interpolateOffset(start, end, -0.2f)
        assertEquals(start, below0)

        val above1 = interpolateOffset(start, end, 1.5f)
        assertEquals(end, above1)
    }

    @Test
    fun descripcionAccesibleReflejaTerminoYEstadoDeCancelacion() {
        val term = "+ 4"
        val cancelledAnnouncement = strikeAnnouncement(term, isCancelled = true)
        assertEquals("Término cancelado: + 4", cancelledAnnouncement)

        val activeAnnouncement = strikeAnnouncement(term, isCancelled = false)
        assertEquals("+ 4", activeAnnouncement)
    }

    @Test
    fun seleccionDeColorDeTachadoSegunModoOscuroYTipo() {
        // En claro
        assertEquals(StepStrikePalette.SoftRedLight, StepStrikePalette.cancelColor(dark = false, isAmber = false))
        assertEquals(StepStrikePalette.AmberLight, StepStrikePalette.cancelColor(dark = false, isAmber = true))

        // En oscuro
        assertEquals(StepStrikePalette.SoftRedDark, StepStrikePalette.cancelColor(dark = true, isAmber = false))
        assertEquals(StepStrikePalette.AmberDark, StepStrikePalette.cancelColor(dark = true, isAmber = true))
    }
}
