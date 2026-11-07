package mx.matica.learning.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.learning.mastery.EvidenceQuality
import mx.matica.learning.mastery.SkillEvidence
import mx.matica.math.skills.SkillId.INTEGER_OPERATIONS

class ReviewRulesTest {

    private fun day(text: String) = LocalDate.parse(text)

    private fun evidencia(day: String, quality: EvidenceQuality = EvidenceQuality.CORRECT_UNAIDED) =
        SkillEvidence(INTEGER_OPERATIONS, quality, day(day), "test")

    @Test
    fun laEscaloneraEsPublicayRevisable() {
        assertEquals(listOf(1L, 3L, 7L, 14L, 30L), ReviewLadder.intervals)
        assertEquals(4, ReviewLadder.maxStage)
        assertEquals(1L, ReviewLadder.intervalOf(0))
        assertEquals(30L, ReviewLadder.intervalOf(99), "Una etapa fuera de rango no inventa un intervalo infinito.")
    }

    @Test
    fun elPrimerAciertoProgramaElRepasoAlDiaSiguiente() {
        val decision = ReviewRules.advance(ReviewState(), evidencia("2026-11-01"))
        assertEquals(1, decision.state.stage)
        assertEquals(day("2026-11-02"), decision.state.scheduledOn)
        assertTrue("Próximo repaso: 2026-11-02" in decision.reason, decision.reason)
    }

    @Test
    fun subirEtapasSigueLaEscalonera() {
        var state = ReviewState()
        val fechas = listOf("2026-11-01", "2026-11-02", "2026-11-05", "2026-11-12", "2026-11-26")
        fechas.forEach { dia ->
            state = ReviewRules.advance(state, evidencia(dia)).state
        }
        assertEquals(ReviewLadder.maxStage, state.stage)
        assertEquals(day("2026-12-26"), state.scheduledOn)
    }

    @Test
    fun laUltimaEtapaNoCreceIndefinidamente() {
        val maduro = ReviewState(stage = ReviewLadder.maxStage, scheduledOn = day("2026-11-02"))
        val decision = ReviewRules.advance(maduro, evidencia("2026-11-02"))
        assertEquals(ReviewLadder.maxStage, decision.state.stage)
        assertTrue("última etapa" in decision.reason, decision.reason)
    }

    @Test
    fun repetirElMismoDiaNoCuentaComoRetencion() {
        val primero = ReviewRules.advance(ReviewState(), evidencia("2026-11-01"))
        val segundo = ReviewRules.advance(primero.state, evidencia("2026-11-01"))
        assertEquals(primero.state, segundo.state, "El segundo intento del mismo día no mueve ni la fecha.")
        assertTrue("dos veces el mismo día" in segundo.reason, segundo.reason)
    }

    @Test
    fun recordarConPistaMantieneLaEtapa() {
        val base = ReviewState(stage = 2, scheduledOn = day("2026-11-05"))
        val decision = ReviewRules.advance(base, evidencia("2026-11-05", EvidenceQuality.CORRECT_ASSISTED))
        assertEquals(2, decision.state.stage)
        assertEquals(day("2026-11-12"), decision.state.scheduledOn, "Repite el intervalo de la etapa, no lo salta.")
        assertTrue("no avanza" in decision.reason, decision.reason)
    }

    @Test
    fun fallarReiniciaLaEtapaAlDiaSiguiente() {
        val base = ReviewState(stage = 3, scheduledOn = day("2026-11-05"))
        val decision = ReviewRules.advance(base, evidencia("2026-11-05", EvidenceQuality.INCORRECT))
        assertEquals(0, decision.state.stage)
        assertEquals(day("2026-11-06"), decision.state.scheduledOn)
        assertTrue("vuelve a 0" in decision.reason, decision.reason)
    }

    @Test
    fun elVencimientoEsInclusiveEnLaFechaProgramada() {
        val state = ReviewState(stage = 1, scheduledOn = day("2026-11-05"))
        assertTrue(state.isDueOn(day("2026-11-05")))
        assertTrue(state.isDueOn(day("2026-11-07")))
        assertFalse(state.isDueOn(day("2026-11-04")))
        assertEquals(2L, state.daysOverdue(day("2026-11-07")))
        assertEquals(0L, ReviewState().daysOverdue(day("2026-11-07")), "Sin fecha no hay vencimiento que contar.")
    }

    @Test
    fun elRiesgoEsUnaSenalYNoUnaSancion() {
        val stage0 = ReviewState(stage = 0, scheduledOn = day("2026-11-02"))
        assertFalse(ReviewRules.atRisk(stage0, day("2026-11-03")), "Un día de retraso con intervalo de un día aún no es riesgo.")
        assertTrue(ReviewRules.atRisk(stage0, day("2026-11-05")))
        assertFalse(ReviewRules.atRisk(ReviewState(), day("2026-11-05")), "Nunca programado: no puede estar en riesgo.")
    }

    @Test
    fun curvaDelOlvidoDecaeConElTiempoSegunEbbinghaus() {
        val state = ReviewState(stage = 1, completedOn = day("2026-11-01"))

        // El mismo día la retención es máxima (1.0)
        val sameDay = ReviewRules.retentionOf(state, day("2026-11-01"))
        assertEquals(1.0f, sameDay, 0.001f)

        // 3 días después la memoria está en decaimiento suave
        val threeDays = ReviewRules.retentionOf(state, day("2026-11-04"))
        assertTrue(threeDays in 0.45f..0.85f, "La retención tras 3 días debe ser intermedia: $threeDays")

        // 15 días después sin repaso la retención es baja (< 0.6, vulnerable)
        val longTime = ReviewRules.retentionOf(state, day("2026-11-16"))
        assertTrue(longTime < 0.6f, "La retención tras 15 días debe ser vulnerable: $longTime")
    }
}
