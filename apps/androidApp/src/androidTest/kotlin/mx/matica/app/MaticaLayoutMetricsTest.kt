package mx.matica.app

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.design.theme.MaticaTheme
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaCaption
import mx.matica.presentation.app.MaticaProgress
import mx.matica.presentation.app.MaticaScreen
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Medidas de layout leídas en píxeles del dispositivo: lo que mide un dedo, dónde cae el borde del
 * contenido y cuánto hueco hay entre un título y lo que titula. Aquí no se afirma «se ve bien»: se
 * compara un rectángulo contra el token que debería producirlo.
 */
@RunWith(AndroidJUnit4::class)
class MaticaLayoutMetricsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun devuelveLaActividadEnHorizontal() {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun dp(value: Float): Float = with(rule.density) { value.dp.toPx() }

    private fun px(value: Float): String = "${(value / rule.density.density).toInt()} dp"

    @Test
    fun losControlesTocablesLleganALosCuarentaYOchoDpDeMaterial() {
        var route by mutableStateOf<MaticaRoute>(MaticaRoute.Practice("two-step-linear", 1L))
        val session = MaticaSession(today = LocalDate(2026, 11, 5))
        rule.setContent { MaticaApp(session = session, navigator = MaticaNavigator(route)) }
        rule.waitForIdle()

        listOf("Marca anterior", "Marca siguiente").forEach { etiqueta ->
            rule.onNode(hasContentDescription(etiqueta)).apply { performScrollTo() }
                .assertAtLeastTouchTarget("la flecha «$etiqueta» de la recta")
        }

        rule.runOnIdle { route = MaticaRoute.Settings }
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Regresa")).assertAtLeastTouchTarget("el botón de atrás")
        rule.onNode(hasText("Sistema") and hasClickAction()).assertAtLeastTouchTarget("un botón de tema")
    }

    /**
     * El tope de 640 dp sólo se puede medir en una ventana que realmente lo supere. En el retrato de
     * este AVD la ventana hace 392,7 dp, así que un `Box(width = 900.dp)` no sirve: la medida se
     * recorta contra el máximo heredado y el tope nunca se ejercita (así lo mostró la sonda: caja,
     * columna y párrafo medían 392 dp). Girar a horizontal da una ventana de ~800 dp real.
     */
    @Test
    fun enUnaVentanaAnchaElCuerpoSeTopeaYQuedaCentrado() {
        val parrafo = "un párrafo lo bastante largo como para que sin tope cruzara toda la ventana"
        giraEnHorizontal()
        rule.setContent {
            MaticaTheme(darkTheme = false) {
                MaticaScreen(title = "Ajustes", session = MaticaSession(today = LocalDate(2026, 11, 5))) {
                    MaticaCaption(parrafo)
                }
            }
        }
        rule.waitForIdle()

        // Se mide el área de composición y no el `decorView`: en horizontal este AVD reserva 48 dp a
        // la barra de navegación, y ese hueco no es ancho disponible para el cuerpo.
        val anchoRaiz = rule.onRoot().fetchSemanticsNode().boundsInWindow.width
        val bounds = rule.onNode(hasText(parrafo)).fetchSemanticsNode().boundsInWindow
        assertTrue(
            anchoRaiz > dp(640f),
            "El área de composición mide ${px(anchoRaiz)}: sin superarla, el tope de 640 dp no se " +
                "ejercita y esta prueba no mediría nada. La rotación no ocurrió.",
        )
        // El cuerpo llega justo al tope menos los 16 dp de padding por lado: si mide menos, el tope
        // se aplicó dos veces; si mide más, no se aplicó.
        val esperado = dp(640f) - 2f * dp(16f)
        assertTrue(
            abs(bounds.width - esperado) <= 1.5f,
            "El cuerpo midió ${px(bounds.width)} dentro de una ventana de ${px(anchoRaiz)}; el tope " +
                "de 640 dp menos los 16 dp de padding por lado debería dar ${px(esperado)}. Sin tope, " +
                "el renglón cruza toda la tableta y el ojo pierde el inicio del siguiente.",
        )
        assertTrue(
            abs(bounds.left - (anchoRaiz - bounds.right)) <= 1.5f,
            "El cuerpo no quedó centrado en la ventana de ${px(anchoRaiz)}: $bounds, con " +
                "${px(bounds.left)} libres a la izquierda y ${px(anchoRaiz - bounds.right)} a la derecha.",
        )
    }

    @Test
    fun elTituloDeApartadoViveMasCercaDeLoQueTitulaQueDelApartadoSiguiente() {
        rule.setContent {
            MaticaApp(
                session = MaticaSession(today = LocalDate(2026, 11, 5)),
                navigator = MaticaNavigator(MaticaRoute.Settings),
            )
        }
        rule.waitForIdle()

        val titulo = rule.onNode(hasText("Tema")).fetchSemanticsNode().boundsInWindow
        val contenido = rule.onNode(hasText("Sistema") and hasClickAction())
            .apply { performScrollTo() }
            .fetchSemanticsNode()
            .boundsInWindow
        val siguienteTitulo = rule.onNode(hasText("Movimiento")).fetchSemanticsNode().boundsInWindow

        val dentro = contenido.top - titulo.bottom
        val fuera = siguienteTitulo.top - contenido.bottom

        assertTrue(
            abs(dentro - dp(8f)) <= 1f,
            "Entre la cabecera y SU contenido hay ${px(dentro)}, no los 8 dp del token de apartado.",
        )
        assertTrue(
            abs(fuera - dp(16f)) <= 1f,
            "Entre apartados hay ${px(fuera)}, no los 16 dp del contenedor de la pantalla.",
        )
        assertTrue(
            dentro < fuera,
            "Ritmo invertido: la cabecera está más lejos de lo que titula (${px(dentro)}) que un " +
                "apartado del siguiente (${px(fuera)}).",
        )
    }

    @Test
    fun laBarraDeProgresoSeAnunciaComoProporcion() {
        rule.setContent {
            MaticaTheme(darkTheme = false) { MaticaProgress(done = 3, total = 15, label = "Diagnóstico") }
        }
        rule.waitForIdle()

        val esperado = ProgressBarRangeInfo(current = 0.2f, range = 0f..1f, steps = 14)
        val found = runCatching { rule.onNode(hasProgressBarRangeInfo(esperado)).assertExists() }.isSuccess
        assertTrue(
            found,
            "La barra de progreso no expuso su proporción al lector de pantalla: un progreso que sólo " +
                "se ve no es un progreso que se anuncia.",
        )
    }

    /** Gira la actividad y espera al re-layout: sin la ventana ancha real, el tope no se ejercita. */
    private fun giraEnHorizontal() {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val limite = System.currentTimeMillis() + 5_000L
        var ancho = 0f
        while (System.currentTimeMillis() < limite) {
            rule.runOnIdle { ancho = rule.activity.window.decorView.width.toFloat() }
            if (ancho > dp(640f)) return
            Thread.sleep(50L)
        }
        assertTrue(false, "Tras 5 s la actividad sigue en ${px(ancho)} de ancho; no hay ventana ancha que medir.")
    }

    private fun SemanticsNodeInteraction.assertAtLeastTouchTarget(que: String) {
        val bounds: Rect = fetchSemanticsNode().boundsInWindow
        val minimo = dp(48f) - 1f
        assertTrue(
            bounds.width >= minimo && bounds.height >= minimo,
            "$que mide ${px(bounds.width)} × ${px(bounds.height)}; Material fija 48 dp como objetivo " +
                "táctil mínimo.",
        )
    }
}
