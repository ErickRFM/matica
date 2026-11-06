package mx.matica.app

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mx.matica.design.theme.MaticaTheme
import mx.matica.presentation.state.MaticaStateView
import mx.matica.presentation.state.MaticaViewState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los cinco estados de B3-03 dibujados por el mismo `MaticaStateView` que usan las pantallas, sobre el
 * compositor del dispositivo. Se comprueba lo que el estudiante ve y puede tocar: que la carga se ve,
 * que el vacío explica, que el error se puede reintentar y que estar sin conexión no se disfraza de error.
 */
@RunWith(AndroidJUnit4::class)
class MaticaStateViewsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laCargaSePintaYSeRindeSoloContraElEstadoSiguiente() {
        var state by mutableStateOf<MaticaViewState<String>>(MaticaViewState.Loading)
        rule.setContent {
            MaticaTheme(darkTheme = false) {
                MaticaStateView(state = state) { value -> Text("datos: $value") }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Cargando").assertIsDisplayed()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertPresent("el estado de carga no pintó su indicador de progreso")

        rule.runOnIdle { state = MaticaViewState.Success("42") }
        rule.waitForIdle()
        rule.onNodeWithText("datos: 42").assertIsDisplayed()
        rule.onNodeWithText("Cargando").assertDoesNotExist()
    }

    @Test
    fun elVacioExplicaQueNoHayNadaYNoOfreceReintentar() {
        setContent(MaticaViewState.Empty(title = "Nada vencido hoy", description = "Practica y aparecerán los repasos."))
        rule.onNodeWithText("Nada vencido hoy").assertIsDisplayed()
        rule.onNodeWithText("Practica y aparecerán los repasos.").assertIsDisplayed()
        rule.onNodeWithText(MaticaViewState.RETRY_LABEL).assertDoesNotExist()
    }

    @Test
    fun elErrorSePuedeReintentarDesdeLaPantalla() {
        var retries = 0
        setContent(MaticaViewState.Error(title = "No se pudo leer el progreso", detail = "El archivo local está dañado.")) {
            retries += 1
        }
        rule.onNodeWithText("No se pudo leer el progreso").assertIsDisplayed()
        rule.onNodeWithText("El archivo local está dañado.").assertIsDisplayed()
        rule.onNodeWithText(MaticaViewState.RETRY_LABEL).performClick()
        rule.waitForIdle()
        assertEquals(1, retries, "El botón de reintento se pintó pero no está cableado.")
    }

    @Test
    fun sinConexionAvisaQueElMotorLocalSigueSirviendo() {
        setContent(MaticaViewState.Offline(title = "Sin conexión", detail = "No se pudo sincronizar el progreso."))
        rule.onNodeWithText("Sin conexión").assertIsDisplayed()
        rule.onNodeWithText("motor local funciona sin conexión", substring = true).assertIsDisplayed()
        // Offline no es Error: sin una acción explícita no ofrece reintento, para no insinuar un fallo.
        rule.onNodeWithText(MaticaViewState.RETRY_LABEL).assertDoesNotExist()
    }

    @Test
    fun elExitoMuestraElContenidoSinMensajesDeEstado() {
        setContent(MaticaViewState.Success("x = 4"))
        rule.onNodeWithText("x = 4").assertIsDisplayed()
        rule.onNodeWithText("Cargando").assertDoesNotExist()
        rule.onNodeWithText(MaticaViewState.RETRY_LABEL).assertDoesNotExist()
    }

    private fun setContent(state: MaticaViewState<String>, onRetry: (() -> Unit)? = null) {
        rule.setContent {
            MaticaTheme(darkTheme = false) {
                MaticaStateView(state = state, onRetry = onRetry) { value -> Text(value) }
            }
        }
        rule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.assertPresent(why: String) {
        val found = runCatching { assertExists() }.isSuccess
        assertTrue(found, "$why (compositor del dispositivo).")
    }
}
