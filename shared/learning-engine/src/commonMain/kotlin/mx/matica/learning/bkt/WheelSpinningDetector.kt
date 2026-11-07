package mx.matica.learning.bkt

import mx.matica.learning.skills.SkillGraph
import mx.matica.math.skills.SkillId

/**
 * Acciones pedagógicas recomendadas ante el diagnóstico del detector de estancamiento.
 */
enum class Action {
    CONTINUE,
    REVIEW_PREREQUISITE,
    PROVIDE_SCAFFOLDING,
}

/**
 * Señal concisa para emitir en eventos y telemetría de aprendizaje.
 */
data class WheelSpinningSignal(
    val isStuck: Boolean,
    val recommendedPrerequisite: SkillId? = null,
    val recommendedAction: Action = if (isStuck) Action.REVIEW_PREREQUISITE else Action.CONTINUE,
    val growth: Double = 0.0,
    val attemptsCount: Int = 0,
)

/**
 * Resultado detallado del análisis de estancamiento cognitivo (Wheel-Spinning).
 */
sealed interface WheelSpinningResult {
    val isStuck: Boolean
    val growth: Double
    val attemptsCount: Int
    val recommendedAction: Action
    val recommendedPrerequisite: SkillId?

    fun toSignal(): WheelSpinningSignal = WheelSpinningSignal(
        isStuck = isStuck,
        recommendedPrerequisite = recommendedPrerequisite,
        recommendedAction = recommendedAction,
        growth = growth,
        attemptsCount = attemptsCount,
    )

    typealias Action = mx.matica.learning.bkt.Action

    data class Progressing(
        override val growth: Double,
        override val attemptsCount: Int,
        override val recommendedAction: Action = Action.CONTINUE,
        override val recommendedPrerequisite: SkillId? = null,
    ) : WheelSpinningResult {
        override val isStuck: Boolean get() = false
    }

    data class Stuck(
        override val growth: Double,
        override val attemptsCount: Int,
        override val recommendedAction: Action = Action.REVIEW_PREREQUISITE,
        override val recommendedPrerequisite: SkillId? = null,
        val message: String = "Estudiante estancado: $attemptsCount intentos con crecimiento de $growth (< 0.10). Se recomienda revisar prerrequisitos.",
    ) : WheelSpinningResult {
        override val isStuck: Boolean get() = true
    }
}

/**
 * Detector de estancamiento de Beck & Gong (2013) ("Wheel-Spinning: Students Who Fail to Master a Skill").
 *
 * Cuando un estudiante realiza 5 o más intentos sin que su probabilidad de dominio latente aumente
 * al menos 0.10 (10%), se encuentra atrapado en wheel-spinning. Continuar dándole ejercicios idénticos
 * produce frustración; el sistema debe derivarlo a repasar los prerrequisitos de la habilidad.
 */
object WheelSpinningDetector {

    const val DEFAULT_THRESHOLD_ATTEMPTS: Int = 5
    const val DEFAULT_MIN_GROWTH: Double = 0.10

    /**
     * Evalúa si una racha de intentos consecutivos constituye estancamiento cognitivo (wheel-spinning)
     * según el criterio de Beck & Gong (2013).
     *
     * @param recentAttempts Colección de intentos recientes (true = acierto, false = fallo).
     * @param initialPL Probabilidad de conocimiento al iniciar la secuencia.
     * @param currentPL Probabilidad de conocimiento actual tras los intentos.
     * @param thresholdAttempts Mínimo de intentos para declarar estancamiento (default 5).
     * @param minGrowth Ganancia mínima de probabilidad esperada (default 0.10).
     */
    fun check(
        recentAttempts: List<Boolean>,
        initialPL: Double,
        currentPL: Double,
        thresholdAttempts: Int = DEFAULT_THRESHOLD_ATTEMPTS,
        minGrowth: Double = DEFAULT_MIN_GROWTH,
    ): WheelSpinningResult {
        val attempts = recentAttempts.size
        val growth = currentPL - initialPL

        return if (attempts >= thresholdAttempts && growth < minGrowth) {
            WheelSpinningResult.Stuck(
                growth = growth,
                attemptsCount = attempts,
                recommendedAction = Action.REVIEW_PREREQUISITE,
                recommendedPrerequisite = null,
            )
        } else {
            WheelSpinningResult.Progressing(
                growth = growth,
                attemptsCount = attempts,
                recommendedAction = Action.CONTINUE,
                recommendedPrerequisite = null,
            )
        }
    }

    /**
     * Sobrecarga contextual que recibe la habilidad actual y el grafo curricular para recomendar
     * automáticamente el prerrequisito directo pendiente en caso de estancamiento.
     */
    fun check(
        skillId: SkillId,
        recentAttempts: List<Boolean>,
        initialPL: Double,
        currentPL: Double,
        graph: SkillGraph? = null,
        masteredSkills: Set<SkillId> = emptySet(),
        thresholdAttempts: Int = DEFAULT_THRESHOLD_ATTEMPTS,
        minGrowth: Double = DEFAULT_MIN_GROWTH,
    ): WheelSpinningResult {
        val baseResult = check(recentAttempts, initialPL, currentPL, thresholdAttempts, minGrowth)
        if (baseResult is WheelSpinningResult.Stuck) {
            val prereqs = graph?.prerequisitesOf(skillId).orEmpty()
            val recommendedPrereq = prereqs.firstOrNull { it !in masteredSkills } ?: prereqs.firstOrNull()
            return baseResult.copy(recommendedPrerequisite = recommendedPrereq)
        }
        return baseResult
    }

    /**
     * Emite directamente un [WheelSpinningSignal] apto para telemetría o despacho de navegación.
     */
    fun detectSignal(
        skillId: SkillId,
        recentAttempts: List<Boolean>,
        initialPL: Double,
        currentPL: Double,
        graph: SkillGraph? = null,
        masteredSkills: Set<SkillId> = emptySet(),
        thresholdAttempts: Int = DEFAULT_THRESHOLD_ATTEMPTS,
        minGrowth: Double = DEFAULT_MIN_GROWTH,
    ): WheelSpinningSignal {
        return check(
            skillId = skillId,
            recentAttempts = recentAttempts,
            initialPL = initialPL,
            currentPL = currentPL,
            graph = graph,
            masteredSkills = masteredSkills,
            thresholdAttempts = thresholdAttempts,
            minGrowth = minGrowth,
        ).toSignal()
    }
}
