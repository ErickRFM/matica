package mx.matica.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveCompanionMascotTest {

    @Test
    fun losEstadosReactivosTienenEtiquetaEnEspanolYEstanCompletos() {
        val states = LiveCompanionState.entries
        assertEquals(4, states.size)
        assertTrue(states.contains(LiveCompanionState.IDLE))
        assertTrue(states.contains(LiveCompanionState.THINKING))
        assertTrue(states.contains(LiveCompanionState.ENCOURAGING))
        assertTrue(states.contains(LiveCompanionState.CHEERING))

        states.forEach { state ->
            assertTrue(state.labelEs.isNotBlank(), "El estado $state debe tener descripción en español")
        }
    }

    @Test
    fun elBobbingSeAnulaEstrictamenteConMovimientoReducido() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            val offset = companionBobbingOffsetDp(reduced = true, progress = progress)
            assertEquals(0f, offset, "Con movimiento reducido el desplazamiento debe ser estrictamente 0 dp")
        }
    }

    @Test
    fun elBobbingOscilaEntreMenosTresYMasTresDp() {
        val start = companionBobbingOffsetDp(reduced = false, progress = 0f)
        val middle = companionBobbingOffsetDp(reduced = false, progress = 0.5f)
        val end = companionBobbingOffsetDp(reduced = false, progress = 1f)

        assertEquals(-3f, start, 0.001f)
        assertEquals(0f, middle, 0.001f)
        assertEquals(3f, end, 0.001f)
    }

    @Test
    fun elProgresoDelBobbingSeAcotaSeguramente() {
        val below = companionBobbingOffsetDp(reduced = false, progress = -1f)
        val above = companionBobbingOffsetDp(reduced = false, progress = 2f)

        assertEquals(-3f, below, 0.001f)
        assertEquals(3f, above, 0.001f)
    }
}
