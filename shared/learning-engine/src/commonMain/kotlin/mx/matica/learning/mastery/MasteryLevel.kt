package mx.matica.learning.mastery

import kotlinx.datetime.LocalDate
import mx.matica.math.skills.SkillId

enum class EvidenceQuality(val id: String, val labelEs: String) {
    CORRECT_UNAIDED("correct-unaided", "Correcto sin pista"),
    CORRECT_ASSISTED("correct-assisted", "Correcto con pista"),
    INCORRECT("incorrect", "Incorrecto"),
}

/**
 * Una observación aislada del trabajo del estudiante. `origin` guarda la regla o el error que la
 * produjo, así cualquier nivel se puede rastrear hasta la evidencia que lo sostiene.
 */
data class SkillEvidence(
    val skill: SkillId,
    val quality: EvidenceQuality,
    val occurredOn: LocalDate,
    val origin: String,
) {
    val isUnaided: Boolean get() = quality == EvidenceQuality.CORRECT_UNAIDED
}

enum class MasteryLevel(val id: String, val labelEs: String) {
    NOT_INTRODUCED("not-introduced", "Sin introducción"),
    NOVICE("novice", "Novato"),
    DEVELOPING("developing", "En desarrollo"),
    PROFICIENT("proficient", "Competente"),
    FLUENT("fluent", "Fluido");

    val retreated: MasteryLevel get() = entries[(ordinal - 1).coerceAtLeast(DEVELOPING.ordinal)]
}
