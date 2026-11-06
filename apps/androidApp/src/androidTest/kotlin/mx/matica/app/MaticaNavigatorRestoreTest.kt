package mx.matica.app

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertTrue
import mx.matica.math.skills.SkillId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La rotación se simula como la vive el estudiante: se destruye `MainActivity` — el host real, que es
 * el que llama a `rememberMaticaNavigator` en su propio `onCreate` — y se levanta otra instancia.
 * Una prueba que montara el contenido desde la prueba misma nunca restauraría nada: el `setContent`
 * de la prueba no forma parte del `savedInstanceState`, así que lo observado aquí es sólo la app.
 */
@RunWith(AndroidJUnit4::class)
class MaticaNavigatorRestoreTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun unaRotacionDevuelveLaPilaExactaSinVolverAlInicio() {
        rule.waitForIdle()
        // El host arranca en Bienvenida porque `MainActivity` crea una sesión nueva en cada proceso, así
        // que el recorrido es el que haría el estudiante con el dedo: sin inyectar ninguna pila.
        scrollAndClick("Explorar la ruta sin diagnóstico")
        scrollAndClick("Abrir mi ruta")
        // El nudo abierto del perfil nuevo es el único al que el dedo puede entrar: los demás están
        // bloqueados hasta dominar sus prerrequisitos, y pulsar uno bloqueado no navega.
        rule.onNode(
            hasClickAction() and hasContentDescription(SkillId.INTEGER_OPERATIONS.labelEs, substring = true),
        ).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Por qué esta habilidad").assertPresent("el detalle de la habilidad")

        liveRotate()

        // Sin el `Saver` del navegador la app reaparecería en Bienvenida; la habilidad sigue en pantalla.
        rule.onNodeWithText("Por qué esta habilidad").assertPresent("la habilidad tras la rotación")
        rule.onNodeWithText("Comenzar el diagnóstico").assertAbsent("Bienvenida, que no debía volver")

        // Y la pila no se aplastó: cada atrás deshace un nivel, no teleporta al inicio.
        pressBack()
        rule.onNodeWithText("Mi ruta").assertPresent("el mapa de ruta restaurado")
        pressBack()
        rule.onNodeWithText("Abrir mi ruta").assertPresent("la pantalla de hoy restaurada")
        pressBack()
        rule.onNodeWithText("Explorar la ruta sin diagnóstico").assertPresent("la bienvenida restaurada")
    }

    /** `recreate()` sólo corre en el hilo principal; la cola de instrumentación hace el resto. */
    private fun liveRotate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { rule.activity.recreate() }
        instrumentation.waitForIdleSync()
        rule.waitForIdle()
    }

    private fun pressBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { rule.activity.onBackPressedDispatcher.onBackPressed() }
        instrumentation.waitForIdleSync()
        rule.waitForIdle()
    }

    private fun scrollAndClick(label: String) {
        rule.onNodeWithText(label).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.assertPresent(what: String) {
        val found = runCatching { assertExists() }.isSuccess
        assertTrue(found, "$what no apareció en el dispositivo.")
    }

    private fun SemanticsNodeInteraction.assertAbsent(what: String) {
        val gone = runCatching { assertDoesNotExist() }.isSuccess
        assertTrue(gone, "$what siguió en pantalla.")
    }
}
