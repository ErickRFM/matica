package mx.matica.learning

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.learning.mastery.EvidenceFromVerdict
import mx.matica.learning.mastery.EvidenceQuality
import mx.matica.learning.mastery.MasteryLevel
import mx.matica.learning.mastery.SkillEvidence
import mx.matica.math.analysis.StepError
import mx.matica.math.analysis.StepVerdict
import mx.matica.math.transform.RuleId
import mx.matica.math.skills.SkillId
import mx.matica.math.skills.SkillId.BALANCE_PRINCIPLE
import mx.matica.math.skills.SkillId.COMBINING_LIKE_TERMS
import mx.matica.math.skills.SkillId.DISTRIBUTIVE_PROPERTY
import mx.matica.math.skills.SkillId.INTEGER_OPERATIONS
import mx.matica.math.skills.SkillId.ISOLATING_VARIABLE
import mx.matica.math.skills.SkillId.ORDER_OF_OPERATIONS
import mx.matica.math.skills.SkillId.SIGN_HANDLING

class EvidenceFromVerdictTest {

    private val day = LocalDate.parse("2026-11-01")

    @Test
    fun unPasoValidoEmiteEvidenciaPorCadaHabilidadInvolucrada() {
        val evidencias = EvidenceFromVerdict.of(
            StepVerdict.Valid(RuleId.DISTRIBUTE, setOf(DISTRIBUTIVE_PROPERTY, SkillId.ALGEBRAIC_EXPRESSIONS)),
            day,
        )
        assertEquals(2, evidencias.size)
        assertTrue(evidencias.all { it.quality == EvidenceQuality.CORRECT_UNAIDED })
        assertTrue(evidencias.all { it.origin == RuleId.DISTRIBUTE.id })
        assertContains(evidencias.map { it.skill }, DISTRIBUTIVE_PROPERTY)
    }

    @Test
    fun laPistaUsadaCambiaLaCalidadNoLaHabilidad() {
        val evidencia = EvidenceFromVerdict.of(
            StepVerdict.Valid(RuleId.COMBINE_LIKE_TERMS, setOf(COMBINING_LIKE_TERMS)),
            day,
            usedHint = true,
        ).single()
        assertEquals(EvidenceQuality.CORRECT_ASSISTED, evidencia.quality)
        assertEquals(COMBINING_LIKE_TERMS, evidencia.skill)
    }

    @Test
    fun cadaErrorNombraLaHabilidadQueFallo() {
        val casos = mapOf(
            StepError.NOT_BALANCED_OPERATION to setOf(BALANCE_PRINCIPLE),
            StepError.ONE_SIDED_OPERATION to setOf(BALANCE_PRINCIPLE),
            StepError.DISTRIBUTION_ERROR to setOf(DISTRIBUTIVE_PROPERTY),
            StepError.SIGN_ERROR to setOf(SIGN_HANDLING),
            StepError.ORDER_OF_OPERATIONS to setOf(ORDER_OF_OPERATIONS),
            StepError.ARITHMETIC_ERROR to setOf(INTEGER_OPERATIONS),
            StepError.DIVISION_BY_ZERO to setOf(SkillId.DOMAIN_RESTRICTIONS),
            StepError.LOST_SOLUTION to setOf(SkillId.DOMAIN_RESTRICTIONS),
            StepError.ADDED_SOLUTION to setOf(SkillId.DOMAIN_RESTRICTIONS),
        )
        casos.forEach { (error, esperadas) ->
            val evidencias = EvidenceFromVerdict.of(StepVerdict.Invalid(error, "detalle", "repare"), day)
            assertEquals(esperadas, evidencias.map { it.skill }.toSet(), "Error $error")
            assertTrue(evidencias.all { it.quality == EvidenceQuality.INCORRECT })
            assertTrue(evidencias.all { it.origin == error.id })
        }
    }

    @Test
    fun unPasoQueElMotorNoReconoceNoInventaEvidencia() {
        val unsupported = EvidenceFromVerdict.of(
            StepVerdict.Invalid(StepError.UNSUPPORTED_STEP, "detalle", "repare"),
            day,
        )
        assertEquals(emptyList(), unsupported, "Marcar una habilidad con un paso que no entendimos sería fabricar.")

        val indeterminado = EvidenceFromVerdict.of(StepVerdict.Indeterminate("no demuestra"), day)
        assertEquals(emptyList(), indeterminado)

        val sintaxis = EvidenceFromVerdict.of(StepVerdict.SyntaxProblem(3, "ambiguo", "escriba 2*x"), day)
        assertEquals(emptyList(), sintaxis)
    }
}
