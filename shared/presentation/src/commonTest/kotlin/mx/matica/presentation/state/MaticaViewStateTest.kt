package mx.matica.presentation.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaticaViewStateTest {

    @Test
    fun ningunEstadoSeAnunciaVacio() {
        val states: List<MaticaViewState<String>> = listOf(
            MaticaViewState.Loading,
            MaticaViewState.Empty("Sin rutas", "Todavía no hay habilidades recorridas."),
            MaticaViewState.Error("No se pudo verificar", "El paso usa una regla que no existe."),
            MaticaViewState.Offline("Sin conexión", "Se perdió la verificación remota."),
            MaticaViewState.Success("2x + 5 = 11"),
        )
        states.forEach { state ->
            assertTrue(state.announceAs().isNotBlank(), "estado sin anuncio: $state")
        }
    }

    @Test
    fun elErrorSugiereReintentarPorDefecto() {
        assertEquals(MaticaViewState.RETRY_LABEL, MaticaViewState.Error("t", "d").retryLabel)
        assertEquals("Seguir igual", MaticaViewState.Error("t", "d", retryLabel = "Seguir igual").retryLabel)
    }

    @Test
    fun offline_y_error_noSonElMismoEstado() {
        val error: MaticaViewState<String> = MaticaViewState.Error("t", "d")
        val offline: MaticaViewState<String> = MaticaViewState.Offline("t", "d")
        assertFalse(error == offline, "offline debe seguir siendo su propio estado")
        assertTrue(offline.announceAs().contains("d"))
    }

    @Test
    fun successConservaSuDatos() {
        val state: MaticaViewState<String> = MaticaViewState.success("2x + 5 = 11")
        assertEquals("2x + 5 = 11", (state as MaticaViewState.Success).data)
    }
}
