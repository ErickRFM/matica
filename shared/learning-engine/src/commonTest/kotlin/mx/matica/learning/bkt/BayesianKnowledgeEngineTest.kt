package mx.matica.learning.bkt

import mx.matica.learning.mastery.MasteryLevel
import mx.matica.learning.skills.DefaultCurriculum
import mx.matica.math.skills.SkillId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BayesianKnowledgeEngineTest {

    private val defaultParams = BktParameters(
        pL0 = 0.20,
        pT = 0.15,
        pG = 0.14,
        pS = 0.10,
    )

    @Test
    fun testCorbettAndAndersonPublishedParametersConvergence() {
        val initialBelief = SkillBelief.initial(SkillId.INTEGER_OPERATIONS, defaultParams, day = 1)
        assertEquals(0.20, initialBelief.pL, 0.0001)
        assertEquals(MasteryLevel.NOVICE, initialBelief.masteryLevel)

        // Intento 1: Acierto
        // P(L|C) = (0.20 * 0.90) / (0.20 * 0.90 + 0.80 * 0.14) = 0.18 / 0.292 = 0.616438
        // P(L1) = 0.616438 + (1 - 0.616438) * 0.15 = 0.673972
        val step1 = BayesianKnowledgeEngine.update(initialBelief, isCorrect = true, day = 1, params = defaultParams)
        assertEquals(0.67397, step1.pL, 0.001)
        assertEquals(1, step1.attemptsCount)
        assertEquals(1, step1.correctCount)
        assertEquals(MasteryLevel.DEVELOPING, step1.masteryLevel)

        // Intento 2: Acierto consecutivo
        // P(L1) = 0.67397
        // P(L|C) = (0.67397 * 0.90) / (0.67397 * 0.90 + 0.32603 * 0.14) = 0.606575 / 0.652219 = 0.930018
        // P(L2) = 0.930018 + (1 - 0.930018) * 0.15 = 0.940515
        val step2 = BayesianKnowledgeEngine.update(step1, isCorrect = true, day = 1, params = defaultParams)
        assertEquals(0.94051, step2.pL, 0.001)
        assertEquals(2, step2.attemptsCount)
        assertEquals(2, step2.correctCount)
        assertEquals(MasteryLevel.FLUENT, step2.masteryLevel)
        assertTrue(step2.isMastered())

        // Intento 1 alternativo: Fallo inicial
        // P(L|I) = (0.20 * 0.10) / (0.20 * 0.10 + 0.80 * 0.86) = 0.02 / 0.708 = 0.028249
        // P(L1_incorrect) = 0.028249 + (1 - 0.028249) * 0.15 = 0.174011
        val step1Fail = BayesianKnowledgeEngine.update(initialBelief, isCorrect = false, day = 1, params = defaultParams)
        assertEquals(0.17401, step1Fail.pL, 0.001)
        assertEquals(MasteryLevel.NOVICE, step1Fail.masteryLevel)
        assertEquals(1, step1Fail.incorrectCount)
    }

    @Test
    fun testSlipResilienceDoesNotDemoteFluentStudentToNovice() {
        // Estudiante con alta maestría demostrada (P(L) = 0.95 -> FLUENT)
        val fluentBelief = SkillBelief(
            skillId = SkillId.ORDER_OF_OPERATIONS,
            pL = 0.95,
            attemptsCount = 10,
            correctCount = 10,
            lastUpdatedDay = 1,
        )
        assertEquals(MasteryLevel.FLUENT, fluentBelief.masteryLevel)

        // Ocurre un desliz / error aislado (slip)
        // P(L|I) = (0.95 * 0.10) / (0.95 * 0.10 + 0.05 * 0.86) = 0.095 / 0.138 = 0.6884
        // P(L_next) = 0.6884 + (1 - 0.6884) * 0.15 = 0.7351
        val afterSlip = BayesianKnowledgeEngine.update(fluentBelief, isCorrect = false, day = 2, params = defaultParams)

        assertEquals(0.7351, afterSlip.pL, 0.005)
        // Slip resilience: retrocede a PROFICIENT, nunca es degradado a NOVICE por un solo tropiezo
        assertEquals(MasteryLevel.PROFICIENT, afterSlip.masteryLevel)
        assertFalse(afterSlip.masteryLevel == MasteryLevel.NOVICE)
    }

    @Test
    fun testLuckyGuessDiscountingDoesNotPrematurelyGrantFluency() {
        // Estudiante novato con bajo conocimiento inicial (P(L) = 0.20 -> NOVICE)
        val noviceBelief = SkillBelief.initial(SkillId.ISOLATING_VARIABLE, defaultParams, day = 1)
        assertEquals(MasteryLevel.NOVICE, noviceBeliceLevel(noviceBelief))

        // El estudiante acierta una pregunta (adivinanza afortunada)
        val afterGuess = BayesianKnowledgeEngine.update(noviceBelief, isCorrect = true, day = 1, params = defaultParams)

        // Guess discounting: P(L) sube a ~0.674 (DEVELOPING), NO pasa a PROFICIENT ni a FLUENT
        assertEquals(MasteryLevel.DEVELOPING, afterGuess.masteryLevel)
        assertFalse(afterGuess.isMastered())
        assertTrue(afterGuess.pL < 0.70)
    }

    @Test
    fun testProbabilityOfCorrectPrediction() {
        // Novato P(L) = 0.20: P(C) = 0.20 * 0.90 + 0.80 * 0.14 = 0.18 + 0.112 = 0.292
        val pCorrectNovice = BayesianKnowledgeEngine.probabilityOfCorrect(0.20, defaultParams)
        assertEquals(0.292, pCorrectNovice, 0.0001)

        // Fluido P(L) = 0.95: P(C) = 0.95 * 0.90 + 0.05 * 0.14 = 0.855 + 0.007 = 0.862
        val pCorrectFluent = BayesianKnowledgeEngine.probabilityOfCorrect(0.95, defaultParams)
        assertEquals(0.862, pCorrectFluent, 0.0001)
    }

    @Test
    fun testZpdSelectionTargetsOptimalCognitiveFlowZone() {
        // Candidatas con distintos niveles de dominio latente:
        // 1. ISOLATING_VARIABLE: pL = 0.10 -> P(C) = 0.10 * 0.90 + 0.90 * 0.14 = 0.216 (Frustrante, < 0.75)
        // 2. FRACTION_ARITHMETIC: pL = 0.85 -> P(C) = 0.85 * 0.90 + 0.15 * 0.14 = 0.786 (Óptimo, en [0.75, 0.85])
        // 3. INTEGER_OPERATIONS: pL = 0.99 -> P(C) = 0.99 * 0.90 + 0.01 * 0.14 = 0.8924 (Aburrido, > 0.85)
        val beliefs = mapOf(
            SkillId.ISOLATING_VARIABLE to SkillBelief(SkillId.ISOLATING_VARIABLE, 0.10, 2, 0, 1),
            SkillId.FRACTION_ARITHMETIC to SkillBelief(SkillId.FRACTION_ARITHMETIC, 0.85, 6, 5, 1),
            SkillId.INTEGER_OPERATIONS to SkillBelief(SkillId.INTEGER_OPERATIONS, 0.99, 15, 15, 1),
        )

        val candidates = listOf(
            SkillId.ISOLATING_VARIABLE,
            SkillId.FRACTION_ARITHMETIC,
            SkillId.INTEGER_OPERATIONS,
        )

        val selectedSkill = ZpdDispatcher.selectOptimalSkill(candidates, beliefs, defaultParams)
        assertEquals(SkillId.FRACTION_ARITHMETIC, selectedSkill)

        val rankings = ZpdDispatcher.rankSkills(candidates, beliefs, defaultParams)
        assertEquals(3, rankings.size)
        assertEquals(SkillId.FRACTION_ARITHMETIC, rankings[0].item)
        assertTrue(rankings[0].inFlowZone)
        assertEquals(0.786, rankings[0].successProbability, 0.001)

        // El segundo ítem más cercano a la zona es INTEGER_OPERATIONS (distancia 0.8924 - 0.85 = 0.0424)
        // versus ISOLATING_VARIABLE (distancia 0.75 - 0.216 = 0.534)
        assertEquals(SkillId.INTEGER_OPERATIONS, rankings[1].item)
        assertFalse(rankings[1].inFlowZone)

        assertEquals(SkillId.ISOLATING_VARIABLE, rankings[2].item)
        assertFalse(rankings[2].inFlowZone)
    }

    @Test
    fun testZpdCandidateItemsRanking() {
        data class ExerciseItem(val id: String, val skillId: SkillId)

        val items = listOf(
            ExerciseItem("ex-hard", SkillId.ISOLATING_VARIABLE),
            ExerciseItem("ex-flow", SkillId.FRACTION_ARITHMETIC),
            ExerciseItem("ex-easy", SkillId.INTEGER_OPERATIONS),
        )

        val beliefs = mapOf(
            SkillId.ISOLATING_VARIABLE to SkillBelief(SkillId.ISOLATING_VARIABLE, 0.15, 3, 0, 1),
            SkillId.FRACTION_ARITHMETIC to SkillBelief(SkillId.FRACTION_ARITHMETIC, 0.87, 8, 7, 1),
            SkillId.INTEGER_OPERATIONS to SkillBelief(SkillId.INTEGER_OPERATIONS, 0.98, 20, 19, 1),
        )

        val chosen = ZpdDispatcher.selectOptimalItem(
            items = items,
            skillOf = { it.skillId },
            beliefs = beliefs,
            paramsOf = { defaultParams },
        )

        assertNotNull(chosen)
        assertEquals("ex-flow", chosen.id)
    }

    @Test
    fun testWheelSpinningDetectionTriggerAndPrerequisiteRecommendation() {
        val graph = DefaultCurriculum.graph

        // FRACTION_ARITHMETIC tiene como prerrequisito directo INTEGER_OPERATIONS
        val skill = SkillId.FRACTION_ARITHMETIC
        assertTrue(SkillId.INTEGER_OPERATIONS in graph.prerequisitesOf(skill))

        // 5 intentos infructuosos con apenas crecimiento de pL
        val attempts = listOf(false, false, true, false, false)
        val initialPL = 0.20
        val currentPL = 0.24 // Ganancia de solo 0.04 (< 0.10 mínimo requerido tras 5 intentos)

        val result = WheelSpinningDetector.check(
            skillId = skill,
            recentAttempts = attempts,
            initialPL = initialPL,
            currentPL = currentPL,
            graph = graph,
        )

        assertIs<WheelSpinningResult.Stuck>(result)
        assertTrue(result.isStuck)
        assertEquals(Action.REVIEW_PREREQUISITE, result.recommendedAction)
        assertEquals(SkillId.INTEGER_OPERATIONS, result.recommendedPrerequisite)
        assertEquals(5, result.attemptsCount)
        assertEquals(0.04, result.growth, 0.0001)

        // Verificar emisión de WheelSpinningSignal
        val signal = WheelSpinningDetector.detectSignal(
            skillId = skill,
            recentAttempts = attempts,
            initialPL = initialPL,
            currentPL = currentPL,
            graph = graph,
        )
        assertTrue(signal.isStuck)
        assertEquals(SkillId.INTEGER_OPERATIONS, signal.recommendedPrerequisite)
        assertEquals(Action.REVIEW_PREREQUISITE, signal.recommendedAction)
    }

    @Test
    fun testWheelSpinningNotTriggeredWhenNormalProgressOrUnderThreshold() {
        // Caso 1: Progreso adecuado (> 0.10)
        val attempts5 = listOf(true, true, false, true, true)
        val progressingResult = WheelSpinningDetector.check(
            recentAttempts = attempts5,
            initialPL = 0.20,
            currentPL = 0.67, // Ganancia de 0.47 >= 0.10
        )
        assertIs<WheelSpinningResult.Progressing>(progressingResult)
        assertFalse(progressingResult.isStuck)
        assertEquals(Action.CONTINUE, progressingResult.recommendedAction)

        // Caso 2: Menos de 5 intentos (p.ej. 3 intentos) aun con baja ganancia
        val attempts3 = listOf(false, false, false)
        val underThresholdResult = WheelSpinningDetector.check(
            recentAttempts = attempts3,
            initialPL = 0.20,
            currentPL = 0.21,
            thresholdAttempts = 5,
        )
        assertIs<WheelSpinningResult.Progressing>(underThresholdResult)
        assertFalse(underThresholdResult.isStuck)
    }

    private fun noviceBeliceLevel(belief: SkillBelief): MasteryLevel =
        BayesianKnowledgeEngine.masteryLevelOf(belief.pL)
}
