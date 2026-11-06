package mx.matica.presentation.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import mx.matica.math.numeric.Rational

class NumberLineModelTest {

    private fun whole(value: Long) = Rational.fromLong(value)

    private fun longRange(from: Long, to: Long, step: Long = 1): List<Rational> =
        (from..to step step).map { whole(it) }

    private val symmetric = NumberLineModel(from = whole(-4), to = whole(4), ticks = longRange(-4, 4))

    private val quarters = NumberLineModel(from = Rational.ZERO, to = whole(8), ticks = longRange(0, 8, 2))

    @Test
    fun losExtremosQuedanEnLosBordes() {
        assertEquals(0f, positionOf(whole(-4), whole(-4), whole(4)), 1e-6f)
        assertEquals(1f, positionOf(whole(4), whole(-4), whole(4)), 1e-6f)
    }

    @Test
    fun elCeroDeLaRectaSimetricaEsElCentro() {
        assertEquals(0.5f, positionOf(Rational.ZERO, whole(-4), whole(4)), 1e-6f)
    }

    @Test
    fun unaFraccionSePosicionaExacta() {
        assertEquals(0.125f, positionOf(Rational.of(1, 2), Rational.ZERO, whole(4)), 1e-6f)
    }

    @Test
    fun elArrastreDevuelveSiempreUnaMarcaExacta() {
        assertEquals(whole(-4), valueAt(0f, symmetric))
        assertEquals(whole(4), valueAt(1f, symmetric))
        assertEquals(Rational.ZERO, valueAt(0.5f, symmetric))
        val chosen = valueAt(0.13f, symmetric)
        assertTrue(symmetric.orderedTicks.any { it == chosen }, "$chosen no es una marca de la recta")
    }

    @Test
    fun elArrastreMasAllaDelBordeNoCreaUnValorImposible() {
        assertEquals(whole(4), valueAt(1.9f, symmetric))
        assertEquals(whole(-4), valueAt(-2f, symmetric))
    }

    @Test
    fun laMarcaMasCercanaEsLaQueGana() {
        assertEquals(Rational.ONE, nearestTick(Rational.of(11, 10), longRange(0, 3)))
        assertEquals(whole(3), nearestTick(Rational.of(7, 2), longRange(0, 3)))
    }

    @Test
    fun unEmpateSeResuelveConLaMarcaMenor() {
        assertEquals(whole(2), nearestTick(Rational.of(5, 2), longRange(0, 3)))
    }

    @Test
    fun elPrimerCuartoCaeEnUnaMarcaDelModelo() {
        assertEquals(whole(2), quarterTick(quarters))
    }

    @Test
    fun lasMarcasSeOrdenanAntesDeDibujarse() {
        val shuffled = NumberLineModel(
            from = Rational.ZERO,
            to = whole(10),
            ticks = listOf(whole(7), whole(2), whole(5)),
        )
        assertEquals(listOf(2L, 5L, 7L), shuffled.orderedTicks.map { it.toLongOrNull() })
    }

    @Test
    fun unIntervaloInvertidoSeRechaza() {
        assertFailsWith<IllegalArgumentException> { NumberLineModel(whole(4), whole(4), longRange(0, 4)) }
    }

    @Test
    fun unaRectaSinMarcasSeRechaza() {
        assertFailsWith<IllegalArgumentException> { NumberLineModel(Rational.ZERO, whole(4), emptyList()) }
    }

    @Test
    fun unaPosicionFueraDeRangoNoSeSaleDelCanvas() {
        assertTrue(positionOf(whole(10), Rational.ZERO, whole(4)) in 0f..1f)
    }
}
