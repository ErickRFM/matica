package mx.matica.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mx.matica.design.motion.LocalMotionStyle
import mx.matica.design.motion.MotionStyle
import mx.matica.design.theme.MaticaTheme
import mx.matica.math.numeric.Rational
import mx.matica.presentation.math.CartesianGraphView
import mx.matica.presentation.math.plotSeries
import mx.matica.presentation.math.samplePlot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El trazado del plano medido en el teléfono, cuadro por cuadro, con el reloj de pruebas congelado.
 *
 * La primera muestra se toma en el cuadro 4 (64 ms de reloj de pruebas) y no en el 1: capturar antes de
 * que el nodo esté medido da `Failed to capture a node to bitmap`, observado en la propia sonda.
 *
 * Los números de abajo no son teóricos: salen de la sonda que hubo que correr primero en ese AVD
 * (`companero_v12_qa_20261009`, 1080x2340 @440 dpi), que dio 1.512 px de tinta en el cuadro 4, 2.068
 * en el 8 y 2.422 estabilizados desde el cuadro 16, con los deciles de columna rellenándose de
 * izquierda a derecha.
 */
@RunWith(AndroidJUnit4::class)
class MaticaRevealTest {

    @get:Rule
    val rule = createComposeRule()

    private data class Tinta(val total: Int, val izquierda: Int, val derecha: Int)

    @Test
    fun elPlanoSeTrazaDeIzquierdaADerecha() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MaticaTheme { Plano() } }
        val node = rule.onNodeWithTag("plano")
        avanzar(4)
        val arranque = tinta(node)
        avanzar(4)
        val temprano = tinta(node)
        avanzar(60)
        val asentada = tinta(node)

        assertTrue(
            temprano.total < asentada.total,
            "A los ocho cuadros el plano ya estaba entero (${temprano.total} de ${asentada.total} px): no hubo trazado progresivo.",
        )
        assertTrue(
            temprano.total > arranque.total,
            "Entre el cuadro 4 y el 8 la curva no avanzó (${arranque.total} → ${temprano.total} px).",
        )
        assertTrue(
            temprano.izquierda > temprano.derecha,
            "El trazado no salió de la izquierda: ${temprano.izquierda} px en las tres decimas de la " +
                "izquierda contra ${temprano.derecha} en las tres de la derecha.",
        )
        assertTrue(
            asentada.derecha > temprano.derecha,
            "La mitad derecha del plano nunca terminó de dibujarse.",
        )
        assertEquals(asentada.total, tinta(node).total, "El trazado siguió cambiando después de asentarse.")
    }

    @Test
    fun conMovimientoReducidoElPlanoApareceCompletoSinTrazarse() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaticaTheme {
                CompositionLocalProvider(LocalMotionStyle provides MotionStyle(0f)) {
                    Plano()
                }
            }
        }
        val node = rule.onNodeWithTag("plano")
        avanzar(4)
        val primerCuadro = tinta(node)
        avanzar(60)
        val asentada = tinta(node)

        assertEquals(
            asentada.total,
            primerCuadro.total,
            "Con la escala 0 el plano se fue dibujando: ${primerCuadro.total} px en el cuadro 4 " +
                "contra ${asentada.total} al terminar.",
        )
    }

    /** El plano del ejercicio, montado fuera de la pantalla para poder congelar el reloj cuadro a cuadro. */
    @Composable
    private fun Plano() {
        CartesianGraphView(
            series = plotSeries(
                points = samplePlot({ x -> x * Rational.fromLong(2L) }, Rational.ZERO, Rational.fromLong(4L), samples = 41),
                xFrom = Rational.ZERO,
                xTo = Rational.fromLong(4L),
                yFrom = Rational.ZERO,
                yTo = Rational.fromLong(8L),
            ),
            modifier = Modifier.fillMaxWidth().height(220.dp).testTag("plano"),
            xFrom = Rational.ZERO,
            xTo = Rational.fromLong(4L),
            yFrom = Rational.ZERO,
            yTo = Rational.fromLong(8L),
        )
    }

    private fun avanzar(cuadros: Int) = repeat(cuadros) { rule.mainClock.advanceTimeByFrame() }

    /** Tinta = píxeles distintos del color dominante, muestreados cada 3 px, con su reparto horizontal. */
    private fun tinta(node: SemanticsNodeInteraction): Tinta {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val paso = 3
        val frecuencia = HashMap<Int, Int>()
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                frecuencia[p] = (frecuencia[p] ?: 0) + 1
                x += paso
            }
            y += paso
        }
        val fondo = frecuencia.entries.maxByOrNull { it.value }!!.key
        var total = 0
        val deciles = IntArray(10)
        y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (bitmap.getPixel(x, y) != fondo) {
                    total++
                    deciles[(x * 10 / bitmap.width).coerceIn(0, 9)]++
                }
                x += paso
            }
            y += paso
        }
        return Tinta(total, deciles[0] + deciles[1] + deciles[2], deciles[7] + deciles[8] + deciles[9])
    }
}
