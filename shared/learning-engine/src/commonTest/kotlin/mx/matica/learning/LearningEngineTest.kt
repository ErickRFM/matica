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
import mx.matica.learning.review.ReviewState
import mx.matica.math.MathEngine
import mx.matica.math.skills.SkillId
import mx.matica.math.skills.SkillId.BALANCE_PRINCIPLE
import mx.matica.math.skills.SkillId.COMBINING_LIKE_TERMS
import mx.matica.math.skills.SkillId.DISTRIBUTIVE_PROPERTY
import mx.matica.math.skills.SkillId.INTEGER_OPERATIONS
import mx.matica.math.skills.SkillId.ISOLATING_VARIABLE
import mx.matica.math.skills.SkillId.ORDER_OF_OPERATIONS

/**
 * El motor de aprendizaje no decide matemática: recibe veredictos de `MathEngine`. Estas pruebas
 * cruzan los dos motores para que la evidencia que sube un nivel sea realmente demostrada.
 */
class LearningEngineTest {

    private val engine = LearningEngine()
    private val math = MathEngine()

    private fun day(text: String) = LocalDate.parse(text)

    private fun evidencia(
        skill: SkillId,
        day: String,
        quality: EvidenceQuality = EvidenceQuality.CORRECT_UNAIDED,
    ) = SkillEvidence(skill, quality, day(day), "prueba")

    @Test
    fun registrarUnaEvidenciaActualizaNivelYRepaso() {
        val recording = engine.record(LearnerProfile.EMPTY, evidencia(INTEGER_OPERATIONS, "2026-11-01"))
        val progress = recording.profile.progressOf(INTEGER_OPERATIONS)
        assertEquals(MasteryLevel.DEVELOPING, progress.level)
        assertEquals(day("2026-11-02"), progress.review.scheduledOn)
        assertEquals(1, progress.window.size)
        assertTrue(recording.mastery.changed)
    }

    @Test
    fun laVentanaNoCreceSinLimite() {
        val evidencias = (1..12).map { evidencia(INTEGER_OPERATIONS, "2026-11-${it.toString().padStart(2, '0')}") }
        val (profile, _) = engine.recordAll(LearnerProfile.EMPTY, evidencias)
        assertEquals(8, profile.progressOf(INTEGER_OPERATIONS).window.size)
        assertEquals(day("2026-11-05"), profile.progressOf(INTEGER_OPERATIONS).window.first().occurredOn)
    }

    @Test
    fun dominarDesbloqueaLaSiguienteHabilidadDelGrafo() {
        var profile = LearnerProfile.EMPTY
        (1..3).forEach { dia ->
            profile = engine.record(profile, evidencia(INTEGER_OPERATIONS, "2026-11-0$dia")).profile
        }
        assertEquals(MasteryLevel.PROFICIENT, profile.levelOf(INTEGER_OPERATIONS))
        assertContains(profile.mastered(), INTEGER_OPERATIONS)

        val plan = engine.plan(profile, day("2026-11-04"), limit = 5)
        assertEquals(
            listOf(ORDER_OF_OPERATIONS, SkillId.FRACTION_ARITHMETIC),
            plan.nextSkills.map { it.skill },
            "Con enteros dominados solo se abren las dos habilidades que piden enteros.",
        )
        assertFalse(DISTRIBUTIVE_PROPERTY in plan.nextSkills.map { it.skill }, "Distribuir exige leer una expresión algebraica antes.")
        assertFalse(plan.nextSkills.any { it.skill == ISOLATING_VARIABLE }, "Despejar exige equilibrio, y aún no existe.")
    }

    @Test
    fun lasHabilidadesSoloConPistaNoSeDeclaranDominadas() {
        var profile = LearnerProfile.EMPTY
        (1..6).forEach { dia ->
            profile = engine.record(
                profile,
                evidencia(INTEGER_OPERATIONS, "2026-11-0$dia", EvidenceQuality.CORRECT_ASSISTED),
            ).profile
        }
        assertEquals(MasteryLevel.DEVELOPING, profile.levelOf(INTEGER_OPERATIONS))
        assertFalse(INTEGER_OPERATIONS in profile.mastered())
    }

    @Test
    fun elPlanPonePrimeroLoQueYaVencio() {
        val perfil = LearnerProfile(
            mapOf(
                INTEGER_OPERATIONS to SkillProgress(
                    skill = INTEGER_OPERATIONS,
                    level = MasteryLevel.PROFICIENT,
                    window = listOf(evidencia(INTEGER_OPERATIONS, "2026-10-01")),
                    review = ReviewState(stage = 1, scheduledOn = day("2026-10-20")),
                ),
                BALANCE_PRINCIPLE to SkillProgress(
                    skill = BALANCE_PRINCIPLE,
                    level = MasteryLevel.DEVELOPING,
                    window = listOf(evidencia(BALANCE_PRINCIPLE, "2026-10-01")),
                    review = ReviewState(stage = 0, scheduledOn = day("2026-11-28")),
                ),
            ),
        )
        val plan = engine.plan(perfil, day("2026-11-30"), limit = 2)
        assertEquals(2, plan.reviewsDue.size)
        assertEquals(INTEGER_OPERATIONS, plan.reviewsDue.first().skill, "Lo más vencido abre el plan.")
        assertEquals(41L, plan.reviewsDue.first().daysOverdue)
        assertTrue("Repasos primero" in plan.reason, plan.reason)

        val enRiesgo = perfil.atRisk(day("2026-11-30"))
        assertEquals(listOf(BALANCE_PRINCIPLE, INTEGER_OPERATIONS), enRiesgo.map { it.skill }, "Ambas vencieron más de su propio intervalo.")
    }

    @Test
    fun elCambioDePerfilEsInmutable() {
        val inicial = LearnerProfile.EMPTY
        engine.record(inicial, evidencia(INTEGER_OPERATIONS, "2026-11-01"))
        assertEquals(emptyMap(), inicial.progress, "Registrar no muta el perfil anterior.")
    }

    @Test
    fun laRutaALaMetaExplicaPorQueAunNoToca() {
        val ruta = engine.pathTo(LearnerProfile.EMPTY, ISOLATING_VARIABLE)
        assertContains(ruta.orderedSkills, BALANCE_PRINCIPLE)
        assertTrue("faltan" in ruta.reason, ruta.reason)
    }

    @Test
    fun funcionaExtremoAExtremoConVeredictosRealesDelMotor() {
        val dia = day("2026-11-01")
        val veredicto = math.checkStep("2(x+3)=14", "2x+6=14")
        val evidencias = EvidenceFromVerdict.of(veredicto, dia)
        assertContains(evidencias.map { it.skill }, DISTRIBUTIVE_PROPERTY)

        var profile = LearnerProfile.EMPTY
        evidencias.forEach { evidencia -> profile = engine.record(profile, evidencia).profile }
        assertEquals(MasteryLevel.DEVELOPING, profile.levelOf(DISTRIBUTIVE_PROPERTY))

        val indeterminado = math.checkStep("(x+1)/x = 2", "x+1 = 2")
        assertEquals(emptyList(), EvidenceFromVerdict.of(indeterminado, day("2026-11-02")))
        assertFalse(COMBINING_LIKE_TERMS in profile.mastered(), "El perfil solo cambia con evidencia demostrada.")
    }
}
