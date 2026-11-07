package mx.matica.exercises

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WordProblemTest {

    @Test
    fun catalogoDeProblemasVerbalesCompletoYCoherente() {
        val problems = WordProblemRepository.all
        assertTrue(problems.size >= 3, "Debe haber al menos 3 problemas verbales en el catálogo")

        for (problem in problems) {
            assertTrue(problem.storyContext.isNotBlank(), "Debe tener contexto")

            // Estación 1: Incógnita
            assertEquals(1, problem.stage1.choices.count { it.isCorrect }, "Estación 1 debe tener exactamente 1 respuesta correcta")
            assertTrue(problem.stage1.choices.size >= 3)

            // Estación 2: Datos
            assertEquals(1, problem.stage2.choices.count { it.isCorrect }, "Estación 2 debe tener exactamente 1 respuesta correcta")
            assertTrue(problem.stage2.choices.size >= 3)

            // Estación 3: Ecuación
            assertEquals(1, problem.stage3.choices.count { it.isCorrect }, "Estación 3 debe tener exactamente 1 respuesta correcta")
            assertTrue(problem.stage3.choices.size >= 3)

            // Estación 4: Interpretación
            assertEquals(1, problem.stage4.choices.count { it.isCorrect }, "Estación 4 debe tener exactamente 1 respuesta correcta")
            assertTrue(problem.stage4.choices.size >= 3)
        }
    }

    @Test
    fun recuperaProblemasPorIdYPorSkill() {
        val taxi = WordProblemRepository.byId("wp-taxi-01")
        assertNotNull(taxi)
        assertEquals(mx.matica.math.skills.SkillId.BALANCE_PRINCIPLE, taxi.skill)

        val cine = WordProblemRepository.byId("wp-cine-01")
        assertNotNull(cine)
        assertEquals(mx.matica.math.skills.SkillId.DISTRIBUTIVE_PROPERTY, cine.skill)
    }
}
