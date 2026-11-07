package mx.matica.learning.review

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import mx.matica.learning.mastery.EvidenceQuality
import mx.matica.learning.mastery.SkillEvidence
import mx.matica.learning.text.pluralEs

object ReviewLadder {
    /** Intervalos en días por etapa. Etapa 0 = repaso al día siguiente. */
    val intervals: List<Long> = listOf(1, 3, 7, 14, 30)

    fun intervalOf(stage: Int): Long = intervals[stage.coerceIn(0, intervals.lastIndex)]

    val maxStage: Int get() = intervals.lastIndex
}

data class ReviewState(
    val stage: Int = 0,
    val scheduledOn: LocalDate? = null,
    val completedOn: LocalDate? = null,
    val lastQuality: EvidenceQuality? = null,
) {
    /** Vence desde la fecha programada en adelante, inclusive ese mismo día. */
    fun isDueOn(day: LocalDate): Boolean = scheduledOn != null && day.compareTo(scheduledOn) >= 0

    fun daysOverdue(day: LocalDate): Long = scheduledOn?.let { scheduled -> daysBetween(scheduled, day) } ?: 0L
}

data class ReviewDecision(val state: ReviewState, val reason: String)

/**
 * Repaso espaciado con reglas visibles: la escalonera es fija y cada transición dice por qué se
 * quedó, subió o bajó de etapa. No hay modelo oculto que justifique una fecha.
 */
object ReviewRules {

    fun advance(previous: ReviewState, evidence: SkillEvidence): ReviewDecision {
        val day = evidence.occurredOn
        if (previous.completedOn == day) {
            return ReviewDecision(
                previous,
                "Sin cambio de etapa: $day ya registró el repaso de esta habilidad; " +
                    "intentarlo dos veces el mismo día no demuestra retención.",
            )
        }

        val (stage, reason) = when (evidence.quality) {
            EvidenceQuality.CORRECT_UNAIDED -> {
                val next = (previous.stage + 1).coerceAtMost(ReviewLadder.maxStage)
                if (next == previous.stage) {
                    next to "Ya en la última etapa (${ReviewLadder.maxStage + 1} niveles): se mantiene el intervalo largo."
                } else {
                    next to "Recuerda sin pista: etapa ${previous.stage} → $next."
                }
            }

            EvidenceQuality.CORRECT_ASSISTED -> previous.stage to
                "Recuerda solo con pista: la etapa ${previous.stage} no avanza y se repite su intervalo."

            EvidenceQuality.INCORRECT -> 0 to
                "El recuerdo falló: la etapa vuelve a 0 y el repaso se programa al día siguiente."
        }

        // Se aplica el intervalo que la etapa actual ya ganó; fallar lo devuelve al principio de la escalonera.
        val intervalDays = if (evidence.quality == EvidenceQuality.INCORRECT) {
            ReviewLadder.intervalOf(0)
        } else {
            ReviewLadder.intervalOf(previous.stage)
        }
        val scheduledOn = day.plus(intervalDays.toInt(), DateTimeUnit.DAY)
        return ReviewDecision(
            state = ReviewState(stage, scheduledOn, day, evidence.quality),
            reason = "$reason Próximo repaso: $scheduledOn ($intervalDays " +
                pluralEs(intervalDays.toInt(), "día", "días") + ").",
        )
    }

    /**
     * Señal, no sanción: un repaso vencido mucho después de su fecha baja la confianza en el nivel
     * actual, pero el nivel solo cambia con evidencia real.
     */
    fun atRisk(state: ReviewState, day: LocalDate): Boolean {
        if (state.scheduledOn == null) return false
        return state.daysOverdue(day) > ReviewLadder.intervalOf(state.stage)
    }
}

internal fun daysBetween(from: LocalDate, to: LocalDate): Long {
    val diff = to.toEpochDays() - from.toEpochDays()
    return if (diff < 0L) 0L else diff
}
