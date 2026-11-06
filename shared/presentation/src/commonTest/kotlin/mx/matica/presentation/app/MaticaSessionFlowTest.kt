package mx.matica.presentation.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import mx.matica.math.analysis.StepVerdict
import mx.matica.math.hints.HintLevel
import mx.matica.math.skills.SkillId
import mx.matica.math.verify.VerificationOutcome
import mx.matica.presentation.exercise.ExerciseSlot

/**
 * Recorrido completo del estado, exactamente en el orden en que lo hacen las pantallas:
 * bienvenida → diagnóstico → ruta → lección → paso → respuesta → recompensa.
 *
 * Es la prueba de que el host y las 18 pantallas tienen algo que mostrar. Lo matemático lo sigue
 * decidiendo `MathEngine`: aquí sólo se verifica que la sesión no inventa veredictos ni recompensas.
 */
class MaticaSessionFlowTest {

    private fun sessionOf() = MaticaSession(today = LocalDate(2026, 11, 5))

    @Test
    fun elDiagnostioRespondeConLoQueElMotorPuedeDemostrar() {
        val session = sessionOf()
        val diagnosis = session.startDiagnosis(seed = 1L)
        assertTrue(diagnosis.pendingQuestions().isNotEmpty(), "Sin preguntas el diagnóstico no colocaría a nadie.")

        diagnosis.pendingQuestions().forEach { question ->
            val correct = question.options.indexOfFirst { session.math.parseOrNull(it) != null }
            session.answerDiagnosis(question.item.id, correct.coerceAtLeast(0))
        }
        val outcome = session.finishDiagnosis()
        assertNotNull(outcome, "Un diagnóstico abierto siempre se puede cerrar.")
        assertTrue(session.diagnosed)
        assertEquals(outcome.correctCount * MaticaSession.XP_PER_DIAGNOSIS_HIT, session.xp)
        assertTrue(
            outcome.evidences.isEmpty() || session.profile.mastered().isEmpty(),
            "Una sola evidencia por habilidad no puede dar dominio: MasteryRules lo prohíbe.",
        )
    }

    @Test
    fun elPasoQueAceptaElMotorDejaIntentoYEvidencia() {
        val session = sessionOf()
        val plan = session.lessonsFor(SkillId.BALANCE_PRINCIPLE).first().plan
        // El paso lo escribe el estudiante, no el generador: el reproductor sólo manda sus dos líneas al motor.
        val verdict = session.submitStep(plan, "3x + 2 = 11", "3x = 9")
        assertTrue(verdict is StepVerdict.Valid, verdictMessage(verdict).second)

        assertEquals(1, session.attempts.size)
        assertEquals("3x + 2 = 11", session.attempts.first().before)
        assertTrue(session.xp > 0, "Un paso sin pista es evidencia correcta y por eso da XP.")
        assertTrue(session.masteryChanges.isNotEmpty(), "El perfil tiene que haber cambiado.")
    }

    @Test
    fun unPasoIlegibleNoInventaAprendizaje() {
        val session = sessionOf()
        val plan = session.lessonsFor(SkillId.BALANCE_PRINCIPLE).first().plan
        val before = session.xp
        val verdict = session.submitStep(plan, "3x + 2 = 11", "no es una ecuación")
        assertTrue(verdict !is StepVerdict.Valid, verdictMessage(verdict).second)
        assertEquals(1, session.attempts.size, "El intento se muestra aunque el motor no lo entienda.")
        assertEquals(before, session.xp, "Sin evidencia no hay recompensa.")
    }

    @Test
    fun laRespuestaCorrectaSinPistaValeMasQueConPista() {
        val sinPista = sessionOf()
        val plan = sinPista.lessonsFor(SkillId.BALANCE_PRINCIPLE).first().plan
        val solution = assertNotNull(sinPista.solutionOf(plan))
        assertTrue(sinPista.checkAnswer(plan, solution) is VerificationOutcome.Satisfies)
        val xpSinPista = sinPista.xp

        val conPista = sessionOf()
        val otroPlan = conPista.lessonsFor(SkillId.BALANCE_PRINCIPLE).first().plan
        conPista.markHintUsed(otroPlan)
        conPista.checkAnswer(otroPlan, assertNotNull(conPista.solutionOf(otroPlan)))

        assertTrue(xpSinPista > conPista.xp, "La ayuda debe costar recompensa, no gratis: $xpSinPista vs ${conPista.xp}")
    }

    @Test
    fun unaRespuestaFalsaNoSumaNada() {
        val session = sessionOf()
        val plan = session.lessonsFor(SkillId.BALANCE_PRINCIPLE).first().plan
        val solution = assertNotNull(session.solutionOf(plan))
        val wrong = solution + mx.matica.math.numeric.Rational.ONE
        assertTrue(session.checkAnswer(plan, wrong) is VerificationOutcome.DoesNotSatisfy)
        assertEquals(0, session.xp, "El XP sólo sale de evidencia correcta; un fallo no puede darte nada.")
    }

    @Test
    fun cadaHabilidadConPlantillaTieneTresLeccionesDistintas() {
        val session = sessionOf()
        val withTemplate = SkillId.entries.filter { session.templateIdFor(it) != null }
        assertTrue(withTemplate.isNotEmpty(), "Sin plantillas la ruta no tendría dónde practicarse.")
        withTemplate.forEach { skill ->
            val lessons = session.lessonsFor(skill)
            assertEquals(3, lessons.size, "$skill debe producir tres lecciones legibles.")
            assertEquals(3, lessons.map { it.plan.equationText }.distinct().size, "$skill repite ejercicio entre lecciones.")
            lessons.forEach { assertTrue(it.plan.slots.contains(ExerciseSlot.EQUATION)) }
        }
    }

    @Test
    fun elPlanDeEstudioNuncaQuedaSinExplicacion() {
        val session = sessionOf()
        val plan = session.studyPlan()
        assertTrue(plan.reason.isNotBlank(), "El plan dice por qué se muestra cada bloque.")
    }

    @Test
    fun lasPistasProgresivasCrecenHastaLaLineaCompleta() {
        val session = sessionOf()
        val plan = session.lessonsFor(SkillId.DISTRIBUTIVE_PROPERTY).first().plan
        val basicas = session.hintsOf(plan, HintLevel.NAME_RULE)
        val completas = session.hintsOf(plan, HintLevel.SHOW_LINE)
        assertTrue(completas.size >= basicas.size, "Subir de nivel no puede quitar pistas.")
        assertEquals(completas.last().text, session.revealLine(plan), "La última pista es la línea que se revela.")
    }

    @Test
    fun reiniciarLaSesionDevuelveAlPerfilVacio() {
        val session = sessionOf()
        val plan = session.lessonsFor(SkillId.FRACTION_ARITHMETIC).first().plan
        session.checkAnswer(plan, assertNotNull(session.solutionOf(plan)))
        assertTrue(session.xp > 0)
        session.resetSession()
        assertEquals(0, session.xp)
        assertEquals(0, session.gems)
        assertEquals(0, session.masteryChanges.size)
        assertTrue(!session.diagnosed)
    }

    /**
     * El bucle tiene que ser entrable desde el minuto cero: si la sugerencia de la ruta señala una
     * habilidad sin plantilla, el botón principal queda apagado y la app no se puede usar. Fue
     * exactamente lo que mostró la caminata por el emulador antes de existir `integer-ops`.
     */
    @Test
    fun laRutaSiempreApuntaAAlgoQueSePuedeAbrir() {
        val session = sessionOf()
        val suggested = session.nodes().singleOrNull { it.suggested }
        assertNotNull(suggested, "Un perfil nuevo necesita un primer paso abierto.")
        assertNotNull(session.templateIdFor(suggested.skill), "La sugerencia no puede señalar una habilidad sin ejercicios.")

        val lessons = session.lessonsFor(suggested.skill)
        assertEquals(3, lessons.size, "Abrir la primera habilidad tiene que dar lecciones, no una pantalla vacía.")
        lessons.forEach { lesson ->
            assertTrue(lesson.plan.verified, "${lesson.plan.equationText} llegó sin la sustitución confirmada.")
        }
    }
}
