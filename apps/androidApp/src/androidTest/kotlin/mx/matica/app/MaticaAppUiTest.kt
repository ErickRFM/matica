package mx.matica.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.design.tokens.MaticaPalette
import mx.matica.math.skills.SkillId
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.app.verdictMessage
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Estas pruebas corren en el dispositivo y tocan la pantalla de verdad: verifican que el host, las
 * rutas y el estado observable están cableados, no que una función pura devuelve lo esperado.
 *
 * Lo matemático se comprueba en `commonTest`; aquí sólo se mira lo que el usuario ve después de pulsar.
 */
@RunWith(AndroidJUnit4::class)
class MaticaAppUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun session() = MaticaSession(today = LocalDate(2026, 11, 5))

    @Test
    fun elDiagnostioCuentaEnPantallaCadaRespuesta() {
        val session = session()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Diagnosis)) }
        rule.waitForIdle()
        rule.onNodeWithText("0 de 15 respondidas").assertIsDisplayed()

        val question = requireNotNull(session.currentDiagnosis()).pendingQuestions().first()
        rule.onNodeWithText(question.options[question.correctIndex]).performClick()
        rule.waitForIdle()

        rule.onNodeWithText("1 de 15 respondidas").assertIsDisplayed()
        assertEquals(1, session.diagnosisAnswered)
    }

    @Test
    fun laHojaDePistasDejaElReproductorDebajoYRegresa() {
        val session = session()
        val skill = SkillId.BALANCE_PRINCIPLE
        val navigator = MaticaNavigator(MaticaRoute.Home)
        navigator.go(MaticaRoute.LessonPlayer(skill, 1))
        rule.setContent { MaticaApp(session = session, navigator = navigator) }
        rule.waitForIdle()
        rule.onNodeWithText("Comprobar paso").assertIsDisplayed()

        rule.onNodeWithText("Pista").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Mostrar una pista más").assertIsDisplayed()
        assertEquals(MaticaRoute.HintSheet(skill, 1), navigator.current)
        assertEquals(MaticaRoute.LessonPlayer(skill, 1), navigator.base)

        // Con la hoja abierta hay dos botones «Regresa» en el árbol: el del reproductor de abajo y el
        // de la hoja. Se pulsa el de la hoja, que es el que el estudiante ve. El título es hermano
        // directo del botón: un `Column` sin semántica propia no aparece como nodo intermedio.
        rule.onNode(hasContentDescription("Regresa") and hasAnySibling(hasText("Pistas"))).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Comprobar paso").assertIsDisplayed()
        assertEquals(MaticaRoute.LessonPlayer(skill, 1), navigator.current)
    }

    @Test
    fun elReproductorMuestraElVeredictoQueDioElMotor() {
        val session = session()
        val navigator = MaticaNavigator(MaticaRoute.LessonPlayer(SkillId.BALANCE_PRINCIPLE, 1))
        rule.setContent { MaticaApp(session = session, navigator = navigator) }
        rule.waitForIdle()

        rule.onNode(hasSetTextAction() and hasAnySibling(hasText("Siguiente línea")))
            .performTextInput("x = 4")
        rule.onNodeWithText("Comprobar paso").performClick()
        rule.waitForIdle()

        val verdict = requireNotNull(session.lastVerdict)
        // El texto en pantalla es el del motor: la UI no decide si el paso sirve.
        rule.onNodeWithText(verdictMessage(verdict).first).assertIsDisplayed()
        assertEquals(1, session.attempts.size)
    }

    @Test
    fun laRespuestaVerificadaEnPantallaCierraLaLeccion() {
        val skill = SkillId.BALANCE_PRINCIPLE
        val session = session()
        val plan = session.lessonsFor(skill).first { it.number == 1 }.plan
        val solution = requireNotNull(session.solutionOf(plan)).toString()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.LessonPlayer(skill, 1))) }
        rule.waitForIdle()

        rule.onNode(hasSetTextAction() and hasAnySibling(hasText("¿Cuánto vale ${plan.variable}?")))
            .performTextInput(solution)
        rule.onNodeWithText("Verificar por sustitución").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Lección terminada").assertIsDisplayed()
        assertTrue(session.xp > 0, "Cerrar la lección sin XP dejaría la recompensa desconectada de la evidencia.")
    }

    @Test
    fun losAjustesCambianElTemaQueSePinta() {
        val session = session()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Settings)) }
        rule.waitForIdle()

        rule.onNodeWithText("Claro").performClick()
        rule.waitForIdle()
        assertCercaDe(
            MaticaPalette.Mist.toArgb(),
            cornerPixel(),
            "Con tema claro el fondo pintado no es MaticaPalette.Mist",
        )

        rule.onNodeWithText("Oscuro").performClick()
        rule.waitForIdle()
        assertCercaDe(
            MaticaPalette.Night.toArgb(),
            cornerPixel(),
            "Con tema oscuro el fondo pintado no es MaticaPalette.Night",
        )
    }

    private fun cornerPixel(): Int =
        rule.onRoot().captureToImage().asAndroidBitmap().getPixel(4, 4)

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
