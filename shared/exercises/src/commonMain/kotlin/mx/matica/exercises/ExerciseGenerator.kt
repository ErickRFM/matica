package mx.matica.exercises

import kotlin.random.Random
import mx.matica.math.MathEngine
import mx.matica.math.domain.Restriction
import mx.matica.math.parser.ParseOutcome
import mx.matica.math.skills.SkillId
import mx.matica.math.solve.SolveOutcome
import mx.matica.math.verify.VerificationOutcome

/**
 * Generador determinista: la misma `(plantilla, semilla)` siempre produce el mismo ejercicio. Así un
 * intento guardado en el dispositivo se puede reproducir en el servidor sin confiar en el estado del
 * cliente, y una misma fila de la base de datos no describe dos problemas distintos.
 */
class ExerciseGenerator(
    private val templates: List<ExerciseTemplate> = ExerciseTemplates.all,
    private val engine: MathEngine = MathEngine(),
) {
    private val byId = templates.associateBy { it.id }

    fun templateIds(): List<String> = templates.map { it.id }

    fun skillOf(templateId: String): SkillId? = byId[templateId]?.skill

    /** Sorteo en el orden declarado de los rangos: un solo `Random` por semilla, no uno por parámetro. */
    fun parametersFor(template: ExerciseTemplate, seed: Long): ParameterSet {
        val random = Random(seed)
        return ParameterSet(
            template.ranges.associate { range ->
                range.name to random.nextInt(range.fromInclusive, range.toInclusive + 1)
            },
        )
    }

    fun generate(templateId: String, seed: Long, maxAttempts: Int = 8): GenerationOutcome {
        require(maxAttempts >= 1) { "Sin intentos no se puede generar nada: maxAttempts = $maxAttempts." }
        val template = byId[templateId]
            ?: return GenerationOutcome.Rejected(listOf("La plantilla '$templateId' no está registrada."))

        var lastRejection: GenerationOutcome.Rejected? = null
        repeat(maxAttempts) { attempt ->
            val attemptSeed = seed + attempt
            when (val outcome = template.build(parametersFor(template, attemptSeed), attemptSeed)) {
                is GenerationOutcome.Ready -> {
                    val problem = disagreement(outcome.instance, template)
                        ?: return outcome
                    return GenerationOutcome.Rejected(listOf(problem))
                }

                is GenerationOutcome.Rejected -> lastRejection = outcome
            }
        }
        return requireNotNull(lastRejection)
    }

    /**
     * Serie para una sesión de práctica. Los textos se deduplican (semillas cercanas pueden repetir el
     * mismo ejercicio) y cada rechazo queda registrado con su semilla, sin desaparecer en un `filter`.
     */
    fun generateBatch(templateId: String, seed: Long, count: Int, stride: Long = 100): GenerationBatch {
        require(count >= 0) { "count no puede ser negativo: $count." }
        val instances = LinkedHashMap<String, ExerciseInstance>()
        val failures = mutableListOf<String>()
        var index = 0
        while (instances.size < count && index < count) {
            val baseSeed = seed + index * stride
            when (val outcome = generate(templateId, baseSeed)) {
                is GenerationOutcome.Ready -> {
                    if (outcome.instance.equationText !in instances) {
                        instances[outcome.instance.equationText] = outcome.instance
                    } else {
                        failures += "semilla $baseSeed: texto repetido ${outcome.instance.equationText}"
                    }
                }

                is GenerationOutcome.Rejected -> failures += "semilla $baseSeed: ${outcome.reasons}"
            }
            index++
        }
        return GenerationBatch(instances.values.toList(), failures)
    }

    /**
     * El motor tiene la última palabra. Si el texto no se relee, si nace con una restricción de dominio
     * inesperada, si el resolutor no llega a la misma respuesta o si la sustitución no satisface, el generador
     * lo devuelve como rechazo en lugar de emitir un ejercicio cuya respuesta no está demostrada.
     */
    private fun disagreement(instance: ExerciseInstance, template: ExerciseTemplate): String? {
        if (engine.parse(instance.equationText) !is ParseOutcome.Success) {
            return "El texto generado no se volvió a leer: '${instance.equationText}'."
        }
        val restrictions = engine.restrictions(instance.equationText)
        if (template.skill == SkillId.DOMAIN_RESTRICTIONS) {
            val notEqual = restrictions.filterIsInstance<Restriction.NotEqualTo>().singleOrNull()
                ?: return "Se esperaba una restricción de dominio única pero se obtuvo: $restrictions en '${instance.equationText}'."
            if (notEqual.variable != instance.variable || notEqual.excluded != instance.solution) {
                return "La restricción de dominio $notEqual no coincide con la solución guardada ${instance.solution} para ${instance.variable} en '${instance.equationText}'."
            }
            return null
        }
        if (restrictions.isNotEmpty()) {
            return "El ejercicio nació con restricción de dominio $restrictions: '${instance.equationText}'."
        }
        val solved = engine.solve(instance.equationText)
        if (solved !is SolveOutcome.Unique || solved.value != instance.solution) {
            return "El resolutor obtuvo $solved, no la respuesta guardada ${instance.solution} en '${instance.equationText}'."
        }
        val substitution = engine.verifySolution(instance.equationText, template.variable, instance.solution)
        if (substitution !is VerificationOutcome.Satisfies) {
            return "Sustituir ${instance.solution} no satisfizo '${instance.equationText}': $substitution."
        }
        return null
    }
}

data class GenerationBatch(val instances: List<ExerciseInstance>, val failures: List<String>)
