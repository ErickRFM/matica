package mx.matica.learning.bkt

import mx.matica.learning.mastery.MasteryLevel
import mx.matica.math.skills.SkillId

/**
 * Parámetros del modelo estándar BKT de 4 parámetros (Corbett & Anderson, 1995).
 * - [pL0]: Probabilidad inicial de conocimiento previo (prior P(L_0)). Default 0.20.
 * - [pT]: Probabilidad de transición/aprendizaje en cada intento (P(T)). Default 0.15.
 * - [pG]: Probabilidad de acertar por adivinación sin saber (P(G) - Guess). Default 0.14.
 * - [pS]: Probabilidad de deslizarse/fallar aun sabiendo (P(S) - Slip). Default 0.10.
 */
data class BktParameters(
    val pL0: Double = 0.20,
    val pT: Double = 0.15,
    val pG: Double = 0.14,
    val pS: Double = 0.10,
) {
    init {
        require(pL0 in 0.0..1.0) { "pL0 debe estar entre 0.0 y 1.0 (recibido: $pL0)" }
        require(pT in 0.0..1.0) { "pT debe estar entre 0.0 y 1.0 (recibido: $pT)" }
        require(pG in 0.0..1.0) { "pG debe estar entre 0.0 y 1.0 (recibido: $pG)" }
        require(pS in 0.0..1.0) { "pS debe estar entre 0.0 y 1.0 (recibido: $pS)" }
    }
}

/**
 * Estado bayesiano de conocimiento para una habilidad específica.
 *
 * @param skillId Identificador de la habilidad matemática.
 * @param pL Probabilidad latente continua de que el estudiante domine la habilidad P(L_t).
 * @param attemptsCount Número total de intentos registrados para esta habilidad.
 * @param correctCount Total de aciertos conseguidos.
 * @param lastUpdatedDay Día del calendario o epoch day de la última interacción registrada.
 */
data class SkillBelief(
    val skillId: SkillId,
    val pL: Double,
    val attemptsCount: Int,
    val correctCount: Int,
    val lastUpdatedDay: Int,
) {
    val incorrectCount: Int get() = attemptsCount - correctCount
    val masteryLevel: MasteryLevel get() = BayesianKnowledgeEngine.masteryLevelOf(pL)

    fun isMastered(threshold: Double = 0.90): Boolean = pL >= threshold

    companion object {
        fun initial(skillId: SkillId, params: BktParameters = BktParameters(), day: Int = 0): SkillBelief =
            SkillBelief(
                skillId = skillId,
                pL = params.pL0,
                attemptsCount = 0,
                correctCount = 0,
                lastUpdatedDay = day,
            )
    }
}

/**
 * Motor de Rastreo de Conocimiento Bayesiano (Bayesian Knowledge Tracing - BKT).
 *
 * Implementa el modelo de 4 parámetros de Corbett & Anderson (1995) con actualización continua
 * de probabilidades posteriores y transición hacia el siguiente intento, mapeado a niveles de maestría.
 */
object BayesianKnowledgeEngine {

    val DEFAULT_PARAMS = BktParameters()

    /**
     * Calcula la probabilidad posterior de dominio P(L|Obs) dada una respuesta correcta o incorrecta.
     */
    fun posterior(pL: Double, isCorrect: Boolean, params: BktParameters = DEFAULT_PARAMS): Double {
        return if (isCorrect) {
            val numerator = pL * (1.0 - params.pS)
            val denominator = numerator + (1.0 - pL) * params.pG
            if (denominator == 0.0) 0.0 else numerator / denominator
        } else {
            val numerator = pL * params.pS
            val denominator = numerator + (1.0 - pL) * (1.0 - params.pG)
            if (denominator == 0.0) 0.0 else numerator / denominator
        }
    }

    /**
     * Actualiza el estado bayesiano [currentBelief] incorporando la nueva observación [isCorrect]
     * y aplicando la probabilidad de transición por aprendizaje [BktParameters.pT].
     *
     * 1. Posterior:
     *    P(L|Correct) = (P(L) * (1 - P(S))) / (P(L) * (1 - P(S)) + (1 - P(L)) * P(G))
     *    P(L|Incorrect) = (P(L) * P(S)) / (P(L) * P(S) + (1 - P(L)) * (1 - P(G)))
     *
     * 2. Prior para el siguiente intento:
     *    P(L_{t+1}) = (P(L|Obs) + (1 - P(L|Obs)) * P(T)).coerceIn(0.001, 0.999)
     */
    fun update(
        currentBelief: SkillBelief,
        isCorrect: Boolean,
        day: Int,
        params: BktParameters = DEFAULT_PARAMS,
    ): SkillBelief {
        val post = posterior(currentBelief.pL, isCorrect, params)
        val pLNext = (post + (1.0 - post) * params.pT).coerceIn(0.001, 0.999)

        return SkillBelief(
            skillId = currentBelief.skillId,
            pL = pLNext,
            attemptsCount = currentBelief.attemptsCount + 1,
            correctCount = currentBelief.correctCount + if (isCorrect) 1 else 0,
            lastUpdatedDay = day,
        )
    }

    /**
     * Mapeo de la probabilidad continua P(L) a [MasteryLevel]:
     * - P(L) < 0.40 -> NOVICE
     * - 0.40 <= P(L) < 0.70 -> DEVELOPING
     * - 0.70 <= P(L) < 0.90 -> PROFICIENT
     * - P(L) >= 0.90 -> FLUENT
     */
    fun masteryLevelOf(pL: Double): MasteryLevel = when {
        pL < 0.40 -> MasteryLevel.NOVICE
        pL < 0.70 -> MasteryLevel.DEVELOPING
        pL < 0.90 -> MasteryLevel.PROFICIENT
        else -> MasteryLevel.FLUENT
    }

    /**
     * Probabilidad predicha de respuesta correcta dado el nivel de dominio actual:
     * P(Correct) = P(L) * (1 - P(S)) + (1 - P(L)) * P(G)
     */
    fun probabilityOfCorrect(pL: Double, params: BktParameters = DEFAULT_PARAMS): Double =
        pL * (1.0 - params.pS) + (1.0 - pL) * params.pG
}
