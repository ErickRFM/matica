package mx.matica.exercises

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ErrorDetectionTest {

    @Test
    fun catalogoDeErroresContieneCasosValidadosYAlMenosUnControl() {
        val exercises = ErrorDetectionRepository.all
        assertTrue(exercises.size >= 5, "Debe haber al menos 5 ejercicios en el catálogo")

        val control = exercises.firstOrNull { it.erroneousStepNumber == null }
        assertNotNull(control, "Debe haber al menos un ejercicio de control donde no haya errores")

        for (exercise in exercises) {
            assertTrue(exercise.steps.isNotEmpty(), "Cada ejercicio debe tener pasos")
            assertTrue(exercise.reasonOptions.size >= 3, "Cada ejercicio debe tener al menos 3 opciones de justificación")
            assertEquals(1, exercise.reasonOptions.count { it.isCorrect }, "Debe haber exactamente 1 justificación correcta en ${exercise.id}")

            if (exercise.erroneousStepNumber != null) {
                val faultyStep = exercise.steps.firstOrNull { it.stepNumber == exercise.erroneousStepNumber }
                assertNotNull(faultyStep, "El paso marcado como erróneo debe existir en los pasos")
                assertTrue(faultyStep.isErroneous, "El paso debe estar flaggeado como isErroneous")
                assertNotNull(faultyStep.canonicalError, "El paso erróneo debe especificar su canonicalError")
            } else {
                assertTrue(exercise.steps.none { it.isErroneous }, "En el ejercicio control ningún paso debe ser erróneo")
            }
        }
    }

    @Test
    fun recuperaPorIdYPorHabilidad() {
        val signEx = ErrorDetectionRepository.byId("err-sign-flip-01")
        assertNotNull(signEx)
        assertEquals(1, signEx.erroneousStepNumber)

        val fracExs = ErrorDetectionRepository.forSkill(mx.matica.math.skills.SkillId.FRACTION_ARITHMETIC)
        assertTrue(fracExs.isNotEmpty())
    }
}
