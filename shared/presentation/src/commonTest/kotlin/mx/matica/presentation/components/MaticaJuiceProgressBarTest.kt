package mx.matica.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaticaJuiceProgressBarTest {

    @Test
    fun recorteDeProgresoEnRangoValido() {
        assertEquals(0f, MaticaJuiceProgressBarDefaults.clampProgress(0f))
        assertEquals(0.5f, MaticaJuiceProgressBarDefaults.clampProgress(0.5f))
        assertEquals(1f, MaticaJuiceProgressBarDefaults.clampProgress(1f))

        // Fuera de rango
        assertEquals(0f, MaticaJuiceProgressBarDefaults.clampProgress(-0.35f))
        assertEquals(1f, MaticaJuiceProgressBarDefaults.clampProgress(1.42f))
    }

    @Test
    fun calculoDeMuescasDeHitosDistribuidasUniformemente() {
        // Cero o muescas negativas deben devolver lista vacía
        assertTrue(MaticaJuiceProgressBarDefaults.calculateMilestoneFractions(0).isEmpty())
        assertTrue(MaticaJuiceProgressBarDefaults.calculateMilestoneFractions(-2).isEmpty())

        // 1 hito en medio (0.5f)
        val singleNotch = MaticaJuiceProgressBarDefaults.calculateMilestoneFractions(1)
        assertEquals(1, singleNotch.size)
        assertEquals(0.5f, singleNotch[0])

        // 3 hitos (cuartos: 0.25f, 0.5f, 0.75f)
        val threeNotches = MaticaJuiceProgressBarDefaults.calculateMilestoneFractions(3)
        assertEquals(listOf(0.25f, 0.5f, 0.75f), threeNotches)

        // 4 hitos (quintos: 0.2f, 0.4f, 0.6f, 0.8f)
        val fourNotches = MaticaJuiceProgressBarDefaults.calculateMilestoneFractions(4)
        assertEquals(4, fourNotches.size)
        assertEquals(0.2f, fourNotches[0], 0.001f)
        assertEquals(0.4f, fourNotches[1], 0.001f)
        assertEquals(0.6f, fourNotches[2], 0.001f)
        assertEquals(0.8f, fourNotches[3], 0.001f)

        // Comprobar orden estrictamente creciente
        for (i in 0 until fourNotches.size - 1) {
            assertTrue(fourNotches[i] < fourNotches[i + 1])
        }
    }

    @Test
    fun hazDeLuzSeDesactivaEstrictamenteConMovimientoReducido() {
        // Con movimiento reducido, el brillo siempre debe estar apagado (accesibilidad)
        assertFalse(
            MaticaJuiceProgressBarDefaults.shouldDrawSheen(currentProgress = 0.5f, isReducedMotion = true),
            "El haz de luz debe apagarse con movimiento reducido.",
        )
        assertFalse(
            MaticaJuiceProgressBarDefaults.shouldDrawSheen(currentProgress = 1.0f, isReducedMotion = true),
            "El haz de luz debe apagarse con movimiento reducido al 100%.",
        )
    }

    @Test
    fun hazDeLuzSeActivaSoloConProgresoSignificativoEnModoNormal() {
        // En barra vacía o casi nula no debe brillar
        assertFalse(
            MaticaJuiceProgressBarDefaults.shouldDrawSheen(currentProgress = 0.0f, isReducedMotion = false),
            "La barra vacía no debe dibujar haz de luz.",
        )
        assertFalse(
            MaticaJuiceProgressBarDefaults.shouldDrawSheen(currentProgress = 0.01f, isReducedMotion = false),
            "Progreso insignificante no debe dibujar haz de luz.",
        )

        // Con progreso visible y animación activa
        assertTrue(
            MaticaJuiceProgressBarDefaults.shouldDrawSheen(currentProgress = 0.15f, isReducedMotion = false),
            "Debe dibujar el haz con 15% de progreso.",
        )
        assertTrue(
            MaticaJuiceProgressBarDefaults.shouldDrawSheen(currentProgress = 0.85f, isReducedMotion = false),
            "Debe dibujar el haz con 85% de progreso.",
        )
    }

    @Test
    fun parametrosFisicosDeResorteElasticoParaSensacionJuicy() {
        assertEquals(
            Spring.DampingRatioMediumBouncy,
            MaticaJuiceProgressBarDefaults.SPRING_DAMPING,
            "Amortiguamiento debe ser MediumBouncy para sensación jugosa.",
        )
        assertEquals(
            Spring.StiffnessMediumLow,
            MaticaJuiceProgressBarDefaults.SPRING_STIFFNESS,
            "Rigidez del muelle debe ser StiffnessMediumLow.",
        )
        assertTrue(
            MaticaJuiceProgressBarDefaults.DefaultHeight >= 12.dp,
            "La altura predeterminada debe ser suficientemente 'chunky' (al menos 12-16 dp).",
        )
    }

    @Test
    fun calculoDeProgresoDiscretoPorPasos() {
        // Mitad de lección
        val half = calculateDiscreteProgress(done = 3, total = 6)
        assertEquals(0.5f, half)

        // Total 0 evita división por cero
        val zeroTotal = calculateDiscreteProgress(done = 3, total = 0)
        assertEquals(0f, zeroTotal)

        // Sobrepasado se recorta a 1f
        val overflow = calculateDiscreteProgress(done = 10, total = 5)
        assertEquals(1f, overflow)
    }

    private fun calculateDiscreteProgress(done: Int, total: Int): Float {
        if (total <= 0) return 0f
        return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}
