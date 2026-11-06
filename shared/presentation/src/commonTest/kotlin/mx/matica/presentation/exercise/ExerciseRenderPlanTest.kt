package mx.matica.presentation.exercise

import mx.matica.exercises.ExerciseGenerator
import mx.matica.exercises.ExerciseInstance
import mx.matica.exercises.ExerciseTemplates
import mx.matica.exercises.GenerationOutcome
import mx.matica.exercises.ParameterSet
import mx.matica.math.MathEngine
import mx.matica.math.numeric.Rational
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseRenderPlanTest {

    private val generator = ExerciseGenerator()
    private val engine = MathEngine()

    private fun instanceOf(templateId: String, seed: Long): ExerciseInstance {
        val outcome = generator.generate(templateId, seed)
        return requireNotNull((outcome as? GenerationOutcome.Ready)?.instance) {
            "La plantilla $templateId no generó ejercicio con semilla $seed: $outcome"
        }
    }

    private fun planOf(templateId: String, seed: Long): ExerciseRenderPlan {
        val instance = instanceOf(templateId, seed)
        return requireNotNull(renderPlan(instance, engine)) { "Sin plan para ${instance.equationText}" }
    }

    @Test
    fun elCatalogoCubreTodasLasPlantillasReales() {
        assertEquals(
            ExerciseTemplates.all.map { it.id }.sorted(),
            ExerciseRenderCatalog.templateIds.sorted(),
            "El catálogo de render y las plantillas se separaron.",
        )
    }

    @Test
    fun laPlantillaDeEnterosNoInventaUnPlano() {
        val plan = planOf("integer-ops", 4_000L)
        assertTrue(ExerciseSlot.NUMBER_LINE in plan.slots, "La recta sí muestra dónde cae el valor.")
        assertFalse(ExerciseSlot.GRAPH in plan.slots, "Con la incógnita aislada no hay dos rectas que discutan.")
        assertFalse(plan.hasGraph)
        assertTrue(plan.verified, "El plan sólo se dibuja si el motor confirma la sustitución.")
        assertEquals(plan.solution, plan.numberLine.markers.single(), "La marca de la recta es la respuesta del motor.")
    }

    @Test
    fun cadaPlantillaTraeSuPropioAndamiaje() {
        val twoStep = planOf("two-step-linear", 4_000L)
        val fraction = planOf("fraction-linear", 4_000L)
        val distributive = planOf("distributive-linear", 4_000L)

        assertTrue(ExerciseSlot.EQUATION in twoStep.slots)
        assertTrue(ExerciseSlot.NUMBER_LINE in twoStep.slots)
        assertTrue(ExerciseSlot.QUOTIENT_SPLIT in fraction.slots)
        assertFalse(ExerciseSlot.QUOTIENT_SPLIT in twoStep.slots, "Sin cociente no hay reparto que mostrar.")
        assertFalse(ExerciseSlot.QUOTIENT_SPLIT in distributive.slots)
        assertEquals("Ecuación lineal con denominador", fraction.labelEs)
        assertTrue(distributive.strategyEs.contains("Reparte"))
    }

    @Test
    fun unaPlantillaDesconocidaNoInventaUnPlan() {
        val instance = ExerciseInstance(
            templateId = "no-existe",
            variable = "x",
            equationText = "x + 1 = 2",
            solution = Rational.ONE,
            parameters = ParameterSet(emptyMap()),
            seed = 1L,
        )
        assertNull(renderPlan(instance, engine))
    }

    @Test
    fun unTextoIlegibleNoProducePlan() {
        val broken = instanceOf("two-step-linear", 1L).copy(equationText = "x + = ")
        assertNull(renderPlan(broken, engine))
    }

    @Test
    fun laRectaTieneLaSolucionEnElCentro() {
        val plan = planOf("two-step-linear", 12_000L)
        val center = (plan.numberLine.from + plan.numberLine.to) / Rational.of(2, 1)
        assertEquals(plan.solution, center)
        assertTrue(plan.numberLine.markers.contains(plan.solution))
        assertEquals(9, plan.numberLine.ticks.size)
        assertEquals(plan.numberLine.from, plan.numberLine.ticks.first())
    }

    @Test
    fun lasDosRectasSecortanEnLaSolucion() {
        val plan = planOf("distributive-linear", 7_000L)
        assertTrue(plan.hasGraph, "La plantilla distributiva siempre es lineal.")
        val left = plan.lines.first { it.labelEs == "lado izquierdo" }
        val right = plan.lines.first { it.labelEs == "lado derecho" }
        val x = plan.solution
        assertEquals(
            left.slope * x + left.intercept,
            right.slope * x + right.intercept,
            "El cruce de las dos rectas debía ser exactamente la solución.",
        )
    }

    @Test
    fun laVerificacionQuedaEscritaPorElMotor() {
        val plan = planOf("fraction-linear", 3_000L)
        assertTrue(plan.verified, "Un ejercicio generado por el motor debe verificarse por sustitución.")
        assertEquals(3, plan.verification.size)
        assertTrue(plan.verification[0].contains("="), plan.verification[0])
        assertTrue(plan.verification[2].contains("deja ambos lados"), plan.verification[2])
        assertTrue(plan.verification[0].contains(plan.solution.toString()), "La sustitución debe mostrar el valor.")
    }

    @Test
    fun laRespuestaEnteraNoSeDibujaComoFaccion() {
        val plan = planOf("two-step-linear", 21_000L)
        val expected = plan.solution
        assertFalse(plan.answer.isFraction, "Una solución entera se anuncia como entera.")
        // El signo vive fuera del número: «-10» se dibuja como «−» y «10».
        assertEquals(expected.abs.toString(), plan.answer.numeratorText)
        assertEquals(if (expected < Rational.ZERO) "−" else "", plan.answer.signText)
    }

    @Test
    fun elRepartoDelCocienteReconstruyeElNumerador() {
        val template = requireNotNull(ExerciseTemplates.byId("fraction-linear"))
        val seed = 5_000L
        val params = generator.parametersFor(template, seed)
        val built = template.build(params, seed)
        if (built !is GenerationOutcome.Ready) return
        val denominator = Rational.fromLong(requireNotNull(params.of("d")).toLong())
        val b = Rational.fromLong(requireNotNull(params.of("b")).toLong())
        val parts = planOf("fraction-linear", seed).quotientParts
        val rebuilt = parts.fold(Rational.ZERO) { acc, part -> acc + part * denominator }
        // El numerador del ejercicio es x + b: el coeficiente de x vale 1.
        assertEquals(Rational.ONE + b, rebuilt, "Repartir y volver a juntar debe dar el numerador original.")
    }
}
