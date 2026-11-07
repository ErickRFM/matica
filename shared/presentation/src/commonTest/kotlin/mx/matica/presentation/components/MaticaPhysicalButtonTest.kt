package mx.matica.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import mx.matica.design.a11y.Contrast
import mx.matica.design.feedback.FeedbackHaptic
import mx.matica.design.feedback.FeedbackSound
import mx.matica.design.feedback.RecordingMaticaFeedback
import mx.matica.design.tokens.MaticaLayout

class MaticaPhysicalButtonTest {

    private val variants = MaticaPhysicalButtonVariant.entries.toList()

    @Test
    fun todasLasVariantesCumplenAccesibilidadWcagAaEnAmbosTemas() {
        val pairs = MaticaPhysicalButtonPalette.measuredPairs()
        // 4 variantes x 2 temas (claro y oscuro) = 8 pares medidos
        assertEquals(variants.size * 2, pairs.size)

        pairs.forEach { pair ->
            assertTrue(
                Contrast.meetsAa(pair.foreground, pair.background),
                "Fallo de contraste en ${pair.usage}: ratio ${Contrast.ratio(pair.foreground, pair.background)}",
            )
        }
    }

    @Test
    fun loQueSePintaEsLoQueSeMide() {
        listOf(false, true).forEach { dark ->
            variants.forEach { variant ->
                val colors = MaticaPhysicalButtonPalette.colorsOf(variant, dark)
                val pair = colors.toColorPair()
                assertTrue(
                    pair in MaticaPhysicalButtonPalette.measuredPairs(),
                    "$variant en ${if (dark) "oscuro" else "claro"} no está en la lista medida de contraste.",
                )
            }
        }
    }

    @Test
    fun lasBasesDeCadaVarianteDifierenDeLaCaraSuperiorParaEfecto3D() {
        listOf(false, true).forEach { dark ->
            variants.forEach { variant ->
                val colors = MaticaPhysicalButtonPalette.colorsOf(variant, dark)
                assertNotEquals(
                    colors.background,
                    colors.base,
                    "La base de $variant (dark=$dark) debe ser distinta de la superficie para simular volumen 3D.",
                )
            }
        }
    }

    @Test
    fun calculoDeOffsetFisicoAlPresionar() {
        // En reposo (sin presionar), el cuerpo está a 0 dp
        val unpressedOffset = MaticaPhysicalButtonDefaults.calculatePressedOffset(
            isPressed = false,
            enabled = true,
        )
        assertEquals(0.dp, unpressedOffset, "En reposo el botón debe tener offset 0 dp.")

        // Al presionarse habilitado, desciende exactamente 4 dp
        val pressedOffset = MaticaPhysicalButtonDefaults.calculatePressedOffset(
            isPressed = true,
            enabled = true,
        )
        assertEquals(4.dp, pressedOffset, "Al presionarse, el botón debe descender 4 dp.")
        assertEquals(MaticaPhysicalButtonDefaults.Elevation, pressedOffset)

        // Si está deshabilitado, no desciende aunque se reporte presionado
        val disabledOffset = MaticaPhysicalButtonDefaults.calculatePressedOffset(
            isPressed = true,
            enabled = false,
        )
        assertEquals(0.dp, disabledOffset, "Un botón deshabilitado no debe descender.")

        // Con elevación personalizada
        val customElevation = 6.dp
        val customPressedOffset = MaticaPhysicalButtonDefaults.calculatePressedOffset(
            isPressed = true,
            enabled = true,
            elevation = customElevation,
        )
        assertEquals(customElevation, customPressedOffset)
    }

    @Test
    fun tamanoTactilMinimoCumpleNormaDeAccesibilidad() {
        // WCAG y Material exigen al menos 48 dp de objetivo táctil
        assertTrue(
            MaticaPhysicalButtonDefaults.MinTouchTarget >= 48.dp,
            "El objetivo táctil debe ser al menos 48 dp.",
        )
        assertEquals(MaticaLayout.touchTarget, MaticaPhysicalButtonDefaults.MinTouchTarget)
    }

    @Test
    fun fisicaDeResorteConfiguradaParaSensacionElastica() {
        assertEquals(
            Spring.DampingRatioMediumBouncy,
            MaticaPhysicalButtonDefaults.SPRING_DAMPING,
            "El amortiguamiento debe ser MediumBouncy para sensación jugosa y táctil.",
        )
        assertEquals(
            Spring.StiffnessMediumLow,
            MaticaPhysicalButtonDefaults.SPRING_STIFFNESS,
            "La rigidez del resorte debe ser StiffnessMediumLow para rebote natural.",
        )
    }

    @Test
    fun feedbackHapticoYAuditivoDespachaClickYLightTick() {
        val recorder = RecordingMaticaFeedback()
        recorder.keyTap()

        assertEquals(listOf(FeedbackSound.CLICK), recorder.playedSounds)
        assertEquals(listOf(FeedbackHaptic.LIGHT_TICK), recorder.performedHaptics)
    }
}
