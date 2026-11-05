package mx.matica.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import mx.matica.design.theme.MaticaTheme
import mx.matica.design.tokens.MaticaPalette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de instrumentación: se ejecuta sobre hardware, no sobre un sustituto JVM.
 *
 * Lo que interesa no es que el texto aparezca, sino que el píxel que pinta el teléfono sea el
 * token del sistema. Un `ColorScheme` mal cableado daría verde en las pruebas de reglas y rojo
 * aquí.
 */
@RunWith(AndroidJUnit4::class)
class MaticaThemeRenderTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun elSmokeScreenSeMuestraConElTemaClaro() {
        rule.setContent {
            MaticaTheme(darkTheme = false) {
                Surface { SmokeScreen() }
            }
        }
        rule.onNodeWithText("Mática").assertIsDisplayed()
        rule.onNodeWithText("1/2 + 1/3 = 5/6").assertIsDisplayed()
    }

    @Test
    fun laSuperficieQuePintaElTelefonoEsElTokenMedido() {
        rule.setContent {
            MaticaTheme(darkTheme = false) {
                Surface { SmokeScreen() }
            }
        }
        assertCercaDe(
            MaticaPalette.Paper.toArgb(),
            rule.onRoot().captureToImage().asAndroidBitmap().getPixel(4, 4),
            "El colorScheme.surface renderizado no es MaticaPalette.Paper",
        )
    }

    @Test
    fun elFondoDePantallaQuePintaElTelefonoEsElTokenMedido() {
        rule.setContent {
            MaticaTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) { SmokeScreen() }
            }
        }
        assertCercaDe(
            MaticaPalette.Mist.toArgb(),
            rule.onRoot().captureToImage().asAndroidBitmap().getPixel(4, 4),
            "El colorScheme.background renderizado no es MaticaPalette.Mist",
        )
    }

    private fun assertCercaDe(expected: Int, actual: Int, message: String) {
        val canales = intArrayOf(16, 8, 0)
        val desviado = canales.any { shift ->
            abs(((expected shr shift) and 0xFF) - ((actual shr shift) and 0xFF)) > 2
        }
        if (desviado) {
            throw AssertionError(
                "$message: esperado #${Integer.toHexString(expected)} real #${Integer.toHexString(actual)}",
            )
        }
    }
}
