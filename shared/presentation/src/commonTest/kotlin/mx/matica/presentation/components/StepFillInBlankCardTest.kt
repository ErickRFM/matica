package mx.matica.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import mx.matica.design.a11y.Contrast
import mx.matica.design.tokens.MaticaPalette

class StepFillInBlankCardTest {

    @Test
    fun todosLosParesDeColoresCumplenAccesibilidadAA() {
        val pairs = StepFillInBlankPalette.measuredPairs()
        assertTrue(pairs.isNotEmpty(), "Debe haber pares medidos para claro y oscuro.")
        pairs.forEach { pair ->
            assertTrue(
                Contrast.meetsAa(pair.foreground, pair.background),
                "Fallo de contraste en ${pair.usage}: foreground=${pair.foreground}, background=${pair.background}, ratio=${Contrast.ratio(pair.foreground, pair.background)}",
            )
        }
    }

    @Test
    fun transicionDeBordeYFondoSegunEstadoDeValidacion() {
        listOf(false, true).forEach { dark ->
            // Borde de la tarjeta
            val neutralBorder = StepFillInBlankPalette.cardBorderColor(isCorrect = null, dark = dark)
            val correctBorder = StepFillInBlankPalette.cardBorderColor(isCorrect = true, dark = dark)
            val wrongBorder = StepFillInBlankPalette.cardBorderColor(isCorrect = false, dark = dark)

            assertTrue(neutralBorder != correctBorder, "El borde debe cambiar en acierto (dark=$dark)")
            assertTrue(neutralBorder != wrongBorder, "El borde debe cambiar en error (dark=$dark)")
            assertTrue(correctBorder != wrongBorder, "El borde de acierto debe ser distinto al de error (dark=$dark)")

            if (!dark) {
                assertEquals(MaticaPalette.GreenInk, correctBorder)
                assertEquals(StepFillInBlankPalette.WarningAmber, wrongBorder)
                assertEquals(MaticaPalette.BlueSoft, neutralBorder)
            }

            // Fondo del hueco
            val emptySlotBg = StepFillInBlankPalette.slotBackgroundColor(hasToken = false, isCorrect = null, dark = dark)
            val filledSlotBg = StepFillInBlankPalette.slotBackgroundColor(hasToken = true, isCorrect = null, dark = dark)
            val correctSlotBg = StepFillInBlankPalette.slotBackgroundColor(hasToken = true, isCorrect = true, dark = dark)
            val wrongSlotBg = StepFillInBlankPalette.slotBackgroundColor(hasToken = true, isCorrect = false, dark = dark)

            assertTrue(emptySlotBg != filledSlotBg, "El fondo del hueco debe cambiar al insertar token (dark=$dark)")
            assertTrue(filledSlotBg != correctSlotBg, "El fondo del hueco debe cambiar en acierto (dark=$dark)")
            assertTrue(filledSlotBg != wrongSlotBg, "El fondo del hueco debe cambiar en error (dark=$dark)")

            if (!dark) {
                assertEquals(MaticaPalette.Mist, emptySlotBg)
                assertEquals(MaticaPalette.BlueMist, filledSlotBg)
                assertEquals(MaticaPalette.Mint, correctSlotBg)
                assertEquals(StepFillInBlankPalette.WarningBgLight, wrongSlotBg)
            }
        }
    }

    @Test
    fun transicionDeColorDeTextoDelHuecoSegunEstado() {
        listOf(false, true).forEach { dark ->
            val placeholderColor = StepFillInBlankPalette.slotTextColor(hasToken = false, isCorrect = null, dark = dark)
            val filledColor = StepFillInBlankPalette.slotTextColor(hasToken = true, isCorrect = null, dark = dark)
            val correctColor = StepFillInBlankPalette.slotTextColor(hasToken = true, isCorrect = true, dark = dark)
            val wrongColor = StepFillInBlankPalette.slotTextColor(hasToken = true, isCorrect = false, dark = dark)

            assertTrue(placeholderColor != filledColor, "El color del texto del placeholder debe diferenciarse del token insertado")
            assertTrue(correctColor != wrongColor, "El color de acierto debe diferenciarse del error")
            if (!dark) {
                assertEquals(MaticaPalette.BlueDeep, placeholderColor)
                assertEquals(MaticaPalette.Ink, filledColor)
                assertEquals(MaticaPalette.GreenInk, correctColor)
                assertEquals(StepFillInBlankPalette.WarningAmber, wrongColor)
            }
        }
    }

    @Test
    fun anunciosAccesiblesEnEspanolIncluyenHuecoYEstado() {
        // Hueco vacío
        val announcementEmpty = blankAnnouncement(
            prefix = "2x + 4 - 4 = 12 - ",
            suffix = "",
            selectedToken = null,
            placeholder = "?",
            isCorrect = null,
        )
        assertTrue(announcementEmpty.contains("hueco vacío"), "Debe indicar 'hueco vacío' para lectores de pantalla")
        assertTrue(announcementEmpty.contains("es igual a"), "Debe anunciar el signo '=' en español")

        // Hueco con respuesta correcta
        val announcementCorrect = blankAnnouncement(
            prefix = "2x = ",
            suffix = "",
            selectedToken = "8",
            placeholder = "?",
            isCorrect = true,
        )
        assertTrue(announcementCorrect.contains("8"), "Debe incluir el término insertado")
        assertTrue(announcementCorrect.contains("correcto"), "Debe incluir confirmación de acierto")

        // Hueco con respuesta incorrecta
        val announcementWrong = blankAnnouncement(
            prefix = "2x = ",
            suffix = "",
            selectedToken = "4",
            placeholder = "?",
            isCorrect = false,
        )
        assertTrue(announcementWrong.contains("4"), "Debe incluir el término insertado")
        assertTrue(announcementWrong.contains("incorrecto"), "Debe incluir advertencia de fallo")
    }

    @Test
    fun fichasDeOpcionMantienenTokensClaveYContraste() {
        listOf(false, true).forEach { dark ->
            val unselectedBg = StepFillInBlankPalette.chipBackgroundColor(isSelected = false, dark = dark)
            val selectedBg = StepFillInBlankPalette.chipBackgroundColor(isSelected = true, dark = dark)
            assertTrue(unselectedBg != selectedBg, "Fondo de ficha seleccionada debe cambiar")

            val unselectedBorder = StepFillInBlankPalette.chipBorderColor(isSelected = false, dark = dark)
            val selectedBorder = StepFillInBlankPalette.chipBorderColor(isSelected = true, dark = dark)
            assertTrue(unselectedBorder != selectedBorder, "Borde de ficha seleccionada debe cambiar")
        }
    }

    @Test
    fun simulacionDeSeleccionYLimpiezaDeHueco() {
        var currentToken: String? = null
        var isCorrectState: Boolean? = null
        val expectedToken = "4"

        val onSelect: (String) -> Unit = { token ->
            currentToken = token
            isCorrectState = (token == expectedToken)
        }

        val onClear: () -> Unit = {
            currentToken = null
            isCorrectState = null
        }

        // Estado inicial
        assertNull(currentToken)
        assertNull(isCorrectState)

        // Selección incorrecta ("6")
        onSelect("6")
        assertEquals("6", currentToken)
        assertEquals(false, isCorrectState)

        // Limpieza del hueco
        onClear()
        assertNull(currentToken)
        assertNull(isCorrectState)

        // Selección correcta ("4")
        onSelect("4")
        assertEquals("4", currentToken)
        assertEquals(true, isCorrectState)
    }
}
