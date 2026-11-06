package mx.matica.presentation.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import mx.matica.math.numeric.Rational

class PlotSeriesTest {

    private fun whole(value: Long) = Rational.fromLong(value)

    private val reciprocal: (Rational) -> Rational? = { x -> if (x.isZero) null else Rational.ONE / x }

    @Test
    fun lasMuestrasSonExactasYEquiespaciadas() {
        val xs = samplePlot({ x -> x }, whole(-1), whole(1), samples = 5).map { checkNotNull(it).x }
        assertEquals(
            listOf(Rational.of(-1, 1), Rational.of(-1, 2), Rational.ZERO, Rational.of(1, 2), Rational.of(1, 1)),
            xs,
        )
        val step = xs[1] - xs[0]
        assertEquals(3, (2 until xs.size).count { xs[it] - xs[it - 1] == step })
    }

    @Test
    fun unPuntoIndefinidoCortaElTrazo() {
        val points = samplePlot(reciprocal, whole(-1), whole(1), samples = 5)
        assertEquals(1, points.count { it == null })
        assertNull(points[2])
        val series = plotSeries(points, whole(-1), whole(1), whole(-8), whole(8))
        assertEquals(1, series.gaps)
        assertEquals(2, series.segments.size)
    }

    @Test
    fun lo_que_sale_de_rango_se_corta_en_lugar_de_recortarse() {
        val steep: (Rational) -> Rational? = { x -> x * 100L }
        val points = samplePlot(steep, Rational.ZERO, whole(1), samples = 3)
        val series = plotSeries(points, Rational.ZERO, whole(1), Rational.ZERO, whole(10))
        assertEquals(2, series.outOfRange)
        assertEquals(1, series.segments.sumOf { it.size })
    }

    @Test
    fun elEjeVerticalEstaInvertidoPorqueElCanvasCreceHaciaAbajo() {
        val identity: (Rational) -> Rational? = { x -> x }
        val points = samplePlot(identity, Rational.ZERO, whole(2), samples = 3)
        val series = plotSeries(points, Rational.ZERO, whole(2), Rational.ZERO, whole(2))
        val all = series.segments.flatten()
        assertEquals(3, all.size)
        assertEquals(1f, all.first().yFraction, 1e-4f)
        assertEquals(0f, all.last().yFraction, 1e-4f)
    }

    @Test
    fun todasLasCoordenadasQuedanDentroDeLaCaja() {
        val points = samplePlot(reciprocal, whole(-3), whole(3), samples = 13)
        val series = plotSeries(points, whole(-3), whole(3), whole(-2), whole(2))
        series.segments.flatten().forEach { point ->
            assertTrue(point.xFraction in 0f..1f, "x fuera de caja: ${point.xFraction}")
            assertTrue(point.yFraction in 0f..1f, "y fuera de caja: ${point.yFraction}")
        }
    }

    @Test
    fun sinDosMuestrasNoHayGrafica() {
        assertFailsWith<IllegalArgumentException> { samplePlot(reciprocal, whole(-1), whole(1), samples = 1) }
    }

    @Test
    fun unEjeHorizontalQueNoCreceSeRechaza() {
        assertFailsWith<IllegalArgumentException> { samplePlot(reciprocal, whole(1), whole(-1), samples = 5) }
    }
}
