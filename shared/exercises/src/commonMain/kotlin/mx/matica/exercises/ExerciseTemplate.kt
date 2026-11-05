package mx.matica.exercises

import mx.matica.math.numeric.Rational
import mx.matica.math.skills.SkillId

/**
 * Ejercicio concreto generado desde una plantilla. `solution` es la respuesta con la que se construyó
 * la ecuación, no la que alguien dedujo después.
 */
data class ExerciseInstance(
    val templateId: String,
    val variable: String,
    val equationText: String,
    val solution: Rational,
    val parameters: ParameterSet,
    val seed: Long,
)

sealed interface GenerationOutcome {
    data class Ready(val instance: ExerciseInstance) : GenerationOutcome

    data class Rejected(val reasons: List<String>) : GenerationOutcome
}

/**
 * Plantilla parametrizada. `build` es pura y no se autoverifica: el sello de Correcto lo pone
 * `ExerciseGenerator` contra `MathEngine`, para que una plantilla no pueda declararse buena a sí misma.
 */
interface ExerciseTemplate {
    val id: String
    val labelEs: String
    val skill: SkillId
    val variable: String
    val ranges: List<ParameterRange>

    fun build(params: ParameterSet, seed: Long): GenerationOutcome
}
