package mx.matica.learning.mastery

import kotlinx.datetime.LocalDate
import mx.matica.math.skills.SkillId

/**
 * Umbrales del modelo de dominio. Son una decisión pedagógica explícita y única: cambiar uno de
 * estos números cambia el producto, así que viven aquí y no repartidos por las reglas.
 */
object MasteryThresholds {
    const val WINDOW_EVIDENCES: Int = 8
    const val PROFICIENT_UNAIDED_REQUIRED: Int = 3
    const val FLUENT_DISTINCT_DAYS: Int = 3
    const val FLUENT_SPREAD_DAYS: Long = 7
    const val DEMOTION_RECENT_EVIDENCES: Int = 3
    const val DEMOTION_INCORRECT_REQUIRED: Int = 2
}

data class MasteryChange(
    val skill: SkillId,
    val from: MasteryLevel,
    val to: MasteryLevel,
    val reason: String,
) {
    val changed: Boolean get() = from != to
}

/**
 * Reglas de ascenso y descenso. Se evalúan sobre la ventana completa, no sobre el último intento:
 * un acierto aislado no convierte a nadie en fluido y un tropiezo aislado no borra el dominio.
 */
object MasteryRules {

    fun evaluate(skill: SkillId, level: MasteryLevel, window: List<SkillEvidence>): MasteryChange {
        val recent = window.takeLast(MasteryThresholds.WINDOW_EVIDENCES)
        val unaided = recent.count { it.isUnaided }
        val incorrect = recent.count { it.quality == EvidenceQuality.INCORRECT }
        val assisted = recent.count { it.quality == EvidenceQuality.CORRECT_ASSISTED }
        val counts = "$unaided sin pista, $assisted con pista, $incorrect incorrectas"

        val latest = recent.takeLast(MasteryThresholds.DEMOTION_RECENT_EVIDENCES)
        val recentIncorrect = latest.count { it.quality == EvidenceQuality.INCORRECT }
        if (level != MasteryLevel.NOT_INTRODUCED && level != MasteryLevel.NOVICE && recentIncorrect >= MasteryThresholds.DEMOTION_INCORRECT_REQUIRED) {
            val to = level.retreated
            return MasteryChange(skill, level, to, "Baja a ${to.labelEs}: ${recentIncorrect} de las últimas " +
                "${MasteryThresholds.DEMOTION_RECENT_EVIDENCES} evidencias son incorrectas ($counts).")
        }

        val nextLevel = when (level) {
            MasteryLevel.NOT_INTRODUCED,
            MasteryLevel.NOVICE -> if (unaided + assisted >= 1) MasteryLevel.DEVELOPING else level
            MasteryLevel.DEVELOPING -> when {
                unaided >= MasteryThresholds.PROFICIENT_UNAIDED_REQUIRED && incorrect == 0 -> MasteryLevel.PROFICIENT
                else -> level
            }

            MasteryLevel.PROFICIENT -> if (showsRetention(recent) && incorrect == 0) MasteryLevel.FLUENT else level
            MasteryLevel.FLUENT -> level
        }

        if (nextLevel == level) {
            return MasteryChange(skill, level, level, "Sin cambio en ${level.labelEs} ($counts).")
        }
        return MasteryChange(skill, level, nextLevel, "Sube a ${nextLevel.labelEs} ($counts).${retentionNote(level, nextLevel, recent)}")
    }

    /**
     * La fluidez exige recuerdo repartido en el tiempo: la misma cuenta bien hecha cinco veces el
     * mismo día no demuestra retención, así que se piden días distintos separados por una semana.
     */
    private fun showsRetention(window: List<SkillEvidence>): Boolean {
        val days = window.filter { it.isUnaided }.map { it.occurredOn }.distinct().sorted()
        if (days.size < MasteryThresholds.FLUENT_DISTINCT_DAYS) return false
        val spread = days.first().daysBetween(days.last())
        return spread >= MasteryThresholds.FLUENT_SPREAD_DAYS
    }

    private fun retentionNote(from: MasteryLevel, to: MasteryLevel, window: List<SkillEvidence>): String {
        if (to != MasteryLevel.FLUENT) return ""
        val days = window.filter { it.isUnaided }.map { it.occurredOn }.distinct().sorted()
        return " Días con acierto sin pista: ${days.joinToString { it.toString() }}."
    }
}

/** `LocalDate` no expone una resta pública entre fechas; los días absolutos sí son comparables. */
private fun LocalDate.daysBetween(other: LocalDate): Long = other.toEpochDays() - toEpochDays()
