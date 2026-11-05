package mx.matica.design.a11y

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import mx.matica.design.tokens.MaticaPalette

/**
 * Las cifras de contraste se verifican contra una medición independiente hecha fuera del motor
 * (WCAG 2.2 calculado con Python sobre los mismos hex). Si una de las dos implementaciones se
 * mueve, estas aserciones lo detectan.
 */
class ContrastTest {
    private fun assertRatio(foreground: Color, background: Color, expected: Double) {
        assertEquals(expected, Contrast.ratio(foreground, background), absoluteTolerance = 0.02)
    }

    @Test
    fun luminanciaDeLosExtremos() {
        assertEquals(0.0, Contrast.luminance(Color.Black), absoluteTolerance = 1e-9)
        assertEquals(1.0, Contrast.luminance(Color.White), absoluteTolerance = 1e-9)
    }

    @Test
    fun negroSobreBlancoEsElMaximo() {
        assertRatio(Color.Black, Color.White, 21.0)
        assertEquals(1.0, Contrast.ratio(Color.Red, Color.Red), absoluteTolerance = 1e-9)
    }

    @Test
    fun laMedidaNoDependeDelOrden() {
        val a = Contrast.ratio(MaticaPalette.Ink, MaticaPalette.Paper)
        val b = Contrast.ratio(MaticaPalette.Paper, MaticaPalette.Ink)
        assertEquals(a, b, absoluteTolerance = 1e-9)
    }

    @Test
    fun anclasMedidasFueraDelMotor() {
        assertRatio(MaticaPalette.Ink, MaticaPalette.Paper, 15.16)
        assertRatio(MaticaPalette.Paper, MaticaPalette.Blue, 4.63)
        assertRatio(MaticaPalette.VioletInk, MaticaPalette.VioletSoft, 10.40)
        assertRatio(MaticaPalette.Night, MaticaPalette.BlueLight, 7.93)
        assertRatio(MaticaPalette.TealInk, MaticaPalette.Teal, 6.87)
        assertRatio(MaticaPalette.MistText, MaticaPalette.Night, 15.85)
        assertRatio(MaticaPalette.Danger, MaticaPalette.Mist, 6.74)
        assertRatio(MaticaPalette.InkSoft, MaticaPalette.BlueMist, 6.29)
        assertRatio(MaticaPalette.MistTextSoft, MaticaPalette.NightRaised, 6.52)
        assertRatio(MaticaPalette.BlueSoft, MaticaPalette.BlueDeep, 7.94)
        assertRatio(MaticaPalette.Mint, MaticaPalette.GreenInk, 8.71)
        assertRatio(MaticaPalette.Ink, MaticaPalette.Lime, 11.19)
        assertRatio(MaticaPalette.Night, MaticaPalette.Violet, 5.15)
    }

    @Test
    fun elVioletaDelDisenoNoAguantaTextoBlanco() {
        assertRatio(Color.White, MaticaPalette.Violet, 3.51)
        assertFalse(Contrast.meetsAa(Color.White, MaticaPalette.Violet))
        assertTrue(Contrast.meetsAa(Color.White, MaticaPalette.Violet, largeText = true))
    }

    @Test
    fun umbralesDeAa() {
        assertEquals(4.5, Contrast.threshold(largeText = false), absoluteTolerance = 1e-9)
        assertEquals(3.0, Contrast.threshold(largeText = true), absoluteTolerance = 1e-9)
        assertTrue(Contrast.meetsAa(MaticaPalette.Paper, MaticaPalette.Blue))
        assertFalse(Contrast.meetsAa(MaticaPalette.Ink, MaticaPalette.Blue))
    }
}
