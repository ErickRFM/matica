package mx.matica.presentation.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate
import mx.matica.exercises.ExerciseGenerator
import mx.matica.exercises.ExerciseTemplates
import mx.matica.exercises.GenerationOutcome
import mx.matica.learning.LearningEngine
import mx.matica.learning.LearnerProfile
import mx.matica.learning.Recording
import mx.matica.learning.StudyPlan
import mx.matica.learning.diagnosis.DiagnosisOutcome
import mx.matica.learning.diagnosis.DiagnosisSession
import mx.matica.learning.mastery.EvidenceFromVerdict
import mx.matica.learning.mastery.EvidenceQuality
import mx.matica.learning.mastery.MasteryLevel
import mx.matica.learning.mastery.SkillEvidence
import mx.matica.learning.skills.DefaultCurriculum
import mx.matica.learning.skills.SkillGraph
import mx.matica.math.MathEngine
import mx.matica.math.analysis.StepVerdict
import mx.matica.math.hints.Hint
import mx.matica.math.hints.HintLevel
import mx.matica.math.numeric.Rational
import mx.matica.math.skills.SkillId
import mx.matica.math.solve.SolveOutcome
import mx.matica.math.verify.VerificationOutcome
import mx.matica.presentation.exercise.ExerciseRenderPlan
import mx.matica.presentation.exercise.renderPlan
import mx.matica.presentation.math.RouteNode
import mx.matica.presentation.math.routeNodes

/** Modo de color elegido por el estudiante; `SYSTEM` respeta el tema del dispositivo. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Ajustes persistentes del estudiante. La accesibilidad no es un adorno: controla el render. */
data class MaticaSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val motionScale: Float? = null,
    val reduceTransitions: Boolean = false,
    val grade: String = "2.º de secundaria",
    val showHintsByDefault: Boolean = false,
)

/** Un intento dentro de una lección: el texto antes y después, más el veredicto del motor. */
data class Attempt(val before: String, val after: String, val verdict: StepVerdict, val usedHint: Boolean)

/** Lección = un ejercicio generado con su plan de render, en el orden en que se presenta. */
data class Lesson(val number: Int, val title: String, val plan: ExerciseRenderPlan)

/**
 * Estado vivo de la app. Es una clase normal con `StateFlow`-like observabilidad de Compose, no un
 * `ViewModel`: así el mismo estado corre en pruebas de `commonTest` sin depender de Android.
 *
 * Nada de esto habla con un servidor: el backend está bloqueado, así que el perfil vive en memoria y
 * [offline] lo declara en la UI. Fingir sincronización sería peor que decir que no existe.
 */
class MaticaSession(
    val math: MathEngine = MathEngine(),
    val learning: LearningEngine = LearningEngine(),
    private val generator: ExerciseGenerator = ExerciseGenerator(),
    val graph: SkillGraph = DefaultCurriculum.graph,
    var today: LocalDate = LocalDate(2026, 11, 5),
) {
    var profile: LearnerProfile by mutableStateOf(LearnerProfile.EMPTY)
        private set

    var settings: MaticaSettings by mutableStateOf(MaticaSettings())
        private set

    var diagnosed: Boolean by mutableStateOf(false)
        private set

    var offline: Boolean by mutableStateOf(true)
        private set

    var xp: Int by mutableStateOf(0)
        private set

    var gems: Int by mutableStateOf(0)
        private set

    var streak: Int by mutableStateOf(0)
        private set

    var message: String? by mutableStateOf(null)
        private set

    private var diagnosis: DiagnosisSession? = null
    private var recordings: List<Recording> = emptyList()

    /** La plantilla que enseña cada habilidad. `null` = el currículo aún no tiene ejercicios ahí. */
    private val templateOfSkill: Map<SkillId, String> = ExerciseTemplates.all
        .associateBy({ it.skill }, { it.id })

    fun updateSettings(transform: (MaticaSettings) -> MaticaSettings) {
        settings = transform(settings)
        message = "Ajustes actualizados: ${settings.themeMode}."
    }

    fun setConnection(connected: Boolean) {
        offline = !connected
    }

    // --- Diagnóstico ---------------------------------------------------------------

    fun startDiagnosis(seed: Long = 1L): DiagnosisSession {
        val session = DiagnosisSession(math, seed = seed)
        diagnosis = session
        diagnosisAnswered = 0
        return session
    }

    /**
     * Preguntas respondidas, en estado observable. `DiagnosisSession` es dominio puro y no puede
     * llevar `mutableStateOf`; sin este contador la pantalla de diagnóstico seguiría mostrando la
     * primera pregunta aunque el estudiante ya hubiera contestado cinco.
     */
    var diagnosisAnswered: Int by mutableStateOf(0)
        private set

    fun currentDiagnosis(): DiagnosisSession? = diagnosis

    fun answerDiagnosis(itemId: String, optionIndex: Int): Boolean {
        val session = diagnosis ?: return false
        val accepted = session.answer(itemId, optionIndex)
        if (accepted) diagnosisAnswered = session.answeredCount
        return accepted
    }

    /** Cierra el diagnóstico y registra su evidencia. Sin respuestas, el perfil no cambia. */
    fun finishDiagnosis(): DiagnosisOutcome? {
        val session = diagnosis ?: return null
        val outcome = session.outcome(today)
        if (outcome.evidences.isNotEmpty()) {
            val (updated, changes) = learning.recordAll(profile, outcome.evidences)
            profile = updated
            recordings = recordings + changes
        }
        diagnosed = true
        xp += outcome.correctCount * XP_PER_DIAGNOSIS_HIT
        recomputeStreak()
        return outcome
    }

    // --- Ruta y plan ---------------------------------------------------------------

    fun nodes(): List<RouteNode> = routeNodes(
        graph = graph,
        levels = { skill -> profile.levelOf(skill) },
        mastered = profile.mastered(),
        openable = { skill -> templateOfSkill[skill] != null },
    )

    fun studyPlan(): StudyPlan = learning.plan(profile, today)

    fun levelOf(skill: SkillId): MasteryLevel = profile.levelOf(skill)

    // --- Lecciones -----------------------------------------------------------------

    /** Lecciones de una habilidad: tres ejercicios generados con semillas deterministas. */
    fun lessonsFor(skill: SkillId, count: Int = 3): List<Lesson> {
        val templateId = templateOfSkill[skill] ?: return emptyList()
        val lessons = mutableListOf<Lesson>()
        repeat(count) { index ->
            val seed = BASE_SEED + index * SEED_STRIDE + skill.ordinal.toLong()
            val outcome = generator.generate(templateId, seed)
            val instance = (outcome as? GenerationOutcome.Ready)?.instance
            val plan = instance?.let { renderPlan(it, math) }
            if (plan != null) {
                lessons += Lesson(number = lessons.size + 1, title = "${skill.labelEs} · ejercicio ${index + 1}", plan = plan)
            } else {
                message = "La generación falló en '$templateId' semilla $seed: ${(outcome as? GenerationOutcome.Rejected)?.reasons}"
            }
        }
        return lessons
    }

    fun templateIdFor(skill: SkillId): String? = templateOfSkill[skill]

    // --- Práctica ------------------------------------------------------------------

    var attempts: List<Attempt> by mutableStateOf(emptyList())
        private set

    var lastVerdict: StepVerdict? by mutableStateOf(null)
        private set

    var lastAnswer: VerificationOutcome? by mutableStateOf(null)
        private set

    fun startPractice(templateId: String, seed: Long): ExerciseRenderPlan? {
        val outcome = generator.generate(templateId, seed)
        val instance = (outcome as? GenerationOutcome.Ready)?.instance
        if (instance == null) {
            message = "No se pudo generar el ejercicio: ${(outcome as? GenerationOutcome.Rejected)?.reasons}"
            return null
        }
        attempts = emptyList()
        lastVerdict = null
        lastAnswer = null
        return renderPlan(instance, math)
    }

    /**
     * Registra un paso escrito por el estudiante. El veredicto lo emite `MathEngine`; la sesión sólo
     * traduce ese veredicto a evidencia y a recompensas, nunca a «correcto» por su cuenta.
     */
    fun submitStep(plan: ExerciseRenderPlan, before: String, after: String, usedHint: Boolean = false): StepVerdict {
        val verdict = math.checkStep(before, after)
        attempts = attempts + Attempt(before, after, verdict, usedHint)
        lastVerdict = verdict
        val evidence = evidenceOf(verdict, usedHint)
        if (evidence != null) {
            val recording = learning.record(profile, evidence)
            profile = recording.profile
            recordings = recordings + recording
            award(evidence.quality)
            recomputeStreak()
        }
        return verdict
    }

    /** Respuesta final propuesta por el estudiante, verificada por sustitución. */
    fun checkAnswer(plan: ExerciseRenderPlan, proposal: Rational): VerificationOutcome {
        val outcome = math.verifySolution(plan.equationText, plan.variable, proposal)
        lastAnswer = outcome
        val skill = skillOf(plan)
        if (skill != null) {
            val quality = when (outcome) {
                is VerificationOutcome.Satisfies -> if (usedHintOf(plan)) {
                    EvidenceQuality.CORRECT_ASSISTED
                } else {
                    EvidenceQuality.CORRECT_UNAIDED
                }

                else -> EvidenceQuality.INCORRECT
            }
            val recording = learning.record(
                profile,
                SkillEvidence(skill, quality, today, "answer:${plan.templateId}"),
            )
            profile = recording.profile
            recordings = recordings + recording
            award(quality)
            recomputeStreak()
        }
        return outcome
    }

    fun solutionOf(plan: ExerciseRenderPlan): Rational? =
        (math.solve(plan.equationText) as? SolveOutcome.Unique)?.value

    fun hintsOf(plan: ExerciseRenderPlan, upTo: HintLevel = HintLevel.NAME_RULE): List<Hint> =
        math.hints(plan.equationText, upTo)

    /** Nivel de pista mostrado: la capa superior revela la línea completa. */
    fun revealLine(plan: ExerciseRenderPlan): String? =
        math.hints(plan.equationText, HintLevel.SHOW_LINE).lastOrNull()?.text

    fun resetSession() {
        profile = LearnerProfile.EMPTY
        diagnosed = false
        xp = 0
        gems = 0
        streak = 0
        attempts = emptyList()
        lastVerdict = null
        lastAnswer = null
        recordings = emptyList()
        diagnosis = null
        diagnosisAnswered = 0
        message = "Sesión reiniciada: el perfil local vuelve a estar vacío."
    }

    val masteryChanges: List<Recording> get() = recordings

    private var hintedPlans: MutableSet<String> = mutableSetOf()

    fun markHintUsed(plan: ExerciseRenderPlan) {
        hintedPlans += plan.templateId + plan.equationText
    }

    private fun usedHintOf(plan: ExerciseRenderPlan): Boolean =
        (plan.templateId + plan.equationText) in hintedPlans

    /**
     * Traducción del veredicto a evidencia con la misma tabla que usa el motor de aprendizaje
     * ([EvidenceFromVerdict]): `Indeterminate` y `SyntaxProblem` no generan evidencia, y la sesión no
     * tiene una segunda opinión sobre cuándo se aprendió algo.
     */
    private fun evidenceOf(verdict: StepVerdict, usedHint: Boolean): SkillEvidence? =
        EvidenceFromVerdict.of(verdict, today, usedHint).firstOrNull()

    private fun skillOf(plan: ExerciseRenderPlan): SkillId? =
        ExerciseTemplates.byId(plan.templateId)?.skill

    /** Recompensa derivada de la calidad de la evidencia, nunca del tiempo que se pasa en pantalla. */
    private fun award(quality: EvidenceQuality) {
        val gained = when (quality) {
            EvidenceQuality.CORRECT_UNAIDED -> 12
            EvidenceQuality.CORRECT_ASSISTED -> 6
            EvidenceQuality.INCORRECT -> 0
        }
        if (gained == 0) return
        xp += gained
        if (xp % XP_PER_GEM == 0) gems += 1
    }

    /** Racha = días distintos con evidencia registrada. Sin evidencia, la racha no crece sola. */
    private fun recomputeStreak() {
        val days = profile.progress.values.flatMap { progress -> progress.window.map { it.occurredOn } }.toSet()
        streak = days.size
    }

    companion object {
        const val XP_PER_DIAGNOSIS_HIT: Int = 5
        const val XP_PER_GEM: Int = 100
        const val BASE_SEED: Long = 100_000L
        const val SEED_STRIDE: Long = 97L
    }
}
