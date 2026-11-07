package mx.matica.learning

import kotlinx.datetime.LocalDate
import mx.matica.learning.mastery.MasteryChange
import mx.matica.learning.mastery.MasteryLevel
import mx.matica.learning.mastery.MasteryRules
import mx.matica.learning.mastery.MasteryThresholds
import mx.matica.learning.mastery.SkillEvidence
import mx.matica.learning.review.ReviewDecision
import mx.matica.learning.review.ReviewRules
import mx.matica.learning.review.ReviewState
import mx.matica.learning.skills.DefaultCurriculum
import mx.matica.learning.skills.LearningPath
import mx.matica.learning.skills.SkillGraph
import mx.matica.learning.text.pluralEs
import mx.matica.math.skills.SkillId

data class SkillProgress(
    val skill: SkillId,
    val level: MasteryLevel = MasteryLevel.NOT_INTRODUCED,
    val window: List<SkillEvidence> = emptyList(),
    val review: ReviewState = ReviewState(),
)

/** Estado del estudiante. Los repasos vencidos se responden primero: son evidencia más vieja que hay que revalidar. */
data class LearnerProfile(val progress: Map<SkillId, SkillProgress> = emptyMap()) {

    fun progressOf(skill: SkillId): SkillProgress = progress[skill] ?: SkillProgress(skill)

    fun levelOf(skill: SkillId): MasteryLevel = progressOf(skill).level

    /**
     * "Dominada" significa competente o fluido. Una habilidad apenas iniciada no desbloquea la
     * siguiente: el grafo exige que el estudiante pueda operar sin pista antes de composiciones.
     */
    fun mastered(): Set<SkillId> = progress.values
        .filter { it.level == MasteryLevel.PROFICIENT || it.level == MasteryLevel.FLUENT }
        .map { it.skill }
        .toSet()

    fun atRisk(day: LocalDate): List<SkillProgress> = progress.values
        .filter { it.review.isDueOn(day) && ReviewRules.atRisk(it.review, day) }
        .sortedBy { it.skill.name }

    companion object {
        val EMPTY = LearnerProfile()
    }
}

data class Recording(val profile: LearnerProfile, val mastery: MasteryChange, val review: ReviewDecision)

data class Recommendation(
    val skill: SkillId,
    val level: MasteryLevel,
    val reason: String,
    val prerequisitesLeft: List<SkillId>,
)

data class ReviewDue(val skill: SkillId, val level: MasteryLevel, val scheduledOn: LocalDate, val daysOverdue: Long)

data class StudyPlan(
    val reviewsDue: List<ReviewDue>,
    val nextSkills: List<Recommendation>,
    val reason: String,
)

/**
 * Fachada del motor de aprendizaje. No calcula matemática: recibe veredictos ya demostrados por
 * `MathEngine` y decide qué hacer con ellos.
 */
class LearningEngine(private val graph: SkillGraph = DefaultCurriculum.graph) {

    fun record(profile: LearnerProfile, evidence: SkillEvidence): Recording {
        val previous = profile.progressOf(evidence.skill)
        val window = (previous.window + evidence).takeLast(MasteryThresholds.WINDOW_EVIDENCES)
        val mastery = MasteryRules.evaluate(evidence.skill, previous.level, window)
        val review = ReviewRules.advance(previous.review, evidence)
        val updated = previous.copy(level = mastery.to, window = window, review = review.state)
        return Recording(profile.copy(progress = profile.progress + (evidence.skill to updated)), mastery, review)
    }

    fun recordAll(profile: LearnerProfile, evidences: List<SkillEvidence>): Pair<LearnerProfile, List<Recording>> {
        var current = profile
        val recordings = mutableListOf<Recording>()
        evidences.forEach { evidence ->
            val recording = record(current, evidence)
            current = recording.profile
            recordings += recording
        }
        return current to recordings
    }

    fun plan(profile: LearnerProfile, today: LocalDate, limit: Int = 3): StudyPlan {
        val reviewsDue = profile.progress.values
            .filter { it.review.isDueOn(today) }
            .map { progress ->
                ReviewDue(
                    skill = progress.skill,
                    level = progress.level,
                    scheduledOn = requireNotNull(progress.review.scheduledOn),
                    daysOverdue = progress.review.daysOverdue(today),
                )
            }
            .sortedWith(compareByDescending<ReviewDue> { it.daysOverdue }.thenBy { it.skill.name })
            .take(limit)

        val mastered = profile.mastered()
        val nextSkills = graph.availableSkills(mastered)
            .take(limit)
            .map { skill ->
                Recommendation(
                    skill = skill,
                    level = profile.levelOf(skill),
                    reason = graph.pathTo(skill, mastered).reason,
                    prerequisitesLeft = graph.prerequisitesOf(skill).filterNot { it in mastered }.sortedBy { it.name },
                )
            }

        val reason = when {
            reviewsDue.isNotEmpty() && nextSkills.isEmpty() ->
                "Solo repasos: ${reviewsDue.size} " +
                    pluralEs(reviewsDue.size, "habilidad vencida", "habilidades vencidas") +
                    " y ninguna nueva disponible."

            reviewsDue.isNotEmpty() ->
                "Repasos primero (${reviewsDue.size}) y luego ${nextSkills.size} " +
                    pluralEs(nextSkills.size, "habilidad nueva", "habilidades nuevas") + "."

            nextSkills.isEmpty() -> "Sin trabajo nuevo: ninguna habilidad quedó disponible con el dominio actual."
            else -> "${nextSkills.size} " +
                pluralEs(nextSkills.size, "habilidad nueva disponible", "habilidades nuevas disponibles") + "."
        }
        return StudyPlan(reviewsDue, nextSkills, reason)
    }

    fun pathTo(profile: LearnerProfile, target: SkillId): LearningPath = graph.pathTo(target, profile.mastered())
}
