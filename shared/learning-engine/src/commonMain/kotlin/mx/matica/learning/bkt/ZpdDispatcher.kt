package mx.matica.learning.bkt

import mx.matica.math.skills.SkillId
import kotlin.math.abs

/**
 * Evaluación de un candidato (habilidad o ítem) dentro de la Zona de Desarrollo Próximo (ZPD).
 *
 * @param item El candidato evaluado (genérico).
 * @param skillId Habilidad matemática asociada al ítem.
 * @param successProbability Probabilidad predicha de éxito P(Correct).
 * @param inFlowZone Indica si la probabilidad cae dentro del rango óptimo de flujo ([0.75, 0.85]).
 * @param distanceFromRange Distancia a la frontera del rango [0.75, 0.85] (0.0 si está dentro).
 * @param distanceFromTarget Distancia absoluta al centro del flujo (0.80).
 */
data class ZpdItemEvaluation<T>(
    val item: T,
    val skillId: SkillId,
    val successProbability: Double,
    val inFlowZone: Boolean,
    val distanceFromRange: Double,
    val distanceFromTarget: Double,
) : Comparable<ZpdItemEvaluation<T>> {

    override fun compareTo(other: ZpdItemEvaluation<T>): Int {
        val rangeDiff = distanceFromRange.compareTo(other.distanceFromRange)
        if (rangeDiff != 0) return rangeDiff
        return distanceFromTarget.compareTo(other.distanceFromTarget)
    }
}

/**
 * Despachador adaptativo de Zona de Desarrollo Próximo (ZPD).
 *
 * Basado en la teoría de Vygotsky y el canal de flujo de Csikszentmihalyi:
 * Selecciona e interactúa con tareas donde la probabilidad de éxito P(Correct) se sitúa
 * en el punto óptimo entre la frustración (< 0.75) y el aburrimiento (> 0.85).
 */
object ZpdDispatcher {

    /** Límite inferior del canal de flujo cognitivo óptimo. */
    const val FLOW_MIN: Double = 0.75

    /** Límite superior del canal de flujo cognitivo óptimo. */
    const val FLOW_MAX: Double = 0.85

    /** Punto diana central de flujo cognitivo ideal. */
    const val FLOW_TARGET: Double = 0.80

    /**
     * Calcula la probabilidad de éxito P(Correct) dado un nivel latente [pL].
     */
    fun successProbability(
        pL: Double,
        params: BktParameters = BayesianKnowledgeEngine.DEFAULT_PARAMS,
    ): Double = BayesianKnowledgeEngine.probabilityOfCorrect(pL, params)

    /**
     * Verifica si una probabilidad dada se encuentra dentro del rango de flujo cognitivo.
     */
    fun isInFlowZone(probability: Double): Boolean =
        probability in FLOW_MIN..FLOW_MAX

    /**
     * Evalúa un ítem cualquiera con su probabilidad de éxito y métricas de distancia al flujo.
     */
    fun <T> evaluateItem(
        item: T,
        skillId: SkillId,
        pL: Double,
        params: BktParameters = BayesianKnowledgeEngine.DEFAULT_PARAMS,
    ): ZpdItemEvaluation<T> {
        val pCorrect = successProbability(pL, params)
        val inFlow = isInFlowZone(pCorrect)
        val distRange = when {
            pCorrect < FLOW_MIN -> FLOW_MIN - pCorrect
            pCorrect > FLOW_MAX -> pCorrect - FLOW_MAX
            else -> 0.0
        }
        val distTarget = abs(pCorrect - FLOW_TARGET)

        return ZpdItemEvaluation(
            item = item,
            skillId = skillId,
            successProbability = pCorrect,
            inFlowZone = inFlow,
            distanceFromRange = distRange,
            distanceFromTarget = distTarget,
        )
    }

    /**
     * Evalúa una habilidad aislada como candidato directo.
     */
    fun evaluateSkill(
        skillId: SkillId,
        pL: Double,
        params: BktParameters = BayesianKnowledgeEngine.DEFAULT_PARAMS,
    ): ZpdItemEvaluation<SkillId> = evaluateItem(skillId, skillId, pL, params)

    /**
     * Ordena las habilidades candidatas según su cercanía a la ZPD óptima.
     * Prioriza las habilidades que caen dentro de [0.75, 0.85] y luego por cercanía a 0.80.
     */
    fun rankSkills(
        candidates: List<SkillId>,
        beliefs: Map<SkillId, SkillBelief>,
        params: BktParameters = BayesianKnowledgeEngine.DEFAULT_PARAMS,
    ): List<ZpdItemEvaluation<SkillId>> {
        return candidates.map { skill ->
            val pL = beliefs[skill]?.pL ?: params.pL0
            evaluateSkill(skill, pL, params)
        }.sorted()
    }

    /**
     * Selecciona la habilidad más óptima para el estudiante según la ZPD.
     * Retorna null si la lista de candidatas está vacía.
     */
    fun selectOptimalSkill(
        candidates: List<SkillId>,
        beliefs: Map<SkillId, SkillBelief>,
        params: BktParameters = BayesianKnowledgeEngine.DEFAULT_PARAMS,
    ): SkillId? = rankSkills(candidates, beliefs, params).firstOrNull()?.item

    /**
     * Ordena una colección arbitraria de ítems (ej. ejercicios, tarjetas) según la ZPD de su habilidad.
     */
    fun <T> rankItems(
        items: List<T>,
        skillOf: (T) -> SkillId,
        beliefs: Map<SkillId, SkillBelief>,
        paramsOf: (T) -> BktParameters = { BayesianKnowledgeEngine.DEFAULT_PARAMS },
    ): List<ZpdItemEvaluation<T>> {
        return items.map { item ->
            val skill = skillOf(item)
            val params = paramsOf(item)
            val pL = beliefs[skill]?.pL ?: params.pL0
            evaluateItem(item, skill, pL, params)
        }.sorted()
    }

    /**
     * Selecciona el mejor ítem de la colección según la ZPD.
     */
    fun <T> selectOptimalItem(
        items: List<T>,
        skillOf: (T) -> SkillId,
        beliefs: Map<SkillId, SkillBelief>,
        paramsOf: (T) -> BktParameters = { BayesianKnowledgeEngine.DEFAULT_PARAMS },
    ): T? = rankItems(items, skillOf, beliefs, paramsOf).firstOrNull()?.item
}
