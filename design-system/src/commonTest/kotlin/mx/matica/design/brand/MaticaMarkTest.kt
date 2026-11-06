package mx.matica.design.brand

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La marca se anima recortando su trazo, así que su geometría es una regla y no un adorno: si el
 * reparto por longitud se rompe, el splash dibuja la «M» a tirones o el punto de llegada aparece en
 * un sitio que la ruta no alcanzó.
 */
class MaticaMarkTest {

    @Test
    fun sinProgresoNoHayNingunTramo() {
        assertTrue(maticaMarkSegments(0f).isEmpty())
        assertTrue(maticaMarkSegments(-1f).isEmpty())
    }

    @Test
    fun elTrazoCompletoLlegaAlPuntoDeDestino() {
        val segments = maticaMarkSegments(1f)
        assertEquals(4, segments.size, "La M son cuatro tramos: si cambia, el splash recorta otra figura.")

        val last = segments.last()
        assertCerca(0.74f, last.endX, "el destino horizontal de la marca")
        assertCerca(0.70f, last.endY, "el destino vertical de la marca")
    }

    @Test
    fun aMitadDeProgresoSeDibujoMitadDeLongitud() {
        val full = maticaMarkLength()
        val half = maticaMarkSegments(0.5f)
        val drawn = half.sumOf { segment -> distance(segment).toDouble() }.toFloat()
        assertCerca(full / 2f, drawn, "la mitad del gesto")
        assertTrue(half.isNotEmpty(), "A mitad de trazo la marca no puede estar vacía.")
        assertTrue(half.size <= 4, "No se puede dibujar un tramo que la marca no tiene.")
    }

    @Test
    fun elProgresoNuncaSeSaleDelCuadro() {
        listOf(-0.5f, 0f, 0.1f, 0.37f, 0.5f, 0.93f, 1f, 1.5f).forEach { fraction ->
            maticaMarkSegments(fraction).forEach { segment ->
                listOf(segment.startX, segment.endX).forEach { x ->
                    assertTrue(x in 0f..1f, "x=$x fuera del cuadro con progreso $fraction")
                }
                listOf(segment.startY, segment.endY).forEach { y ->
                    assertTrue(y in 0f..1f, "y=$y fuera del cuadro con progreso $fraction")
                }
            }
        }
    }

    @Test
    fun laMotaRecorreLaRutaDeExtremoAExtremo() {
        val start = maticaMarkPoint(0f)
        assertCerca(0.26f, start.first, "el inicio horizontal de la ruta")
        assertCerca(0.70f, start.second, "el inicio vertical de la ruta")

        val end = maticaMarkPoint(1f)
        assertCerca(0.74f, end.first, "la llegada horizontal de la ruta")
        assertCerca(0.70f, end.second, "la llegada vertical de la ruta")

        (0..20).forEach { step ->
            val point = maticaMarkPoint(step / 20f)
            assertTrue(point.first in 0.26f..0.74f, "La mota se salió de la ruta en x: ${point.first}")
            assertTrue(point.second in 0.30f..0.70f, "La mota se salió de la ruta en y: ${point.second}")
        }
    }

    private fun assertCerca(expected: Float, actual: Float, what: String) {
        assertTrue(
            abs(expected - actual) < 1e-4f,
            "$what: esperado $expected, real $actual",
        )
    }

    private fun distance(segment: MarkSegment): Float {
        val dx = segment.endX - segment.startX
        val dy = segment.endY - segment.startY
        return sqrt(dx * dx + dy * dy)
    }
}
