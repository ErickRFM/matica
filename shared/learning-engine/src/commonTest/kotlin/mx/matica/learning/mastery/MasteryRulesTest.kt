package mx.matica.learning.mastery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.math.skills.SkillId
import mx.matica.math.skills.SkillId.BALANCE_PRINCIPLE
import mx.matica.math.skills.SkillId.FRACTION_ARITHMETIC
import mx.matica.math.skills.SkillId.INTEGER_OPERATIONS

class MasteryRulesTest {

    private fun evidencia(
        day: String,
        quality: EvidenceQuality = EvidenceQuality.CORRECT_UNAIDED,
        skill: SkillId = INTEGER_OPERATIONS,
    ) = SkillEvidence(skill, quality, LocalDate.parse(day), "test")

    @Test
    fun sinEvidenciaNoHayNivel() {
        val cambio = MasteryRules.evaluate(INTEGER_OPERATIONS, MasteryLevel.NOT_INTRODUCED, emptyList())
        assertEquals(MasteryLevel.NOT_INTRODUCED, cambio.to)
        assertFalse(cambio.changed)
        assertTrue("Sin cambio" in cambio.reason, cambio.reason)
    }

    @Test
    fun unSoloFalloNoIntroductorioNoSubeNivel() {
        val cambio = MasteryRules.evaluate(
            INTEGER_OPERATIONS,
            MasteryLevel.NOT_INTRODUCED,
            listOf(evidencia("2026-11-01", EvidenceQuality.INCORRECT)),
        )
        assertEquals(MasteryLevel.NOT_INTRODUCED, cambio.to, "Un intento fallido no demuestra dominio de nada.")
    }

    @Test
    fun elPrimerAciertoAunqueConPistaIniciaLaHabilidad() {
        val cambio = MasteryRules.evaluate(
            INTEGER_OPERATIONS,
            MasteryLevel.NOT_INTRODUCED,
            listOf(evidencia("2026-11-01", EvidenceQuality.CORRECT_ASSISTED)),
        )
        assertEquals(MasteryLevel.DEVELOPING, cambio.to)
        assertTrue("Sube a" in cambio.reason, cambio.reason)
    }

    @Test
    fun tresAciertosSinPistaSinFallosDanCompetencia() {
        val cambio = MasteryRules.evaluate(
            INTEGER_OPERATIONS,
            MasteryLevel.DEVELOPING,
            listOf(evidencia("2026-11-01"), evidencia("2026-11-02"), evidencia("2026-11-03")),
        )
        assertEquals(MasteryLevel.PROFICIENT, cambio.to)
        assertTrue("3 sin pista" in cambio.reason, cambio.reason)
    }

    @Test
    fun unFalloDentroDeLaVentanaBloqueaLaCompetencia() {
        val cambio = MasteryRules.evaluate(
            INTEGER_OPERATIONS,
            MasteryLevel.DEVELOPING,
            listOf(
                evidencia("2026-11-01"),
                evidencia("2026-11-02"),
                evidencia("2026-11-03", EvidenceQuality.INCORRECT),
                evidencia("2026-11-04"),
            ),
        )
        assertEquals(MasteryLevel.DEVELOPING, cambio.to, "Con una incorrecta en la ventana no se declara competente.")
    }

    @Test
    fun laFluidezExigeRecuerdoRepartidoEnElTiempo() {
        val mismoDia = MasteryRules.evaluate(
            FRACTION_ARITHMETIC,
            MasteryLevel.PROFICIENT,
            listOf(evidencia("2026-11-01"), evidencia("2026-11-01"), evidencia("2026-11-01"), evidencia("2026-11-01")),
        )
        assertEquals(MasteryLevel.PROFICIENT, mismoDia.to, "Cuatro cuentas el mismo día no son retención.")

        val repartido = MasteryRules.evaluate(
            FRACTION_ARITHMETIC,
            MasteryLevel.PROFICIENT,
            listOf(
                evidencia("2026-11-01"),
                evidencia("2026-11-04"),
                evidencia("2026-11-09"),
                evidencia("2026-11-09"),
            ),
        )
        assertEquals(MasteryLevel.FLUENT, repartido.to)
        assertTrue("Días con acierto sin pista" in repartido.reason, repartido.reason)
    }

    @Test
    fun dosFallosRecientesBajanUnNivel() {
        val cambio = MasteryRules.evaluate(
            BALANCE_PRINCIPLE,
            MasteryLevel.PROFICIENT,
            listOf(
                evidencia("2026-11-01"),
                evidencia("2026-11-02"),
                evidencia("2026-11-03"),
                evidencia("2026-11-04", EvidenceQuality.INCORRECT),
                evidencia("2026-11-05"),
                evidencia("2026-11-06", EvidenceQuality.INCORRECT),
            ),
        )
        assertEquals(MasteryLevel.DEVELOPING, cambio.to)
        assertTrue("Baja a" in cambio.reason, cambio.reason)
    }

    @Test
    fun elDescensoNuncaBorraLaIntroduccion() {
        val cambio = MasteryRules.evaluate(
            INTEGER_OPERATIONS,
            MasteryLevel.DEVELOPING,
            listOf(evidencia("2026-11-05", EvidenceQuality.INCORRECT), evidencia("2026-11-06", EvidenceQuality.INCORRECT)),
        )
        assertEquals(MasteryLevel.DEVELOPING, cambio.to, "Ya sabemos que la habilidad se presentó; no se desintroduce.")
    }

    @Test
    fun laVentanaSoloMiraLasUltimasOchoEvidencias() {
        val viejas = listOf(
            evidencia("2026-10-01", EvidenceQuality.INCORRECT),
            evidencia("2026-10-02", EvidenceQuality.INCORRECT),
        )
        val recientes = (1..8).map { evidencia("2026-11-0$it") }
        val cambio = MasteryRules.evaluate(INTEGER_OPERATIONS, MasteryLevel.DEVELOPING, viejas + recientes)
        assertEquals(MasteryLevel.PROFICIENT, cambio.to, "Los fallos de octubre quedan fuera: la ventana es de ocho.")
        assertTrue("8 sin pista, 0 con pista, 0 incorrectas" in cambio.reason, cambio.reason)
    }

    @Test
    fun elRazonamientoSiempreCuentaNumerosRevisables() {
        val cambio = MasteryRules.evaluate(
            INTEGER_OPERATIONS,
            MasteryLevel.DEVELOPING,
            listOf(
                evidencia("2026-11-01"),
                evidencia("2026-11-02", EvidenceQuality.CORRECT_ASSISTED),
                evidencia("2026-11-03", EvidenceQuality.INCORRECT),
            ),
        )
        assertTrue("1 sin pista, 1 con pista, 1 incorrectas" in cambio.reason, cambio.reason)
    }
}
