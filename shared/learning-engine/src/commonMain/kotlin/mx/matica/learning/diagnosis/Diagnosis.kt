package mx.matica.learning.diagnosis

import kotlinx.datetime.LocalDate
import mx.matica.learning.mastery.EvidenceQuality
import mx.matica.learning.mastery.SkillEvidence
import mx.matica.math.MathEngine
import mx.matica.math.eval.EvalOutcome
import mx.matica.math.domain.Restriction
import mx.matica.math.numeric.Rational
import mx.matica.math.poly.Normalizer
import mx.matica.math.poly.Polynomial
import mx.matica.math.poly.PolyOutcome
import mx.matica.math.skills.SkillId
import mx.matica.math.solve.SolveOutcome
import kotlin.random.Random

/** Cómo se demuestra la respuesta de un ítem. */
enum class DiagnosisKind {

    /** Dos textos distintos, misma expresión algebraica. */
    EXPRESSION,

    /** Dos ecuaciones distintas, misma solución única. */
    EQUATION,

    /** Cuál de los valores hace indefinida a la expresión (denominador cero). */
    UNDEFINED_AT,
}

/**
 * Ítem del diagnóstico. No guarda la respuesta correcta: la demuestra el motor. Un ítem mal escrito
 * (ninguna alternativa equivalente, o dos alternativas equivalentes) se detecta al validar el
 * instrumento, no cuando un estudiante lo contesta.
 */
data class DiagnosisItem(
    val id: String,
    val skill: SkillId,
    val kind: DiagnosisKind,
    val band: Int,
    val promptEs: String,
    val reference: String,
    val options: List<String>,
) {
    init {
        require(options.size >= 2) { "Un ítem sin alternativas no es un ítem: $id." }
        require(options.distinct().size == options.size) { "Alternativas repetidas en $id." }
    }
}

/** Pregunta lista para mostrarse: `correctIndex` salió de una verificación, no de una lista de respuestas. */
data class DiagnosisQuestion(
    val item: DiagnosisItem,
    val options: List<String>,
    val correctIndex: Int,
)

/** Lo que el diagnóstico puede afirmar de una habilidad. */
data class Placement(
    val skill: SkillId,
    val asked: Boolean,
    val correct: Boolean?,
    val evidence: SkillEvidence?,
    val reasonEs: String,
)

data class DiagnosisOutcome(
    val placements: List<Placement>,
    val evidences: List<SkillEvidence>,
    val stoppedAtBand: Int?,
    val summaryEs: String,
) {
    val askedCount: Int get() = placements.count { it.asked }

    val correctCount: Int get() = placements.count { it.correct == true }

    fun placementOf(skill: SkillId): Placement? = placements.firstOrNull { it.skill == skill }
}

/**
 * Instrumento de ubicación inicial: una pregunta por habilidad, en cuatro bandas crecientes.
 *
 * Un ítem acertado no convierte a nadie en competente: el resultado se entrega como evidencia al
 * `LearningEngine`, que aplica sus propias reglas de dominio. Así el diagnóstico no puede inventar
 * niveles que el modelo de dominio negaría.
 *
 * Regla de parada: con dos fallos en una banda, las bandas superiores no se preguntan. Insistir en
 * álgebra a quien falló enteros y fracciones sólo produce ruido y abandono.
 */
class DiagnosisSession(
    private val engine: MathEngine = MathEngine(),
    items: List<DiagnosisItem> = DiagnosisInstrument.items,
    private val seed: Long = 1L,
) {
    private val order: List<DiagnosisItem> = items.shuffled(Random(seed))
    private val answers = LinkedHashMap<String, Int>()

    val items: List<DiagnosisItem> get() = order

    val totalItems: Int get() = order.size

    val answeredCount: Int get() = answers.size

    /** Ítems que quedan por responder dentro de las bandas alcanzables. */
    fun pendingQuestions(): List<DiagnosisQuestion> =
        reachable().filterNot { it.id in answers }.mapNotNull { questionFor(it) }

    val isFinished: Boolean get() = pendingQuestions().isEmpty()

    /** `null` cuando el ítem no se puede demostrar: mejor no preguntarlo que adivinar la clave. */
    fun questionFor(item: DiagnosisItem): DiagnosisQuestion? {
        val proven = item.options.mapIndexed { index, option -> index to judge(item, option) }
        val correct = proven.filter { it.second == true }
        if (correct.size != 1 || proven.any { it.second == null }) return null
        return DiagnosisQuestion(item, item.options, correct.first().first)
    }

    fun indexOfAnswer(itemId: String): Int? = answers[itemId]

    /** Registrar una respuesta. Devuelve `false` si el ítem no existe, ya fue respondido o no es preguntable. */
    fun answer(itemId: String, optionIndex: Int): Boolean {
        val item = order.firstOrNull { it.id == itemId } ?: return false
        if (item.id in answers) return false
        if (item.id !in reachable().map { it.id }) return false
        val question = questionFor(item) ?: return false
        require(optionIndex in question.options.indices) { "Alternativa fuera de rango en $itemId." }
        answers[itemId] = optionIndex
        return true
    }

    fun outcome(occurredOn: LocalDate): DiagnosisOutcome {
        val reachableIds = reachable().map { it.id }.toSet()
        val stopped = stoppedAtBand()
        val placements = order.mapNotNull { item ->
            val chosen = answers[item.id]
            if (chosen == null) {
                Placement(
                    skill = item.skill,
                    asked = false,
                    correct = null,
                    evidence = null,
                    reasonEs = if (item.id in reachableIds) {
                        "Quedó pendiente: se puede responder cuando el estudiante llegue a este ítem."
                    } else {
                        "No se preguntó: la banda ${item.band} quedó cortada por errores en una banda anterior."
                    },
                )
            } else {
                val question = questionFor(item) ?: return@mapNotNull null
                val correct = chosen == question.correctIndex
                Placement(
                    skill = item.skill,
                    asked = true,
                    correct = correct,
                    evidence = SkillEvidence(
                        skill = item.skill,
                        quality = if (correct) EvidenceQuality.CORRECT_UNAIDED else EvidenceQuality.INCORRECT,
                        occurredOn = occurredOn,
                        origin = "diagnosis:${item.id}",
                    ),
                    reasonEs = if (correct) {
                        "'${item.options[question.correctIndex]}' ${relationOf(item.kind)} '${item.reference}' así lo verificó el motor."
                    } else {
                        "'${item.options[chosen]}' no ${relationOf(item.kind)} '${item.reference}': el motor no lo verifica."
                    },
                )
            }
        }
        val asked = placements.filter { it.asked }
        val hits = asked.count { it.correct == true }
        val tail = stopped?.let { " Se cortó en la banda $it." } ?: ""
        val summary = when {
            asked.isEmpty() -> "Sin respuestas todavía: el diagnóstico no puede afirmar nada."
            hits == 0 -> "Ningún ítem verificado.$tail Se empieza por la banda 1."
            hits == asked.size -> "Los $hits ítems respondidos fueron verificados.$tail"
            else -> "$hits de ${asked.size} ítems verificados.$tail"
        }
        return DiagnosisOutcome(placements, placements.mapNotNull { it.evidence }, stopped, summary)
    }

    /**
     * Juicio del motor sobre una alternativa. `null` = no lo puede afirmar, y eso descalifica el ítem:
     * preguntar algo que el propio motor no domina sería una pregunta trampa.
     */
    fun judge(item: DiagnosisItem, option: String): Boolean? = when (item.kind) {
        DiagnosisKind.EXPRESSION -> {
            val reference = polynomialOf(item.reference) ?: return null
            val candidate = polynomialOf(option) ?: return null
            reference == candidate
        }

        DiagnosisKind.EQUATION -> {
            val reference = solutionOf(item.reference) ?: return null
            val candidate = solutionOf(option) ?: return null
            reference == candidate
        }

        DiagnosisKind.UNDEFINED_AT -> {
            val excluded = excludedValues(item.reference)
            // `evaluate` y no un `Expr.Const`: «-5» se lee como Neg(5) y seguiría siendo un número.
            when (val outcome = engine.evaluate(option)) {
                is EvalOutcome.Value -> outcome.value in excluded
                else -> null
            }
        }
    }

    /** Valores prohibidos que el motor ya calculó; sin ellos no hay ítem de dominio que preguntar. */
    private fun excludedValues(text: String): Set<Rational> {
        val restrictions = engine.restrictions(text)
        if (restrictions.any { it is Restriction.Unresolved }) return emptySet()
        return restrictions.filterIsInstance<Restriction.NotEqualTo>().map { it.excluded }.toSet()
    }

    private fun relationOf(kind: DiagnosisKind): String = when (kind) {
        DiagnosisKind.EXPRESSION -> "es equivalente a"
        DiagnosisKind.EQUATION -> "tiene la misma solución que"
        DiagnosisKind.UNDEFINED_AT -> "hace indefinida a"
    }

    private fun polynomialOf(text: String): Polynomial? {
        val expression = engine.parseOrNull(text) ?: return null
        return (Normalizer.of(expression) as? PolyOutcome.Value)?.polynomial
    }

    private fun solutionOf(text: String): Rational? =
        (engine.solve(text) as? SolveOutcome.Unique)?.value

    private fun stoppedAtBand(): Int? = bands().firstOrNull { band ->
        failuresOf(band) >= STOP_AFTER_FAILURES
    }

    private fun failuresOf(band: Int): Int = order.filter { it.band == band }.count { item ->
        val chosen = answers[item.id]
        val question = questionFor(item)
        chosen != null && question != null && chosen != question.correctIndex
    }

    private fun reachable(): List<DiagnosisItem> {
        val stopped = stoppedAtBand()
        return if (stopped == null) order else order.filter { it.band <= stopped }
    }

    private fun bands(): List<Int> = order.map { it.band }.distinct().sorted()

    companion object {
        const val STOP_AFTER_FAILURES: Int = 2
    }
}

/** Auditoría del instrumento: cada ítem debe tener exactamente una alternativa demostrable. */
object DiagnosisInstrumentValidator {

    fun problems(
        items: List<DiagnosisItem> = DiagnosisInstrument.items,
        engine: MathEngine = MathEngine(),
    ): List<String> {
        val report = mutableListOf<String>()
        items.forEach { item ->
            val session = DiagnosisSession(engine, listOf(item))
            val judgements = item.options.map { session.judge(item, it) }
            if (judgements.any { it == null }) {
                report += "${item.id}: el motor no pudo evaluar alguna alternativa de '${item.reference}'."
            }
            val correct = judgements.count { it == true }
            if (correct != 1) {
                report += "${item.id}: $correct alternativas demostradamente equivalentes a '${item.reference}' (se exige 1)."
            }
        }
        return report
    }
}
