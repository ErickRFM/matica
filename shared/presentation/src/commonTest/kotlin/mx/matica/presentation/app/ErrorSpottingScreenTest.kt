package mx.matica.presentation.app

import mx.matica.exercises.ErrorDetectionRepository
import mx.matica.math.skills.SkillId
import mx.matica.presentation.route.MaticaNavigator
import mx.matica.presentation.route.MaticaRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ErrorSpottingScreenTest {

    @Test
    fun rutaErrorSpottingSeParseaYReconstruye() {
        val exercise = ErrorDetectionRepository.all.first()
        val route = MaticaRoute.ErrorSpotting(exercise.id)

        assertEquals("error-spotting/${exercise.id}", route.path)
        val parsed = MaticaRoute.parse(route.path)
        assertNotNull(parsed)
        assertTrue(parsed is MaticaRoute.ErrorSpotting)
        assertEquals(exercise.id, parsed.exerciseId)
    }

    @Test
    fun sesionOtorgaRecompensaMetacognitiva() {
        val session = MaticaSession()
        val initialXp = session.xp

        session.awardMetacognitiveEvidence(SkillId.SIGN_HANDLING)
        assertTrue(session.xp > initialXp, "La evidencia metacognitiva debe sumar XP a la sesión")
    }

    @Test
    fun catalogoDeEjerciciosTieneTodasLasHabilidadesRequeridas() {
        val exercises = ErrorDetectionRepository.all
        assertTrue(exercises.isNotEmpty())

        val skills = exercises.map { it.targetSkill }.toSet()
        assertTrue(skills.contains(SkillId.SIGN_HANDLING))
        assertTrue(skills.contains(SkillId.ISOLATING_VARIABLE))
        assertTrue(skills.contains(SkillId.DISTRIBUTIVE_PROPERTY))
        assertTrue(skills.contains(SkillId.FRACTION_ARITHMETIC))
        assertTrue(skills.contains(SkillId.BALANCE_PRINCIPLE))
    }
}
