package mx.matica.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import mx.matica.design.a11y.Contrast
import mx.matica.design.tokens.MaticaPalette
import mx.matica.exercises.ExerciseGenerator
import mx.matica.exercises.GenerationOutcome
import mx.matica.math.MathEngine
import mx.matica.presentation.exercise.buildStepOptions
import mx.matica.presentation.exercise.fallbackStepOptions
import mx.matica.presentation.exercise.renderPlan

class StepChoiceCardTest {

    @Test
    fun todosLosParesDeColoresCumplenAccesibilidadAA() {
        val pairs = StepChoicePalette.measuredPairs()
        assertTrue(pairs.isNotEmpty(), "Debe haber pares medidos para claro y oscuro.")
        pairs.forEach { pair ->
            assertTrue(
                Contrast.meetsAa(pair.foreground, pair.background),
                "Fallo de contraste en ${pair.usage}: ratio ${Contrast.ratio(pair.foreground, pair.background)}",
            )
        }
    }

    @Test
    fun transicionDeFondoYBorderSegunEstado() {
        listOf(false, true).forEach { dark ->
            // No seleccionado
            val unselectedBorder = StepChoicePalette.borderColor(isSelected = false, isCorrect = null, dark = dark)
            val unselectedBg = StepChoicePalette.backgroundColor(isSelected = false, isCorrect = null, dark = dark)

            // Seleccionado
            val selectedBorder = StepChoicePalette.borderColor(isSelected = true, isCorrect = null, dark = dark)
            val selectedBg = StepChoicePalette.backgroundColor(isSelected = true, isCorrect = null, dark = dark)
            assertTrue(selectedBorder != unselectedBorder, "El borde debe cambiar al seleccionar (modo oscuro=$dark)")
            assertTrue(selectedBg != unselectedBg, "El fondo debe cambiar al seleccionar (modo oscuro=$dark)")

            if (!dark) {
                assertEquals(MaticaPalette.BlueMist, selectedBg, "En claro el fondo seleccionado debe ser BlueMist")
                assertEquals(MaticaPalette.Blue, selectedBorder, "En claro el borde seleccionado debe ser Blue")
            }

            // Acierto (isCorrect = true)
            val correctBorder = StepChoicePalette.borderColor(isSelected = true, isCorrect = true, dark = dark)
            val correctBg = StepChoicePalette.backgroundColor(isSelected = true, isCorrect = true, dark = dark)
            assertTrue(correctBorder != selectedBorder, "El borde debe cambiar en acierto")
            if (!dark) {
                assertEquals(MaticaPalette.Mint, correctBg, "En claro el fondo de acierto debe ser Mint")
                assertEquals(MaticaPalette.GreenInk, correctBorder, "En claro el borde de acierto debe ser GreenInk")
            }

            // Error / Advertencia (isCorrect = false)
            val wrongBorder = StepChoicePalette.borderColor(isSelected = true, isCorrect = false, dark = dark)
            val wrongBg = StepChoicePalette.backgroundColor(isSelected = true, isCorrect = false, dark = dark)
            assertTrue(wrongBorder != selectedBorder, "El borde debe cambiar a advertencia en error")
            assertTrue(wrongBg != selectedBg, "El fondo debe cambiar en error")
            if (!dark) {
                assertEquals(StepChoicePalette.WarningAmber, wrongBorder, "En claro el borde de error debe ser ámbar")
            }
        }
    }

    @Test
    fun opcionesDePrimerPasoParaEcuacionDistributivaCoincidenConFigma() {
        val engine = MathEngine()
        val generator = ExerciseGenerator()
        val outcome = generator.generate("distributive-linear", 4_000L)
        val instance = (outcome as GenerationOutcome.Ready).instance
        val plan = requireNotNull(renderPlan(instance, engine))

        val options = plan.stepOptions
        assertEquals(3, options.size, "Debe haber exactamente 3 opciones de primer paso.")
        assertEquals(listOf("A", "B", "C"), options.map { it.letter })

        val correctOption = options.firstOrNull { it.isCorrect }
        assertNotNull(correctOption, "Debe existir una opción correcta.")
        assertEquals("A", correctOption.letter)
        assertTrue(correctOption.text.startsWith("Dividir ambos lados entre"), "Opción A debe dividir ambos lados.")
        assertNotNull(correctOption.resultingExpression, "La opción correcta debe proporcionar expresión resultante.")

        val wrongOptions = options.filter { !it.isCorrect }
        assertEquals(2, wrongOptions.size)
        assertTrue(wrongOptions.any { it.text.contains("Sumar") || it.text.contains("Restar") })
        assertTrue(wrongOptions.any { it.text.contains("Multiplicar") })
    }

    @Test
    fun opcionesDePrimerPasoParaDosOperaciones() {
        val engine = MathEngine()
        val generator = ExerciseGenerator()
        val outcome = generator.generate("two-step-linear", 4_000L)
        val instance = (outcome as GenerationOutcome.Ready).instance
        val plan = requireNotNull(renderPlan(instance, engine))

        val options = plan.stepOptions
        assertEquals(3, options.size)
        val correct = options.first { it.isCorrect }
        assertEquals("A", correct.letter)
        assertTrue(correct.text.contains("lados"))
        assertNotNull(correct.resultingExpression)
    }

    @Test
    fun opcionesDePrimerPasoParaFracciones() {
        val engine = MathEngine()
        val generator = ExerciseGenerator()
        val outcome = generator.generate("fraction-linear", 4_000L)
        val instance = (outcome as GenerationOutcome.Ready).instance
        val plan = requireNotNull(renderPlan(instance, engine))

        val options = plan.stepOptions
        assertEquals(3, options.size)
        val correct = options.first { it.isCorrect }
        assertEquals("A", correct.letter)
        assertTrue(correct.text.startsWith("Multiplicar ambos lados por"))
        assertNotNull(correct.resultingExpression)
    }

    @Test
    fun opcionesDeRespaldoTienenEstructuraValida() {
        val fallback = fallbackStepOptions()
        assertEquals(3, fallback.size)
        assertEquals(listOf("A", "B", "C"), fallback.map { it.letter })
        assertTrue(fallback.any { it.isCorrect })
        assertEquals(2, fallback.count { !it.isCorrect })
    }
}
