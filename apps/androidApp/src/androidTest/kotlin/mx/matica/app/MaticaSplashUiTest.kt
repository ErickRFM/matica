package mx.matica.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertTrue
import mx.matica.design.motion.MotionStyle
import mx.matica.design.theme.MaticaTheme
import mx.matica.presentation.app.MaticaSplash
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El splash se comprueba también montado, no sólo por sus funciones puras: que deje una barra de
 * avance con semántica y que termine solo. Las fases y la duración viven en `commonTest`; aquí se
 * mira lo que queda en el árbol de accesibilidad del dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class MaticaSplashUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laBarraDeAvanceSeAnunciaComoProgreso() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaticaTheme {
                MaticaSplash(onFinished = {}, motion = MotionStyle(1f))
            }
        }
        rule.onNodeWithText("Preparando tu ruta").assertIsDisplayed()
        val barras = rule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        )
        barras.assertCountEquals(1)
    }

    @Test
    fun elSplashTerminaSoloSinEntradaDelUsuario() {
        var terminado = false
        rule.setContent {
            MaticaTheme {
                MaticaSplash(onFinished = { terminado = true }, motion = MotionStyle(1f))
            }
        }
        rule.waitUntil(timeoutMillis = 4_000) { terminado }
        assertTrue(terminado, "El splash no llamó a onFinished: se quedaría tapando la app.")
    }
}
