package mx.matica.learning.diagnosis

import kotlinx.datetime.LocalDate
import mx.matica.math.skills.SkillId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosisInstrumentTest {

    @Test
    fun elInstrumentoNoTieneProblemas() {
        assertEquals(emptyList(), DiagnosisInstrumentValidator.problems(), "Banco de ítems inválido.")
    }

    @Test
    fun cubreTodasLasHabilidadesDelGrafo() {
        val declared = SkillId.entries.toSet()
        assertEquals(declared, DiagnosisInstrument.skillsCovered, "Faltan habilidades por diagnosticar.")
    }

    @Test
    fun cadaHabilidadApareceUnaSolaVez() {
        val repeated = DiagnosisInstrument.items.groupingBy { it.skill }.eachCount().filterValues { it > 1 }
        assertEquals(emptyMap(), repeated)
    }

    @Test
    fun losItemsSeOrdenanEnCuatroBandas() {
        assertEquals(listOf(1, 2, 3, 4), DiagnosisInstrument.items.map { it.band }.distinct().sorted())
        assertTrue(DiagnosisInstrument.items.all { it.band in 1..4 })
    }

    @Test
    fun elMotorEsQuienDaLaClave() {
        val item = requireNotNull(DiagnosisInstrument.byId("diag-distributive"))
        val question = DiagnosisSession().questionFor(item)
        assertNotNull(question)
        assertEquals(0, question.correctIndex, "La clave se calcula, pero el orden del banco es conocido.")
        assertEquals(item.options, question.options)
    }

    @Test
    fun unItemAmbiguoNoSePregunta() {
        val ambiguous = DiagnosisItem(
            id = "ambiguo",
            skill = SkillId.INTEGER_OPERATIONS,
            kind = DiagnosisKind.EXPRESSION,
            band = 1,
            promptEs = "¿Cuál es igual a 2 + 2?",
            reference = "2 + 2",
            options = listOf("4", "2 + 2", "5"),
        )
        assertNull(DiagnosisSession().questionFor(ambiguous), "Dos alternativas equivalentes no son un ítem.")
        assertTrue(DiagnosisInstrumentValidator.problems(listOf(ambiguous)).isNotEmpty())
    }

    @Test
    fun unItemIndemostrableSeDescarta() {
        val unknown = DiagnosisItem(
            id = "indefinible",
            skill = SkillId.EQUIVALENCE,
            kind = DiagnosisKind.EXPRESSION,
            band = 3,
            promptEs = "¿Cuál es igual a sin(x)?",
            reference = "sin(x)",
            options = listOf("cos(x)", "tan(x)"),
        )
        assertNull(DiagnosisSession().questionFor(unknown))
        assertTrue(DiagnosisInstrumentValidator.problems(listOf(unknown)).isNotEmpty())
    }
}

class DiagnosisSessionTest {

    private val today = LocalDate(2026, 11, 5)

    @Test
    fun arrancaConTodosLosItemsPorResponder() {
        val session = DiagnosisSession(seed = 7L)
        assertEquals(DiagnosisInstrument.items.size, session.totalItems)
        assertEquals(session.totalItems, session.pendingQuestions().size)
        assertFalse(session.isFinished)
    }

    @Test
    fun laSemillaFijaElMismoExamen() {
        assertEquals(
            DiagnosisSession(seed = 3L).items.map { it.id },
            DiagnosisSession(seed = 3L).items.map { it.id },
        )
    }

    @Test
    fun responderElMismoItemDosVecesNoCuenta() {
        val session = DiagnosisSession(seed = 1L)
        val item = session.pendingQuestions().first().item
        assertTrue(session.answer(item.id, 0))
        assertFalse(session.answer(item.id, 1))
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun unaAlternativaFueraDeRangoFalla() {
        val session = DiagnosisSession(seed = 1L)
        val item = session.pendingQuestions().first().item
        assertThrowsIllegalArgument { session.answer(item.id, 99) }
    }

    @Test
    fun acertarTodaLaBandaUnoAbreLaBandaDos() {
        val session = DiagnosisSession(seed = 2L)
        session.items.filter { it.band == 1 }.forEach { item ->
            val question = requireNotNull(session.questionFor(item))
            assertTrue(session.answer(item.id, question.correctIndex))
        }
        assertNull(session.outcome(today).stoppedAtBand)
        assertTrue(session.pendingQuestions().any { it.item.band == 2 })
    }

    @Test
    fun dosFallosEnUnaBandaCortanLasBandasSuperiores() {
        val session = DiagnosisSession(seed = 5L)
        session.items.filter { it.band == 1 }.take(2).forEach { item ->
            val question = requireNotNull(session.questionFor(item))
            val wrong = question.options.indices.first { it != question.correctIndex }
            assertTrue(session.answer(item.id, wrong))
        }
        val outcome = session.outcome(today)
        assertEquals(1, outcome.stoppedAtBand)
        assertEquals(3, session.items.count { it.band == 1 })
        assertTrue(outcome.placements.any { !it.asked })
        assertTrue(outcome.placements.filter { !it.asked }.all { it.evidence == null }, "Nivel inventado sin ítem.")
    }

    @Test
    fun elResultadoEntregaEvidenciaYNoNiveles() {
        val session = DiagnosisSession(seed = 9L)
        val item = session.pendingQuestions().first().item
        val question = requireNotNull(session.questionFor(item))
        session.answer(item.id, question.correctIndex)
        val outcome = session.outcome(today)
        val placement = requireNotNull(outcome.placementOf(item.skill))
        assertTrue(placement.correct == true)
        assertEquals(1, outcome.evidences.size)
        assertEquals(today, requireNotNull(placement.evidence).occurredOn)
        assertTrue(placement.reasonEs.contains("equivalente"))
    }

    @Test
    fun laBandaDeDominioUsaLasRestriccionesDelMotor() {
        val item = requireNotNull(DiagnosisInstrument.byId("diag-domain"))
        val question = requireNotNull(DiagnosisSession().questionFor(item))
        assertEquals(0, question.correctIndex, "El 5 es lo que hace indefinida a 7/(x - 5).")
        assertEquals(DiagnosisKind.UNDEFINED_AT, item.kind)
    }

    @Test
    fun sinRespuestasElResumenNoAfirmaNada() {
        val outcome = DiagnosisSession(seed = 4L).outcome(today)
        assertEquals(0, outcome.askedCount)
        assertTrue(outcome.summaryEs.contains("no puede afirmar nada"))
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "Se esperaba IllegalArgumentException, llegó $failure.")
    }
}
