package mx.matica.learning.mastery

import kotlinx.datetime.LocalDate
import mx.matica.math.analysis.StepError
import mx.matica.math.analysis.StepVerdict
import mx.matica.math.skills.SkillId

/**
 * Traduce un veredicto del motor a evidencia de aprendizaje. `Indeterminate`, `SyntaxProblem` y
 * `UNSUPPORTED_STEP` no producen evidencia: registrarla sería inventar un dominio o un error que el
 * motor no demostró.
 */
object EvidenceFromVerdict {

    fun of(verdict: StepVerdict, occurredOn: LocalDate, usedHint: Boolean = false): List<SkillEvidence> = when (verdict) {
        is StepVerdict.Valid -> {
            val quality = if (usedHint) EvidenceQuality.CORRECT_ASSISTED else EvidenceQuality.CORRECT_UNAIDED
            verdict.skills.map { skill -> SkillEvidence(skill, quality, occurredOn, verdict.rule.id) }
        }

        is StepVerdict.Invalid -> skillsOf(verdict.error).map { skill ->
            SkillEvidence(skill, EvidenceQuality.INCORRECT, occurredOn, verdict.error.id)
        }

        is StepVerdict.Indeterminate -> emptyList()
        is StepVerdict.SyntaxProblem -> emptyList()
    }

    /** El error nombra la habilidad que falló; sin esta tabla el diagnóstico no tendría destino. */
    private fun skillsOf(error: StepError): Set<SkillId> = when (error) {
        StepError.NOT_BALANCED_OPERATION, StepError.ONE_SIDED_OPERATION -> setOf(SkillId.BALANCE_PRINCIPLE)
        StepError.DISTRIBUTION_ERROR -> setOf(SkillId.DISTRIBUTIVE_PROPERTY)
        StepError.SIGN_ERROR -> setOf(SkillId.SIGN_HANDLING)
        StepError.SIGN_FLIP_OMISSION -> setOf(SkillId.SIGN_HANDLING, SkillId.BALANCE_PRINCIPLE)
        StepError.ADDITIVE_DIVISOR_ERROR -> setOf(SkillId.ISOLATING_VARIABLE, SkillId.BALANCE_PRINCIPLE)
        StepError.FRACTION_DENOMINATOR_ADDITION -> setOf(SkillId.FRACTION_ARITHMETIC)
        StepError.ORDER_OF_OPERATIONS -> setOf(SkillId.ORDER_OF_OPERATIONS)
        StepError.ARITHMETIC_ERROR -> setOf(SkillId.INTEGER_OPERATIONS)
        StepError.DIVISION_BY_ZERO, StepError.LOST_SOLUTION, StepError.ADDED_SOLUTION -> setOf(SkillId.DOMAIN_RESTRICTIONS)
        StepError.UNSUPPORTED_STEP -> emptySet()
    }
}
