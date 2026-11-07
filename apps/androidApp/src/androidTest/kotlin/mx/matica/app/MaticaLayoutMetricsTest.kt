package mx.matica.app

import android.content.pm.ActivityInfo
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
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
import kotlin.math.roundToInt
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

    /**
     * La caminata visual por el emulador encontró «Rápida» escrita una letra por renglón: la fila de
     * cuatro opciones no cabía y el `weight` del llamador se estaba ignorando en el botón. La medida
     * que delata ese fallo es la altura del propio botón.
     */
    @Test
    fun lasCuatroEtiquetasDeMovimientoCabenEnUnaLinea() {
        rule.setContent {
            MaticaApp(
                session = MaticaSession(today = LocalDate(2026, 11, 5)),
                navigator = MaticaNavigator(MaticaRoute.Settings),
            )
        }
        rule.waitForIdle()

        listOf("Ninguna", "Lenta", "Normal", "Rápida").forEach { etiqueta ->
            val cuadro = rule.onNode(hasText(etiqueta) and hasClickAction())
                .apply { performScrollTo() }
                .fetchSemanticsNode()
                .boundsInWindow
            assertTrue(
                cuadro.height <= dp(56f),
                "«$etiqueta» mide ${px(cuadro.height)} de alto: una etiqueta de una sola línea no " +
                    "necesita más que el botón de 48 dp, así que el texto se partió en renglones " +
                    "porque la fila no le dejó ancho.",
            )
        }
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

    /**
     * La frase de estrategia bajó de `titleMedium` a `bodyLarge` y eso sólo se podía comprobar a ojo
     * sobre una captura. Los dos tokens miden 16 sp con 24 sp de interlineado, así que el alto, el
     * ancho y los renglones son idénticos: lo único que los separa es el grosor del trazo, y ése sí
     * se ve en píxeles como tinta por unidad de área.
     *
     * La regla se calibra en la misma pasada y con la MISMA frase escrita como cuerpo y como titular,
     * para que la comparación no dependa de qué letras tenga cada texto. Medido aquí: 22,10 % el
     * cuerpo, 26,96 % el titular (1,22 veces más tinta) y 22,99 % el párrafo de la app, que cae a
     * 0,90 puntos del cuerpo y a 3,96 del titular.
     */
    @Test
    fun elParrafoDeEstrategiaPesaComoCuerpoYNoComoTitular() {
        val session = session()
        val frase = requireNotNull(session.startPractice(TWO_STEP, 1L)).strategyEs
        rule.setContent {
            Column {
                Box(Modifier.weight(1f)) {
                    MaticaApp(session = session, navigator = MaticaNavigator(MaticaRoute.Practice(TWO_STEP, 1L)))
                }
                // Fuera del `MaticaApp` para que la regla no herede el tema elegido en Ajustes: el
                // medidor compara tinta contra el fondo dominante de cada recorte.
                MaticaTheme(darkTheme = false) {
                    Text(frase, style = MaterialTheme.typography.bodyLarge)
                    Text(frase, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        rule.waitForIdle()

        val enPantalla = tinta(frase, 0)
        val cuerpo = tinta(frase, 1)
        val titular = tinta(frase, 2)

        // Sin esta guarda lo de abajo no significaría nada: un instrumento que no distingue los dos
        // pesos tampoco puede decir cuál de los dos tiene el párrafo.
        assertTrue(
            titular.densidad > cuerpo.densidad * RAZON_MINIMA_ENTRE_PESOS,
            "La regla no separa los dos pesos: cuerpo ${cuerpo.densidad}% contra titular " +
                "${titular.densidad}%. Con los dos tokens en 16 sp y 24 sp el grosor es la única " +
                "diferencia, y si el medidor no la ve, cualquier conclusión sobre la tinta de la app " +
                "sería ruido.",
        )
        assertTrue(
            abs(enPantalla.densidad - cuerpo.densidad) < abs(enPantalla.densidad - titular.densidad),
            "El párrafo de estrategia tiene ${enPantalla.densidad}% de tinta: más cerca del titular " +
                "(${titular.densidad}%) que del cuerpo (${cuerpo.densidad}%). Se está escribiendo con " +
                "peso de titular sobre la propia ecuación, que es justo lo que se corrigió.",
        )
        assertTrue(
            enPantalla.renglones == cuerpo.renglones,
            "La frase de la pantalla se partió en ${enPantalla.renglones} renglones y la misma frase " +
                "como cuerpo en ${cuerpo.renglones}: la comparación de tinta de arriba ya no es " +
                "manzana con manzana.",
        )
    }

    private fun session(): MaticaSession = MaticaSession(today = LocalDate(2026, 11, 5))

    /**
     * Tinta medida en píxeles del nodo: el color más repetido del recorte es el fondo, y densidad es
     * el porcentaje de píxeles del renglón más alto que se aleja de ese fondo.
     */
    private data class Tinta(val renglones: Int, val densidad: Double)

    private fun tinta(texto: String, indice: Int): Tinta {
        val nodo = rule.onAllNodes(hasText(texto, substring = true))[indice]
        runCatching { nodo.performScrollTo() }
        val caja = nodo.fetchSemanticsNode().boundsInWindow
        val raiz = rule.onRoot().fetchSemanticsNode().boundsInWindow
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val left = (caja.left - raiz.left).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (caja.top - raiz.top).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (caja.right - raiz.left).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (caja.bottom - raiz.top).roundToInt().coerceIn(top + 1, bitmap.height)

        val repeticiones = HashMap<Int, Int>()
        for (y in top until bottom step 2) {
            for (x in left until right step 2) {
                val p = bitmap.getPixel(x, y)
                val color = (Color.red(p) shl 16) or (Color.green(p) shl 8) or Color.blue(p)
                repeticiones[color] = (repeticiones[color] ?: 0) + 1
            }
        }
        val fondo = repeticiones.maxByOrNull { it.value }!!.key
        val fR = (fondo shr 16) and 0xFF
        val fG = (fondo shr 8) and 0xFF
        val fB = fondo and 0xFF

        val porFila = IntArray(bottom - top)
        for (y in top until bottom) {
            for (x in left until right) {
                val p = bitmap.getPixel(x, y)
                if (abs(Color.red(p) - fR) + abs(Color.green(p) - fG) + abs(Color.blue(p) - fB) > TINTA_MINIMA) {
                    porFila[y - top]++
                }
            }
        }
        var bandaInicio = 0
        var bandaLargo = 0
        var actual = -1
        porFila.forEachIndexed { i, conTinta ->
            if (conTinta > 2) {
                if (actual < 0) actual = i
            } else if (actual >= 0) {
                if (i - actual > bandaLargo) {
                    bandaLargo = i - actual
                    bandaInicio = actual
                }
                actual = -1
            }
        }
        if (actual >= 0 && porFila.lastIndex - actual + 1 > bandaLargo) {
            bandaLargo = porFila.lastIndex - actual + 1
            bandaInicio = actual
        }
        val enBanda = (bandaInicio until (bandaInicio + bandaLargo).coerceAtMost(porFila.size)).sumOf { porFila[it] }
        val area = (bandaLargo * (right - left)).coerceAtLeast(1)
        val filas = porFila.count { it > 2 }
        return Tinta(
            renglones = if (bandaLargo == 0) 0 else (filas.toFloat() / bandaLargo).roundToInt(),
            densidad = (100.0 * enBanda / area * 100).roundToInt() / 100.0,
        )
    }

    private companion object {
        const val TWO_STEP = "two-step-linear"

        /**
         * Cuánta más tinta debe traer el titular para que la regla merezca la pena. Medido 1,22; se
         * pide 1,10 para que la guarda siga siendo útil si el antialiasing cambia y no se apaga si el
         * peso del título se pierde.
         */
        const val RAZON_MINIMA_ENTRE_PESOS = 1.10

        /** Distancia summed por canal que separa un píxel de glifo de un píxel de fondo. */
        const val TINTA_MINIMA = 45
    }
}
