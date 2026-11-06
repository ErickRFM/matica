package mx.matica.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.theme.MaticaTheme
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.app.ThemeMode
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Movimiento sobre el dispositivo. Dos cosas distintas y ambas importan: que la escala llegue de
 * verdad al compositor (el ajuste de accesibilidad no puede quedarse en un texto) y que apagar las
 * animaciones nunca esconda contenido, que es el fallo típico de estas banderas.
 */
@RunWith(AndroidJUnit4::class)
class MaticaMotionTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laEscalaCeroLlegaAlCompositorYTopeLosFundidos() {
        var scale by mutableStateOf(1f)
        rule.setContent {
            MaticaTheme(darkTheme = false, motionScale = scale) {
                Column {
                    val style = LocalMotionStyle.current
                    Text(if (style.reduced) "movimiento reducido" else "movimiento activo")
                    Text("fundido de 160 ms: ${style.fadeMs(160)}")
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("movimiento activo").assertIsDisplayed()
        rule.onNodeWithText("fundido de 160 ms: 160").assertIsDisplayed()

        rule.runOnIdle { scale = 0f }
        rule.waitForIdle()
        rule.onNodeWithText("movimiento reducido").assertIsDisplayed()
        // Reducido no significa «sin transición»: el fundido se topea, el contenido sigue apareciendo.
        rule.onNodeWithText("fundido de 160 ms: 150").assertIsDisplayed()
    }

    @Test
    fun reducirTransicionesDesdeAjustesNoDejaPantallaSinContenido() {
        var route by mutableStateOf<MaticaRoute>(MaticaRoute.Settings)
        val session = MaticaSession(today = LocalDate(2026, 11, 5))
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(route)) }
        rule.waitForIdle()

        val toggle = rule.onNodeWithText("Transiciones reducidas: apagadas")
        toggle.performScrollTo()
        toggle.performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Transiciones reducidas: activas").assertIsDisplayed()
        assertTrue(session.settings.reduceTransitions, "La etiqueta cambió pero el ajuste no.")

        // Con las transiciones apagadas se navega igual: el contenido no depende del movimiento.
        rule.runOnIdle { route = MaticaRoute.RouteMap }
        rule.waitForIdle()
        rule.onNodeWithText("Mi ruta").assertIsDisplayed()

        rule.runOnIdle { route = MaticaRoute.Settings }
        rule.waitForIdle()
        rule.onNodeWithText("Transiciones reducidas: activas").assertIsDisplayed()
    }

    @Test
    fun laEscalaNingunaSeGuardaEnAjustesYSigueHabiendoPantalla() {
        val session = MaticaSession(today = LocalDate(2026, 11, 5))
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Settings)) }
        rule.waitForIdle()

        val ninguna = rule.onNodeWithText("Ninguna")
        ninguna.performScrollTo()
        ninguna.performClick()
        rule.waitForIdle()
        assertEquals(0f, session.settings.motionScale)

        // «Ninguna» apaga animaciones, no la pantalla: el resto de los ajustes sigue visible y operable.
        rule.onNodeWithText("Tema").assertIsDisplayed()
        val oscuro = rule.onNodeWithText("Oscuro")
        oscuro.performScrollTo()
        oscuro.performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.DARK, session.settings.themeMode)
    }
}
