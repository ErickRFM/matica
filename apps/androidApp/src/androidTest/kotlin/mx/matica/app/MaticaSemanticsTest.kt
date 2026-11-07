package mx.matica.app

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.design.theme.MaticaTheme
import mx.matica.math.skills.SkillId
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.components.MaticaChipPalette
import mx.matica.presentation.components.MaticaCountChip
import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo que TalkBack recibe de estos tres sitios, medido en el compositor del teléfono y no en el código:
 * que el campo de texto se nombre a sí mismo, que el contador animado no anuncie sus fotogramas
 * intermedios y que el botón de un estado no prometa una acción que no hace.
 */
@RunWith(AndroidJUnit4::class)
class MaticaSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun session() = MaticaSession(today = LocalDate(2026, 11, 5))

    @Test
    fun elCampoDeTextoSeNombreASiMismo() {
        rule.setContent {
            MaticaApp(session = session(), navigator = MaticaNavigator(MaticaRoute.LessonPlayer(SkillId.BALANCE_PRINCIPLE, 1)))
        }
        rule.waitForIdle()

        // El rótulo visible sigue siendo un `Text` aparte; lo que se comprueba aquí es que el campo,
        // al recibir el foco, lleve su propio nombre en la semántica.
        rule.onNode(hasSetTextAction() and hasContentDescription("Siguiente línea")).assertIsDisplayed()
    }

    @Test
    fun elChipExponeElValorFinalYNoLosCuadrosDeLaAnimacion() {
        rule.setContent {
            MaticaTheme(darkTheme = false) {
                Box {
                    MaticaCountChip(
                        value = 250,
                        unit = "XP",
                        glyph = MaticaIcon.XP,
                        pair = MaticaChipPalette.xp,
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onNode(hasContentDescription("250 XP")).assertExists("el chip no expone su valor final en la semántica")
        val interpolados = textosPintados().filter { it.endsWith(" XP") }
        assertTrue(
            interpolados.isEmpty(),
            "El chip expone el número interpolado «${interpolados.firstOrNull()}»: un lector de pantalla " +
                "leería cada cuadro del salto.",
        )
        val region = rule.onAllNodes(SemanticsMatcher("región viva") { node ->
            node.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite
        }).fetchSemanticsNodes()
        assertTrue(region.isNotEmpty(), "El chip no marcó su valor como región viva; el cambio no se anuncia.")
    }

    @Test
    fun elBotonDelEstadoDiceLoQueHace() {
        val navigator = MaticaNavigator(MaticaRoute.Home)
        navigator.go(MaticaRoute.LessonList(SkillId.BALANCE_PRINCIPLE))
        // Lección 99: no la genera nadie, así que la pantalla cae en el estado sin lección.
        navigator.go(MaticaRoute.LessonPlayer(SkillId.BALANCE_PRINCIPLE, 99))
        rule.setContent { MaticaApp(session = session(), navigator = navigator) }
        rule.waitForIdle()

        rule.onNodeWithText("Volver a la lista").performScrollTo().assertIsDisplayed()
        val prometeReintentar = textosPintados().any { it == "Reintentar" }
        assertFalse(prometeReintentar, "El botón deshace la entrada pero se sigue llamando «Reintentar».")

        rule.onNodeWithText("Volver a la lista").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Tres ejercicios por lección", substring = true)
            .assertExists("el botón no devolvió a la lista de lecciones")
    }

    private fun textosPintados(): List<String> =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { parte -> parte.text } }
            .filter { it.isNotBlank() }
            .distinct()
}
