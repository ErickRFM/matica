package mx.matica.exercises

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import mx.matica.math.MathEngine
import mx.matica.math.numeric.Rational
import mx.matica.math.solve.SolveOutcome

/**
 * Pruebas de las plantillas. Se trabaja con parámetros explícitos —no con sorteos— para poder escribir
 * a mano el texto esperado y la respuesta, y cada texto se relee con el motor antes de aceptarlo.
 */
class LinearTemplatesTest {

    private val engine = MathEngine()
    private val twoStep = TwoStepLinearTemplate()
    private val distributive = DistributiveLinearTemplate()
    private val fraction = FractionLinearTemplate()
    private val perimetro = PerimeterRectangleTemplate()

    private fun params(vararg pairs: Pair<String, Int>): ParameterSet = ParameterSet(pairs.toMap())

    private fun ready(template: ExerciseTemplate, params: ParameterSet): ExerciseInstance {
        val outcome = template.build(params, seed = 0L)
        assertIs<GenerationOutcome.Ready>(outcome, "Se esperaba una instancia lista, llegó $outcome")
        return outcome.instance
    }

    private fun rechazado(template: ExerciseTemplate, params: ParameterSet): GenerationOutcome.Rejected {
        val outcome = template.build(params, seed = 0L)
        assertIs<GenerationOutcome.Rejected>(outcome, "Se esperaba un rechazo, llegó $outcome")
        assertTrue(outcome.reasons.isNotEmpty(), "Un rechazo sin motivo no explica nada.")
        return outcome
    }

    private fun resuelta(instance: ExerciseInstance): Rational {
        val solved = engine.solve(instance.equationText)
        assertIs<SolveOutcome.Unique>(solved, "${instance.equationText} no dejó una solución única: $solved")
        assertEquals(instance.variable, solved.variable)
        return solved.value
    }

    @Test
    fun elSignoViveEnElOperadorDelTermino() {
        val instance = ready(twoStep, params("a" to 3, "b" to -5, "k" to 4))
        assertEquals("3x - 5 = 7", instance.equationText)
        assertEquals(Rational.fromLong(4), instance.solution)
        assertEquals(Rational.fromLong(4), resuelta(instance), "La respuesta construida es la que lee el motor.")
    }

    @Test
    fun elCoeficienteUnoNoSeEscribe() {
        val instance = ready(twoStep, params("a" to 1, "b" to 5, "k" to 4))
        assertEquals("x + 5 = 9", instance.equationText, "'1x' enseñaría una regla que no existe.")
        assertEquals(Rational.fromLong(4), resuelta(instance))
    }

    @Test
    fun elTerminoConstanteNuloSeOmitira() {
        val instance = ready(twoStep, params("a" to 2, "b" to 0, "k" to 3))
        assertEquals("2x = 6", instance.equationText)
        assertEquals(Rational.fromLong(3), resuelta(instance))
    }

    @Test
    fun unCoeficienteNuloSeRechazaConMotivo() {
        val outcome = rechazado(twoStep, params("a" to 0, "b" to 4, "k" to 2))
        assertContains(outcome.reasons.single(), "anula la incógnita")
    }

    @Test
    fun losParametrosAusentesOFueraDeRangoSeNombran() {
        val ausente = rechazado(twoStep, params("a" to 2, "b" to 3))
        assertContains(ausente.reasons.single(), "Falta el parámetro 'k'")

        val fuera = rechazado(twoStep, params("a" to 2, "b" to 25, "k" to 3))
        assertEquals(listOf("'b' = 25 está fuera de b ∈ [-20, 20]."), fuera.reasons)
    }

    @Test
    fun elParentesisQuedaListoParaDistribuir() {
        val instance = ready(distributive, params("a" to 4, "b" to -7, "k" to 2))
        assertEquals("4(x - 7) = -20", instance.equationText)
        assertEquals(Rational.fromLong(2), resuelta(instance))
    }

    @Test
    fun sinSumaInteriorNoHayNadaQueDistribuir() {
        val outcome = rechazado(distributive, params("a" to 4, "b" to 0, "k" to 2))
        assertContains(outcome.reasons.single(), "no hay nada que distribuir")
    }

    @Test
    fun elDenominadorConstanteNoProhibeNingunValor() {
        val instance = ready(fraction, params("b" to 5, "d" to 3, "a" to 2))
        assertEquals("(x + 5)/3 = 2", instance.equationText)
        assertEquals(Rational.fromLong(1), resuelta(instance))
        assertTrue(engine.restrictions(instance.equationText).isEmpty(), "El ejercicio no nace con trampa de dominio.")
    }

    @Test
    fun elPerimetroDelRectanguloSeEscribeComoLoEscribiriaUnLibro() {
        val instance = ready(perimetro, params("b" to 3, "k" to 4))
        assertEquals("2(x + 3) = 14", instance.equationText)
        assertEquals(Rational.fromLong(4), instance.solution)
        assertEquals(Rational.fromLong(4), resuelta(instance), "El lado que falta es el que resuelve el motor.")
        assertTrue(engine.restrictions(instance.equationText).isEmpty(), "Un perímetro no nace con dominio proibido.")
    }

    @Test
    fun unLadoQueNoExisteSeRechazaConMotivo() {
        assertIs<GenerationOutcome.Rejected>(perimetro.build(params("b" to 0, "k" to 4), seed = 0L))
        val sinLado = rechazado(perimetro, params("b" to 3, "k" to 0))
        assertTrue(sinLado.reasons.any { it.contains("'k'") }, "El motivo tiene que señalar el lado: ${sinLado.reasons}")
    }

    @Test
    fun cadaPlantillaApuntaAUnaHabilidadDelGrafo() {
        val ids = ExerciseTemplates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Dos plantillas con el mismo id no se pueden rastrear.")

        val skills = ExerciseTemplates.all.map { it.skill }
        assertEquals(skills.size, skills.toSet().size, "Si dos plantillas entrenan la misma habilidad, el plan no distingue.")
        assertEquals(
            listOf(
                "combining-like-terms",
                "decimal-fraction-conversion",
                "distributive-linear",
                "domain-restrictions",
                "equivalence",
                "exponent-rules",
                "fraction-linear",
                "integer-ops",
                "isolating-variable",
                "order-of-operations",
                "percentage-reasoning",
                "rectangle-perimeter",
                "sign-handling",
                "solution-verification",
                "two-step-linear",
            ).sorted(),
            ids.sorted(),
        )

        assertEquals(ExerciseTemplates.byId("two-step-linear"), ExerciseTemplates.all.first { it.id == "two-step-linear" })
        assertNull(ExerciseTemplates.byId("no-existe"))
    }

    @Test
    fun laEtiquetaEspañolaNuncaQuedaVacia() {
        ExerciseTemplates.all.forEach { template ->
            assertTrue(template.labelEs.isNotBlank(), "${template.id} sin etiqueta: la UI no puede nombrarlo.")
            assertTrue(template.ranges.isNotEmpty(), "${template.id} sin parámetros declarados.")
        }
    }
}
