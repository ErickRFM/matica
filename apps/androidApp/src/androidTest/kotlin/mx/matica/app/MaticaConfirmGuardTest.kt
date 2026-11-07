package mx.matica.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.math.skills.SkillId
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Las dos acciones que borran trabajo pidiendo permiso antes, y el estado `Offline` que hasta ahora
 * estaba declarado sin que ninguna pantalla lo mostrara. Se miden en el compositor del teléfono: una
 * confirmación que sólo existe en el código no protege a nadie.
 */
@RunWith(AndroidJUnit4::class)
class MaticaConfirmGuardTest {

    @get:Rule
    val rule = createComposeRule()

    private fun session() = MaticaSession(today = LocalDate(2026, 11, 5))

    /** Perfil con evidencia de verdad: el diagnóstico completo deja XP y progreso registrado. */
    private fun diagnosedSession(): MaticaSession {
        val session = session()
        session.startDiagnosis()
        session.currentDiagnosis()?.pendingQuestions()?.forEach { question ->
            session.answerDiagnosis(question.item.id, question.correctIndex)
        }
        session.finishDiagnosis()
        return session
    }

    @Test
    fun reiniciarElPerfilNoBorraNadaHastaConfirmar() {
        val session = diagnosedSession()
        val xpSembrado = session.xp
        assertTrue(xpSembrado > 0, "La siembra no dejó evidencia: el perfil arranca en cero.")
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Profile)) }
        rule.waitForIdle()

        rule.onNodeWithText("¿Reiniciar el perfil local?").assertDoesNotExist()
        rule.onNodeWithText("Reiniciar el perfil local").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithText("¿Reiniciar el perfil local?").assertIsDisplayed()
        rule.onNodeWithText("Sí, reiniciar").assertIsDisplayed()
        rule.onNodeWithText("Conservar").assertIsDisplayed()
        assertEquals(xpSembrado, session.xp, "El borrado ocurrió al abrir la confirmación, no al confirmarlo.")

        rule.onNodeWithText("Conservar").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("¿Reiniciar el perfil local?").assertDoesNotExist()
        rule.onNodeWithText("Mi perfil").assertIsDisplayed()
        assertTrue(session.xp > 0, "«Conservar» borró el perfil igual.")

        rule.onNodeWithText("Reiniciar el perfil local").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Sí, reiniciar").performClick()
        rule.waitForIdle()

        assertEquals(0, session.xp, "Confirmado el reinicio, el perfil local sigue teniendo XP.")
        rule.onNodeWithText("Comenzar el diagnóstico").assertIsDisplayed()
    }

    @Test
    fun laPizarraDeLaLeccionConservaLoEscritoHastaConfirmar() {
        val session = session()
        val skill = SkillId.BALANCE_PRINCIPLE
        val templateId = requireNotNull(session.templateIdFor(skill)) { "La habilidad no tiene plantilla." }
        val plan = requireNotNull(session.startPractice(templateId, MaticaSession.BASE_SEED)) { "No hubo plan." }
        session.submitStep(plan, plan.equationText, requireNotNull(session.revealLine(plan)))
        assertTrue(session.attempts.isNotEmpty(), "Sin intentos no se ve el botón de «Empezar de nuevo».")

        val navigator = MaticaNavigator(MaticaRoute.LessonPlayer(skill, 1))
        rule.setContent { MaticaApp(session = session, navigator = navigator) }
        rule.waitForIdle()

        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("2x")
        rule.waitForIdle()
        rule.onNode(campoConTexto("2x")).assertIsDisplayed()

        rule.onNodeWithText("Empezar de nuevo").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("¿Empezar de nuevo?").assertIsDisplayed()
        rule.onNode(campoConTexto("2x"))
            .assertExists("La confirmación ya había vaciado el campo antes de responderla.")

        rule.onNodeWithText("Conservar").performClick()
        rule.waitForIdle()
        rule.onNode(campoConTexto("2x")).assertIsDisplayed()

        rule.onNodeWithText("Empezar de nuevo").performScrollTo().performClick()
        rule.waitForIdle()
        val intentosAntes = session.attempts.size
        rule.onNodeWithText("Sí, borrar la pizarra").performClick()
        rule.waitForIdle()

        rule.onNode(campoConTexto("2x")).assertDoesNotExist()
        assertEquals(intentosAntes, session.attempts.size, "Borrar la pizarra también tiró los intentos ya revisados.")
    }

    /**
     * El texto vivo de un `TextField` no está en la propiedad `Text`, está en `EditableText` y viene
     * como `AnnotatedString`: los Matchers habituales no lo comparan, así que se lee aquí.
     */
    private fun campoConTexto(texto: String) = SemanticsMatcher("campo de texto con «$texto»") { node ->
        node.config.getOrNull(SemanticsProperties.EditableText)?.text == texto
    }

    @Test
    fun elAulaDistingueSinConexionDeSinBackend() {
        val session = session()
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Classroom)) }
        rule.waitForIdle()

        rule.onNodeWithText("Sin conexión con el aula").assertIsDisplayed()
        rule.onNodeWithText("motor local funciona sin conexión", substring = true).assertIsDisplayed()
        rule.onNodeWithText("El aula necesita una cuenta").assertDoesNotExist()

        rule.runOnIdle { session.setConnection(true) }
        rule.waitForIdle()

        rule.onNodeWithText("El aula necesita una cuenta").assertIsDisplayed()
        rule.onNodeWithText("Sin conexión con el aula").assertDoesNotExist()
    }
}
