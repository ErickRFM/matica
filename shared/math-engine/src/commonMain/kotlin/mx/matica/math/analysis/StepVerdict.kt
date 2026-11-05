package mx.matica.math.analysis

import mx.matica.math.numeric.Rational
import mx.matica.math.poly.Monomial
import mx.matica.math.poly.Polynomial
import mx.matica.math.skills.SkillId
import mx.matica.math.transform.RuleId

enum class StepError(val id: String, val labelEs: String) {
    NOT_BALANCED_OPERATION("not-balanced-operation", "La operación no se aplicó igual a los dos lados"),
    ONE_SIDED_OPERATION("one-sided-operation", "Se modificó un solo lado de la ecuación"),
    DISTRIBUTION_ERROR("distribution-error", "Error al distribuir el factor"),
    SIGN_ERROR("sign-error", "Error de signos al quitar paréntesis"),
    ORDER_OF_OPERATIONS("order-of-operations", "Se violó la jerarquía de operaciones"),
    ARITHMETIC_ERROR("arithmetic-error", "Procedimiento correcto, cuenta incorrecta"),
    DIVISION_BY_ZERO("division-by-zero", "Se dividió entre cero"),
    LOST_SOLUTION("lost-solution", "El paso descarta una solución válida"),
    ADDED_SOLUTION("added-solution", "El paso introduce una solución extraña"),
    UNSUPPORTED_STEP("unsupported-step", "El motor no reconoce esta transformación"),
}

/**
 * Resultado de validar un paso. `Indeterminate` es distinto de `Invalid`: aquí el motor admite
 * no poder demostrar nada, en lugar de acusar al estudiante de un error que no puede probar.
 */
sealed interface StepVerdict {
    data class Valid(val rule: RuleId, val skills: Set<SkillId>, val notes: List<String> = emptyList()) : StepVerdict

    data class Invalid(val error: StepError, val detail: String, val repairHint: String) : StepVerdict

    data class Indeterminate(val reason: String) : StepVerdict

    data class SyntaxProblem(val position: Int, val reason: String, val suggestion: String) : StepVerdict
}

/** Descripción legible de la corrección que el estudiante debe seguir. */
data class StepCorrection(val expectedLine: String, val explanation: String, val skills: Set<SkillId>)

internal fun Polynomial.nonConstantTerms(): List<Pair<Monomial, Rational>> =
    sortedTerms.filter { (monomial, _) -> !monomial.isConstant }

internal fun Polynomial.isProportionalTo(other: Polynomial): Boolean = proportionalityFactorTo(other) != null
