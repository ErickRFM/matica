package mx.matica.exercises

import mx.matica.math.analysis.StepError
import mx.matica.math.skills.SkillId

/**
 * Paso individual resuelto en un ejercicio de "Detecta el Error".
 */
data class WorkedStep(
    val stepNumber: Int,
    val expression: String,
    val explanation: String,
    val isErroneous: Boolean = false,
    val canonicalError: StepError? = null,
)

/**
 * Opción de justificación para la pregunta "¿Por qué este paso no es válido?".
 */
data class ErrorReasonOption(
    val id: String,
    val text: String,
    val isCorrect: Boolean,
    val explanation: String,
)

/**
 * Ejercicio de metacognición "Detecta el Error" (Spot the Error).
 * Muestra el desarrollo propuesto por un compañero o estudiante virtual.
 *
 * Si [erroneousStepNumber] es null, el desarrollo es completamente correcto (ejercicio de control
 * para mitigar el sesgo de complacencia o suposición automática de que siempre hay un error).
 */
data class ErrorDetectionExercise(
    val id: String,
    val title: String,
    val studentName: String,
    val initialProblem: String,
    val steps: List<WorkedStep>,
    val erroneousStepNumber: Int?,
    val targetSkill: SkillId,
    val reasonOptions: List<ErrorReasonOption>,
    val wrapUpPedagogy: String,
)
