package mx.matica.app

import android.view.ViewGroup
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layout contra la ventana de verdad. Lo que se comprueba aquí no se puede ver en `commonTest`: que
 * edge-to-edge no esconda nada debajo del notch ni de la barra del sistema, que la capa oscura de la
 * hoja sea una capa (y no un bloque que se desliza) y que el atrás con el teclado puesto se gaste en
 * cerrar el teclado, no en tirar la pantalla.
 *
 * Corre sobre `MainActivity` porque los insets los reparte el sistema: un `createComposeRule` normal
 * no tiene ventana, y sin ventana no hay barra de estado que medir.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalLayoutApi::class)
class MaticaWindowInsetsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Lo que lee `WindowInsets.isImeVisible` dentro de la ventana de la app, capturado por la sonda. */
    private var tecladoAbierto by mutableStateOf(false)

    @Test
    fun elTituloDeBienvenidaNaceDebajoDeLaBarraDeEstado() {
        rule.waitForIdle()
        val decor = rule.activity.window.decorView
        val insets = ViewCompat.getRootWindowInsets(decor)
        assertTrue(
            insets != null,
            "El entorno no está preparado: la decorView no reporta WindowInsetsCompat, así que no hay " +
                "inset real con el que comparar.",
        )
        val barraDeEstado = requireNotNull(insets).getInsets(WindowInsetsCompat.Type.statusBars()).top
        assertTrue(
            barraDeEstado > 0,
            "El entorno no está preparado: la barra de estado mide 0 px y la comparación saldría vacía.",
        )

        val titulo = rule.onNode(hasText(TITULO_BIENVENIDA) and isHeading()).fetchSemanticsNode().boundsInWindow
        assertTrue(
            titulo.top >= barraDeEstado,
            "Edge-to-edge sin insets en el host: «$TITULO_BIENVENIDA» pintó en y=${titulo.top} con la " +
                "barra de estado ocupando los $barraDeEstado px de arriba, es decir tapado por el notch.",
        )

        val ultimaAccion = rule.onNodeWithText("Entrar con una cuenta existente")
        ultimaAccion.performScrollTo()
        val ventana = rule.activity.window.decorView.height
        val limiteInferior = ventana - requireNotNull(insets).getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val fondo = ultimaAccion.fetchSemanticsNode().boundsInWindow.bottom
        assertTrue(
            fondo <= limiteInferior,
            "La última acción de la pantalla de bienvenida terminó en y=$fondo dentro de una ventana de " +
                "$ventana px: el contenido sigue metido bajo la barra de navegación del sistema.",
        )
    }

    @Test
    fun tocarElScrimCierraLaHojaYDejaElReproductorMontado() {
        irAlReproductor()

        // «Falta la línea» vive en un `remember` a secas del reproductor: si la pantalla de abajo se
        // remontara al cerrar la hoja, el texto se perdería, y ésa es justamente la regresión a cazar.
        pulsar("Comprobar paso")
        rule.onNodeWithText("Falta la línea").assertIsDisplayed()

        pulsar("Pista")
        rule.onNodeWithText("Mostrar una pista más").assertIsDisplayed()

        val scrim = nodoDelScrim()
        val etiqueta = scrim.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label
        assertTrue(
            etiqueta == ETIQUETA_SCRIM,
            "La capa oscura se anuncia a TalkBack como <$etiqueta>: sin la etiqueta propia el estudiante " +
                "no puede saber que ese toque cierra las pistas.",
        )

        // El toque tiene que caer en un hueco de verdad: la hoja se dibuja encima del scrim, así que un
        // punto que pise un botón se lo lleva ese botón y no la capa oscura. Se busca el primer punto del
        // scrim que no esté dentro del rectángulo de ningún otro nodo clickeable y se golpea ahí.
        val cuadro = scrim.fetchSemanticsNode().boundsInWindow
        val obstructores = nodosConAccionDeToque().map { it.boundsInWindow }.filter { it != cuadro }
        val libre = puntoLibreDelScrim(cuadro, obstructores)
        assertTrue(
            libre != null,
            "Ningún punto de la capa oscura está libre de botones ($obstructores): la hoja tapa todo el " +
                "scrim, y cerrar tocando fuera sería un golpe de suerte y no una capacidad de la app.",
        )
        // Las coordenadas de `click` son locales al nodo: la capa empieza debajo de la barra de estado.
        scrim.performTouchInput {
            click(Offset(requireNotNull(libre).x - cuadro.left, libre.y - cuadro.top))
        }
        rule.waitForIdle()

        rule.onNodeWithText("Mostrar una pista más").assertDoesNotExist()
        rule.onNodeWithText("Comprobar paso").assertIsDisplayed()
        rule.onNodeWithText("Falta la línea").assertIsDisplayed()
        // Fundida la capa sale del árbol: mientras siga montada se traga los toques de la pantalla de abajo.
        val seFue = runCatching {
            rule.waitUntil(MS_SCRIM) {
                nodosConAccionDeToque().none {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == ETIQUETA_SCRIM
                }
            }
        }.isSuccess
        assertTrue(
            seFue,
            "Cerrada la hoja quedó una capa con «$ETIQUETA_SCRIM» en el árbol: el scrim sigue tapando la pantalla.",
        )
    }

    @Test
    fun elAtrasConElTecladoAbiertoCierraElTecladoYNoTiraLaPantalla() {
        // `WindowInsets.isImeVisible` sólo se puede leer dentro de una composición y es exactamente el
        // dato que decide si el atrás se gasta en el teclado: la sonda lo lee en la misma ventana.
        instalarSondaDeTeclado()
        irAlReproductor()

        val campo = rule.onAllNodes(hasSetTextAction()).onFirst()
        campo.performScrollTo()
        campo.performClick()
        rule.waitForIdle()

        val asomo = runCatching { rule.waitUntil(MS_TECLADO) { tecladoAbierto } }.isSuccess
        assertTrue(
            asomo,
            "El entorno no está preparado: con un campo enfocado el emulador no enseñó el teclado (el " +
                "host siguió leyendo WindowInsets.isImeVisible = false en " + MS_TECLADO + " ms), así que " +
                "aquí no se puede probar que el atrás cierre el teclado antes que la pantalla.",
        )

        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Comprobar paso").assertIsDisplayed()
        // La lista de lecciones es la pantalla que quedaría detrás: si el atrás se tiró el reproductor, aparece.
        rule.onAllNodes(hasText("Tres ejercicios por lección", substring = true)).assertCountEquals(0)

        val seGuardo = runCatching { rule.waitUntil(MS_TECLADO) { !tecladoAbierto } }.isSuccess
        assertTrue(
            seGuardo,
            "El atrás con el teclado abierto tenía que guardarlo y seguir en el reproductor: " +
                "WindowInsets.isImeVisible no volvió a false.",
        )
    }

    /**
     * Ruta real dentro de la activity. El reproductor es la única pantalla de la app con un campo
     * habilitado, y sin campo no hay teclado que probar; cada paso se valida con su propia marca.
     */
    private fun irAlReproductor() {
        rule.waitForIdle()
        pulsar("Entrar con una cuenta existente")
        rule.onNodeWithText("Correo de la escuela").assertIsDisplayed()
        pulsar("Seguir con perfil local")
        rule.onNodeWithText("Abrir mi ruta").assertIsDisplayed()
        pulsar("Abrir mi ruta")
        // Sin diagnóstico el único nudo desbloqueado del grafo es la raíz: «Operaciones con enteros».
        pulsar("Operaciones con enteros")
        pulsar("Ver lecciones")
        rule.onAllNodesWithText("Abrir").onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Comprobar paso").assertIsDisplayed()
    }

    private fun pulsar(texto: String) {
        val nodo = rule.onNodeWithText(texto)
        nodo.performScrollTo()
        nodo.performClick()
        rule.waitForIdle()
    }

    /** El scrim es la capa clickeable más grande del árbol: ninguna tarjeta ni botón le llega al ancho. */
    private fun nodoDelScrim(): SemanticsNodeInteraction {
        val candidatos = nodosConAccionDeToque()
        assertTrue(
            candidatos.isNotEmpty(),
            "La hoja abrió sin ninguna capa clickeable detrás: no hay nada que tocar para cerrarla.",
        )
        val areas = candidatos.map { val cuadro = it.boundsInWindow; cuadro.width * cuadro.height }
        val indice = areas.indexOf(areas.max())
        val cuadro = candidatos[indice].boundsInWindow
        val ventana = rule.onRoot().fetchSemanticsNode().boundsInWindow
        assertTrue(
            cuadro.width >= ventana.width * 0.9f && cuadro.height >= ventana.height * 0.5f,
            "El nodo clickeable más grande mide ${cuadro.width} x ${cuadro.height} en una ventana de " +
                "${ventana.width} x ${ventana.height}: la capa oscura no cubre la pantalla, así que " +
                "cerrar por fuera de la hoja sería un golpe de suerte.",
        )
        return rule.onAllNodes(hasClickAction())[indice]
    }

    private fun nodosConAccionDeToque(): List<SemanticsNode> =
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()

    /**
     * Primer punto de la capa oscura que no cae dentro de ningún botón. Se barren franjas de arriba abajo
     * y, dentro de cada una, de izquierda a derecha: el scrim ocupa casi toda la ventana, así que siempre
     * queda algún hueco entre las tarjetas; si no lo hay, la hoja está tapando la capa y eso también es
     * un fallo, pero lo tiene que decir la prueba, no un falso positivo de toque.
     */
    private fun puntoLibreDelScrim(cuadro: Rect, obstructores: List<Rect>): Offset? {
        for (alto in listOf(0.04f, 0.12f, 0.2f, 0.3f, 0.42f, 0.55f, 0.7f, 0.85f, 0.95f)) {
            for (ancho in listOf(0.05f, 0.25f, 0.5f, 0.75f, 0.95f)) {
                val punto = Offset(cuadro.left + cuadro.width * ancho, cuadro.top + cuadro.height * alto)
                if (obstructores.none { it.contains(punto) }) return punto
            }
        }
        return null
    }

    private fun instalarSondaDeTeclado() {
        rule.runOnUiThread {
            val sonda = ComposeView(rule.activity).apply {
                setContent {
                    val visible = WindowInsets.isImeVisible
                    SideEffect { tecladoAbierto = visible }
                }
            }
            // 1 px y sin dibujar nada: la sonda sólo mira los insets de la ventana de la activity.
            (rule.activity.window.decorView as ViewGroup).addView(sonda, 1, 1)
        }
        rule.waitForIdle()
    }

    private companion object {
        const val TITULO_BIENVENIDA = "Mática"

        /** Etiqueta que `MaticaApp` pone en el scrim; repetida a mano porque es privada del módulo. */
        const val ETIQUETA_SCRIM = "Cerrar las pistas"
        const val MS_TECLADO = 8_000L

        /** Tiempo de sobra para que la capa oscura termine de fundirse y salga del árbol. */
        const val MS_SCRIM = 3_000L
    }
}
