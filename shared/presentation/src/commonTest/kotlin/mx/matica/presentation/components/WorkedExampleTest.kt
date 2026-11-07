package mx.matica.presentation.components

import mx.matica.math.skills.SkillId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkedExampleTest {

    @Test
    fun catalogoDeEjemplosGuiadosCubreHabilidadesClave() {
        val examples = WorkedExamplesCatalog.all
        assertTrue(examples.isNotEmpty())

        val balanceExample = WorkedExamplesCatalog.forSkill(SkillId.BALANCE_PRINCIPLE)
        assertNotNull(balanceExample)
        assertEquals("3x + 6 = 21", balanceExample.problemStatement)
        assertEquals(3, balanceExample.steps.size)

        for (step in balanceExample.steps) {
            assertTrue(step.thinkAloudQuestion.isNotBlank(), "Debe tener pregunta guía think-aloud")
            assertTrue(step.mathematicalRationale.isNotBlank(), "Debe tener justificación matemática")
        }

        val distExample = WorkedExamplesCatalog.forSkill(SkillId.DISTRIBUTIVE_PROPERTY)
        assertNotNull(distExample)

        val fracExample = WorkedExamplesCatalog.forSkill(SkillId.FRACTION_ARITHMETIC)
        assertNotNull(fracExample)
    }
}
