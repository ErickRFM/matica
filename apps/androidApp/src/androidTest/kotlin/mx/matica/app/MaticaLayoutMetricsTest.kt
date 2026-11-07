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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.design.theme.MaticaTheme
import mx.matica.math.skills.SkillId
import mx.matica.presentation.app.MaticaApp
import mx.matica.presentation.app.MaticaCaption
import mx.matica.presentation.app.MaticaProgress
import mx.matica.presentation.app.MaticaScreen
import mx.matica.presentation.app.MaticaSession
import mx.matica.presentation.app.ThemeMode
import mx.matica.presentation.math.prettyMath
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

    /** La misma conversión que `px`, pero en número: las aserciones comparan dp, no cadenas. */
    private fun dpDePx(value: Float): Int = (value / rule.density.density).roundToInt()

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

    /**
     * Ritmo de la fila de lección, medido en la AVD de 392 dp de ancho. Antes de corregir, el título
     * y la ecuación de debajo estaban a 0 dp (se leían como un solo bloque) y el botón «Abrir» medía
     * 88 dp de ancho porque el rótulo llevaba su propio padding de 24 dp dentro del botón.
     * Corregido: 4 dp entre título y ecuación, botón de 64x48 dp.
     */
    @Test
    fun lasFilasDeLeccionTienenRitmoYTargetTactil() {
        val ses = session()
        rule.setContent {
            MaticaApp(
                session = ses,
                navigator = MaticaNavigator(MaticaRoute.LessonList(SkillId.BALANCE_PRINCIPLE)),
            )
        }
        rule.waitForIdle()
        val leccion = ses.lessonsFor(SkillId.BALANCE_PRINCIPLE).first()

        // Se desplaza una sola vez y luego se leen las tres piezas en el mismo desplazamiento, y con
        // `positionInWindow` en vez de `boundsInWindow`: los recuadros se recortan contra el borde
        // visible, y con la fila pegada al borde inferior el recorte hacía terminar el título y
        // empezar la ecuación en la misma y (0 dp falsos). La posición no se recorta.
        val ecuacionNodo = rule.onAllNodes(hasText(leccion.plan.equationText, substring = true))[0]
        runCatching { ecuacionNodo.performScrollTo() }
        rule.waitForIdle()
        val titulo = rule.onAllNodes(hasText(leccion.title))[0].fetchSemanticsNode()
        val ecuacion = ecuacionNodo.fetchSemanticsNode()
        val boton = rule.onAllNodes(hasText("Abrir"))[0].fetchSemanticsNode()

        val abajoDelTitulo = titulo.positionInWindow.y + titulo.size.height
        val hueco = dpDePx(ecuacion.positionInWindow.y - abajoDelTitulo)
        assertTrue(
            hueco in 2..6,
            "El título y la ecuación de la misma fila están a $hueco dp: sin aire, la pareja se lee " +
                "como un solo bloque y el estudiante no distingue qué es el nombre y qué es la cuenta.",
        )
        val alto = dpDePx(boton.size.height.toFloat())
        assertTrue(alto >= 48, "El botón «Abrir» mide $alto dp de alto; el target táctil del sistema es de 48 dp.")
        val ancho = dpDePx(boton.size.width.toFloat())
        assertTrue(
            ancho <= 80,
            "El botón «Abrir» mide $ancho dp de ancho: el rótulo volvió a llevar padding propio y la " +
                "fila entera se desbalancea contra el texto.",
        )
    }

    /**
     * Las cuatro opciones del diagnóstico. Medido en la misma AVD: con 8 dp se leían como una lista
     * densa; con 12 dp cada alternativa conserva su caja de 48 dp y el hueco entre ellas.
     */
    @Test
    fun lasOpcionesDelDiagnosticoRespiranYSonTocables() {
        val ses = session()
        rule.setContent { MaticaApp(session = ses, navigator = MaticaNavigator(MaticaRoute.Diagnosis)) }
        rule.waitForIdle()
        val pregunta = (ses.currentDiagnosis() ?: ses.startDiagnosis()).pendingQuestions().first()

        val cajas = pregunta.options.map { option ->
            runCatching {
                rule.onAllNodes(hasText(prettyMath(option)))[0]
                    .fetchSemanticsNode().boundsInWindow
            }.getOrNull()
        }
        val visibles = cajas.filterNotNull()
        assertTrue(
            visibles.size == pregunta.options.size,
            "Sólo ${visibles.size} de ${pregunta.options.size} opciones están en pantalla: " +
                "${pregunta.options}; una alternativa que no se puede medir es una alternativa que no se puede tocar.",
        )
        for (i in 1 until visibles.size) {
            val hueco = dpDePx(visibles[i].top - visibles[i - 1].bottom)
            assertTrue(
                hueco >= 12,
                "Entre la opción $i y la ${i + 1} hay $hueco dp: con menos de 12 dp las cuatro " +
                    "alternativas forman una lista densa en vez de cuatro decisiones.",
            )
        }
        visibles.forEach {
            assertTrue(dpDePx(it.height) >= 48, "Una opción mide ${dpDePx(it.height)} dp de alto; el target táctil es 48 dp.")
        }
    }

    /**
     * El rechazo del motor se pinta una sola vez y el campo queda marcado en rojo.
     *
     * Medido antes de corregir (AVD 1080x2340, 2026-11-06): la frase de 230 caracteres aparecía
     * duplicada, pegada una bajo otra, porque el campo y la tarjeta recibían el mismo `feedback`.
     * Después de separar el motivo corto del veredicto completo, el mismo camino pinta el motivo corto
     * en el campo y el veredicto entero una sola vez, y sobre la caja del campo (328x56 dp) hay 7.811
     * píxeles del color de error de 15.651 en toda la pantalla: el trazo del borde, que es
     * 2·(902+154) px de perímetro por ~3,7 px de grosor.
     */
    @Test
    fun elRechazoDelMotorSePintaUnaSolaVezYElCampoQuedaEnRojo() {
        val ses = session()
        // El tema se fija aquí y no se deja al `SYSTEM`: el color del trazo de error es `Danger` en
        // claro y `DangerLight` en oscuro, así que contando un solo rojo la prueba dependía del modo
        // noche del aparato. Medido: en el AVD recién formateado —que arranca en claro— el condeo
        // daba 0 aunque el borde estuviera teñido.
        ses.updateSettings { it.copy(themeMode = ThemeMode.LIGHT) }
        rule.setContent {
            MaticaApp(
                session = ses,
                navigator = MaticaNavigator(MaticaRoute.LessonPlayer(SkillId.BALANCE_PRINCIPLE, 1)),
            )
        }
        rule.waitForIdle()

        val campo = rule.onAllNodes(hasSetTextAction())[0]
        runCatching { campo.performScrollTo() }
        rule.onNode(hasText("Comprobar paso")).performScrollTo().performClick()
        rule.waitForIdle()

        // Sin línea escrita: hay aviso, pero el campo no está en rojo. El aviso nombra también la
        // opción, porque el reproductor ya ofrece las alternativas de primer paso.
        assertTrue(
            textosEnPantalla().contains("Elige una opción o escribe la expresión a la que llegaste."),
            "Con el campo vacío la pantalla no explicó qué falta: ${textosEnPantalla()}",
        )

        campo.performTextInput("x + 99 = 999")
        rule.waitForIdle()
        rule.onNode(hasText("Comprobar paso")).performClick()
        rule.waitForIdle()

        val textos = textosEnPantalla()
        val duplicados = textos.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(
            duplicados.isEmpty(),
            "La misma frase se pinta dos veces en la práctica: $duplicados. El campo y la tarjeta no " +
                "deben recibir el mismo texto; uno lleva el motivo corto y la otra el veredicto entero.",
        )
        val largo = textos.count { it.contains("aplicaste operaciones distintas a cada lado") }
        assertTrue(
            largo == 1,
            "El veredicto completo se pintó $largo veces; debe verse una sola, en la tarjeta del veredicto.",
        )
        val corto = textos.count { it == "La operación no se aplicó igual a los dos lados" }
        assertTrue(
            corto == 1,
            "El motivo corto del campo debe aparecer una sola vez, junto a lo escrito (contó $corto).",
        )

        runCatching { campo.performScrollTo() }
        rule.waitForIdle()
        val caja = campo.fetchSemanticsNode().boundsInWindow
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val rojoEnElCampo = cuentaDe(bitmap, caja, mx.matica.design.tokens.MaticaPalette.Danger.toArgb())
        assertTrue(
            rojoEnElCampo > 1_000,
            "El campo rechazado no está teñido de error: sólo $rojoEnElCampo píxeles del color de error " +
                "dentro de su caja. Sin ese trazo, el estudiante tiene que leer el párrafo para saber " +
                "qué campo está mal.",
        )
    }

    /**
     * Cuántos píxeles del área son exactamente del color dado. Una línea de coordenadas no basta para
     * saber si el borde del campo está teñido de error: la caja se mueve con el teclado y el trazo
     * tiene tres píxeles. Contar el color dentro de la caja responde la pregunta; la primera sonda
     * muestreaba a 1, 4 y 9 px POR FUERA de la caja y por eso sólo devolvía el color de la tarjeta,
     * porque `boundsInWindow` ya viene en píxeles de la ventana y el trazo se dibuja dentro.
     */
    private fun cuentaDe(bitmap: android.graphics.Bitmap, caja: Rect, argb: Int): Int {
        val x0 = caja.left.roundToInt().coerceIn(0, bitmap.width - 1)
        val x1 = caja.right.roundToInt().coerceIn(x0, bitmap.width - 1)
        val y0 = caja.top.roundToInt().coerceIn(0, bitmap.height - 1)
        val y1 = caja.bottom.roundToInt().coerceIn(y0, bitmap.height - 1)
        var vistos = 0
        for (y in y0..y1) {
            for (x in x0..x1) {
                if (bitmap.getPixel(x, y) == argb) vistos += 1
            }
        }
        return vistos
    }

    private fun textosEnPantalla(): List<String> =
        rule.onAllNodes(hasText("", substring = true))
            .fetchSemanticsNodes()
            .flatMap { nodo -> nodo.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            .filter { it.isNotBlank() }

    /**
     * La flecha de regreso medida píxel a píxel. La sonda devolvía tinta de la columna 44 a la 87 de
     * un recorte de 132 px, con las 44 columnas tintadas y el renglón más largo en 44 px: el palo va
     * corrido de un extremo al otro, sin huecos, y las barbas caen entre la punta y la mitad del palo.
     * Con el signo del ala invertido la tinta arrancaba cinco dp antes de que empezara el palo, así
     * que el glifo no era una flecha sino una raya de 13 dp con dos trazos sueltos a la izquierda.
     */
    @Test
    fun laFlechaRegresaTieneLaPuntaALaIzquierdaDelPalo() {
        rule.setContent {
            MaticaApp(
                session = session(),
                navigator = MaticaNavigator(MaticaRoute.RouteMap),
            )
        }
        rule.waitForIdle()
        val bitmap = rule.onAllNodes(hasContentDescription("Regresa"))[0].captureToImage().asAndroidBitmap()
        val fondo = bitmap.getPixel(0, 0)
        fun tinta(x: Int, y: Int): Boolean = bitmap.getPixel(x, y) != fondo

        val conTinta = (0 until bitmap.width).filter { x -> (0 until bitmap.height).any { y -> tinta(x, y) } }
        assertTrue(
            conTinta.isNotEmpty(),
            "El glifo «Regresa» no pinta ni un píxel: sin flecha el estudiante no ve cómo salir del mapa.",
        )
        val primera = conTinta.first()
        val ultima = conTinta.last()
        assertTrue(
            conTinta.size == ultima - primera + 1,
            "De la columna $primera a la $ultima hay ${ultima - primera + 1 - conTinta.size} columnas sin " +
                "tinta: el palo de la flecha está partido y lo que se ve son dos trazos cortos.",
        )

        val filaPalo = (0 until bitmap.height).maxByOrNull { y -> (0 until bitmap.width).count { x -> tinta(x, y) } }!!
        val inicioPalo = (primera..ultima).first { x -> tinta(x, filaPalo) }
        val finPalo = (primera..ultima).last { x -> tinta(x, filaPalo) }
        val largoPalo = finPalo - inicioPalo + 1
        assertTrue(
            dpDePx(largoPalo.toFloat()) >= 12,
            "El palo de la flecha mide ${dpDePx(largoPalo.toFloat())} dp: por debajo de los doce es un " +
                "puntito, no una dirección.",
        )
        assertTrue(
            dpDePx((inicioPalo - primera).toFloat()) <= 1,
            "Hay ${dpDePx((inicioPalo - primera).toFloat())} dp de tinta antes de que arranque el palo " +
                "($primera px frente a $inicioPalo px): las barbas se abren hacia afuera de la punta, y eso " +
                "se lee como dos pelos sueltos en vez de una cabeza de flecha.",
        )

        // El grosor del palo también es tinta «fuera de la fila del centro», así que la cabeza no se
        // busca por columnas sueltas sino por cuánto baja y sube la tinta en cada columna: donde hay
        // barbas la columna mide el doble del grueso del palo, y donde sólo hay raya mide el grueso.
        fun extension(x: Int): Int {
            val filas = (0 until bitmap.height).filter { y -> tinta(x, y) }
            return if (filas.isEmpty()) 0 else filas.last() - filas.first() + 1
        }
        val grosor = (primera..ultima).map { x -> extension(x) }.sorted().let { it[it.size / 2] }
        val anchoBanda = ultima - primera + 1
        val cabeza = (primera..ultima).filter { x -> extension(x) >= grosor * 2 }
        assertTrue(
            cabeza.isNotEmpty(),
            "Ninguna columna de la flecha abre más de $grosor px, su propio grueso: no hay cabeza, sólo " +
                "una raya, y el botón de atrás se confunde con cualquier separador.",
        )
        val nace = cabeza.first() - primera
        val muere = ultima - cabeza.last()
        assertTrue(
            nace * 4 <= anchoBanda,
            "La tinta de las barbas aparece hasta ${dpDePx(nace.toFloat())} dp dentro del palo " +
                "($nace px de una banda de $anchoBanda): la punta no está en el extremo izquierdo, así " +
                "que el glifo no cierra en punta y nadie sabe hacia dónde mira.",
        )
        assertTrue(
            muere * 3 >= anchoBanda,
            "Detrás de las barbas sólo quedan ${dpDePx(muere.toFloat())} dp de palo suelto ($muere px de " +
                "$anchoBanda): las barbas llegan hasta el otro extremo, o sea que la punta está a la " +
                "derecha y el botón dice «adelante», no «regresa».",
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
