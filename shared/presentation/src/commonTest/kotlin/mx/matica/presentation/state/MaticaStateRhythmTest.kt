package mx.matica.presentation.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mx.matica.design.tokens.MaticaSpacing

/**
 * El contenedor de estados es una sola tarjeta, así que una sola tarjeta impone un solo ritmo. Se
 * mide desde el modelo de ritmo que leen los cuatro cuerpos (`rhythmOf`), porque la separación real
 * que ve el estudiante es la que esos cuerpos aplican.
 */
class MaticaStateRhythmTest {

    @Test
    fun losCuatroCuerposDelContenedorPidenUnSoloRitmo() {
        val rhythms = StateBody.entries.map { rhythmOf(it) }
        assertEquals(
            1,
            rhythms.toSet().size,
            "el contenedor impone ritmos distintos: " + StateBody.entries.zip(rhythms).joinToString { "${it.first}=${it.second}" },
        )
        val rhythm = rhythms.first()
        assertEquals(MaticaSpacing.md, rhythm.gap, "la separación interior debe ser el paso md del sistema")
        assertEquals(MaticaSpacing.lg, rhythm.bodyPadding, "la respiración del borde debe ser el paso lg del sistema")
        assertTrue(
            rhythm.gap < rhythm.bodyPadding,
            "el borde respira menos que la separación entre elementos: $rhythm",
        )
    }

    @Test
    fun elRitmoDelModeloDeEstadosNoDependeDelEstado() {
        // Los cinco estados del modelo se dibujan con esos cuatro cuerpos; ninguno puede reclamar otro ritmo.
        val states: List<MaticaViewState<String>> = listOf(
            MaticaViewState.Loading,
            MaticaViewState.Empty("Sin rutas", "Todavía no hay habilidades recorridas."),
            MaticaViewState.Error("No se pudo verificar", "El paso usa una regla que no existe."),
            MaticaViewState.Offline("Sin conexión", "Se perdió la verificación remota."),
            MaticaViewState.Success("2x + 5 = 11"),
        )
        val bodies = states.map { state ->
            when (state) {
                is MaticaViewState.Loading -> StateBody.Loading
                is MaticaViewState.Empty, is MaticaViewState.Error, is MaticaViewState.Offline -> StateBody.Message
                is MaticaViewState.Success -> StateBody.Success
            }
        }
        assertEquals(1, bodies.map { rhythmOf(it).gap }.toSet().size, "dos estados de la lista se dibujan con separaciones distintas: $bodies")
        assertEquals(1, bodies.map { rhythmOf(it).bodyPadding }.toSet().size, "dos estados de la lista respiran distinto en el borde: $bodies")
        assertEquals(states.size, bodies.size, "un estado se quedó sin cuerpo que lo dibuje")
    }

    @Test
    fun elEsqueletoNoLateConMovimientoReducido() {
        val resting = skeletonAlphaRange(reduced = true)
        assertEquals(
            resting.start,
            resting.endInclusive,
            "el esqueleto anima su alpha aunque el sistema pidió calma: $resting",
        )
        assertEquals(1f, resting.start, "con calma el esqueleto debe verse igual que siempre, sin atenuarse")
        val active = skeletonAlphaRange(reduced = false)
        assertTrue(active.start < active.endInclusive, "sin reducir, el esqueleto debe late: $active")
        assertTrue(active.start >= 0f && active.endInclusive <= 1f, "el alfa del esqueleto salió del rango pintable: $active")
    }

    @Test
    fun elIndicadorDeCargaNoAnimaEnBucleConMovimientoReducido() {
        assertTrue(loadingIndicatorSpinsInfinitely(reduced = false), "sin reducir el indicador debe girar")
        assertTrue(
            !loadingIndicatorSpinsInfinitely(reduced = true),
            "el indicador animó en bucle infinito con el movimiento reducido pedido",
        )
        // El cambio es sólo de dibujo: el texto accesible de carga sigue siendo el mismo que mide la instrumentación.
        assertEquals("Cargando", MaticaViewState.Loading.announceAs(), "el indicador estático cambió el texto de carga")
    }
}
