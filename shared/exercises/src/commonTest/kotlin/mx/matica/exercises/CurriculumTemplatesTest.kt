package mx.matica.exercises

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.MathEngine
import mx.matica.math.domain.Restriction
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ParseOutcome
import mx.matica.math.skills.SkillId
import mx.matica.math.solve.SolveOutcome
import mx.matica.math.verify.VerificationOutcome

/**
 * Pruebas completas del catálogo de las 15 habilidades del currículo de Mática.
 * Verifica reproducibilidad determinista por semilla, relectura limpia en ExprParser,
 * comprobación contra MathEngine (solve / sustitución / restricciones) y validación de rangos.
 */
class CurriculumTemplatesTest {

    private val engine = MathEngine()
    private val generator = ExerciseGenerator(engine = engine)

    private fun params(vararg pairs: Pair<String, Int>): ParameterSet = ParameterSet(pairs.toMap())

    private fun ready(outcome: GenerationOutcome, seed: Long): ExerciseInstance {
        assertIs<GenerationOutcome.Ready>(outcome, "Semilla $seed falló con $outcome")
        return outcome.instance
    }

    private fun ready(template: ExerciseTemplate, params: ParameterSet): ExerciseInstance {
        val outcome = template.build(params, seed = 0L)
        assertIs<GenerationOutcome.Ready>(outcome, "Parámetros $params fallaron con $outcome")
        return outcome.instance
    }

    private fun rejected(template: ExerciseTemplate, params: ParameterSet): GenerationOutcome.Rejected {
        val outcome = template.build(params, seed = 0L)
        assertIs<GenerationOutcome.Rejected>(outcome, "Se esperaba rechazo para $params pero fue $outcome")
        assertTrue(outcome.reasons.isNotEmpty(), "El rechazo debe tener motivos.")
        return outcome
    }

    @Test
    fun todasLasQuinceHabilidadesTienenPlantillaRegistrada() {
        assertEquals(15, ExerciseTemplates.all.size, "Deben existir exactamente 15 plantillas.")
        val ids = ExerciseTemplates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Los IDs de plantillas deben ser únicos.")

        val skills = ExerciseTemplates.all.map { it.skill }.toSet()
        assertEquals(SkillId.entries.toSet(), skills, "El catálogo debe cubrir el 100% de las 15 habilidades de SkillId.")

        ExerciseTemplates.all.forEach { template ->
            assertTrue(template.labelEs.isNotBlank(), "${template.id} debe tener labelEs.")
            assertTrue(template.ranges.isNotEmpty(), "${template.id} debe declarar parámetros.")
            assertEquals(template, ExerciseTemplates.byId(template.id), "byId debe recuperar la plantilla ${template.id}.")
        }
    }

    @Test
    fun reproducibilidadDeterministaPorSemillaEnTodasLasPlantillas() {
        val otroGenerador = ExerciseGenerator(engine = MathEngine())
        ExerciseTemplates.all.forEach { plantilla ->
            (1L..8L).forEach { seed ->
                val instance1 = ready(generator.generate(plantilla.id, seed), seed)
                val instance2 = ready(otroGenerador.generate(plantilla.id, seed), seed)
                assertEquals(instance1, instance2, "La semilla $seed debe ser determinista para ${plantilla.id}.")
            }
        }
    }

    @Test
    fun relecturaYParseoSinErroresEnTodasLasPlantillas() {
        ExerciseTemplates.all.forEach { plantilla ->
            (1L..8L).forEach { seed ->
                val instance = ready(generator.generate(plantilla.id, seed), seed)
                val parseResult = engine.parse(instance.equationText)
                assertIs<ParseOutcome.Success>(
                    parseResult,
                    "La ecuación '${instance.equationText}' de ${plantilla.id} no se releyó limpiamente.",
                )
            }
        }
    }

    @Test
    fun verificacionConMathEngineEnTodasLasPlantillas() {
        ExerciseTemplates.all.forEach { plantilla ->
            (1L..8L).forEach { seed ->
                val instance = ready(generator.generate(plantilla.id, seed), seed)
                assertEquals(plantilla.skill, generator.skillOf(plantilla.id))

                if (plantilla.skill == SkillId.DOMAIN_RESTRICTIONS) {
                    val restrictions = engine.restrictions(instance.equationText)
                    val notEqual = restrictions.filterIsInstance<Restriction.NotEqualTo>().singleOrNull()
                    assertTrue(notEqual != null, "Se esperaba restricción única en '${instance.equationText}'")
                    assertEquals(instance.variable, notEqual.variable)
                    assertEquals(instance.solution, notEqual.excluded)
                } else {
                    val solved = engine.solve(instance.equationText)
                    assertIs<SolveOutcome.Unique>(
                        solved,
                        "Ecuación '${instance.equationText}' no tuvo solución única: $solved",
                    )
                    assertEquals(instance.solution, solved.value, "Solución de '${instance.equationText}' no coincide.")

                    val verified = engine.verifySolution(instance.equationText, instance.variable, instance.solution)
                    assertIs<VerificationOutcome.Satisfies>(
                        verified,
                        "Sustitución en '${instance.equationText}' no satisfizo: $verified",
                    )
                    assertTrue(
                        engine.restrictions(instance.equationText).isEmpty(),
                        "Ecuación '${instance.equationText}' no debe tener restricciones no deseadas.",
                    )
                }
            }
        }
    }

    // --- Pruebas específicas y validación de rangos de las 10 nuevas plantillas ---

    @Test
    fun orderOfOperationsTemplateComportamientoYValidacion() {
        val template = OrderOfOperationsTemplate()
        val instance = ready(template, params("a" to 5, "b" to 3, "c" to 7, "d" to 2))
        assertEquals("x = 5 + 3(7 - 2)", instance.equationText)
        assertEquals(Rational.fromLong(20), instance.solution)
        assertEquals(instance.solution, (engine.solve(instance.equationText) as SolveOutcome.Unique).value)

        val degenerate = rejected(template, params("a" to 5, "b" to 3, "c" to 4, "d" to 4))
        assertContains(degenerate.reasons.single(), "anula el paréntesis")

        val outOfRange = rejected(template, params("a" to 25, "b" to 3, "c" to 5, "d" to 2))
        assertContains(outOfRange.reasons.single(), "fuera de")

        val missing = rejected(template, params("a" to 5, "b" to 3, "c" to 5))
        assertContains(missing.reasons.single(), "Falta el parámetro 'd'")
    }

    @Test
    fun combiningLikeTermsTemplateComportamientoYValidacion() {
        val template = CombiningLikeTermsTemplate()
        val instance = ready(template, params("a" to 3, "b" to 5, "c" to -4, "k" to 3))
        assertEquals("3x + 5x - 4 = 20", instance.equationText)
        assertEquals(Rational.fromLong(3), instance.solution)
        assertEquals(instance.solution, (engine.solve(instance.equationText) as SolveOutcome.Unique).value)

        val zeroB = rejected(template, params("a" to 3, "b" to 0, "c" to 4, "k" to 2))
        assertContains(zeroB.reasons.single(), "se necesita un segundo término")

        val cancelling = rejected(template, params("a" to 4, "b" to -4, "c" to 2, "k" to 2))
        assertContains(cancelling.reasons.single(), "cancela los términos semejantes")

        val zeroC = rejected(template, params("a" to 3, "b" to 2, "c" to 0, "k" to 2))
        assertContains(zeroC.reasons.single(), "omite la constante")
    }

    @Test
    fun signHandlingTemplateComportamientoYValidacion() {
        val template = SignHandlingTemplate()
        val instance = ready(template, params("a" to 5, "k" to 8))
        assertEquals("-(5 - x) = 3", instance.equationText)
        assertEquals(Rational.fromLong(8), instance.solution)
        assertEquals(instance.solution, (engine.solve(instance.equationText) as SolveOutcome.Unique).value)

        val zeroRight = rejected(template, params("a" to 5, "k" to 5))
        assertContains(zeroRight.reasons.single(), "produce 0 en el lado derecho")

        val outOfRange = rejected(template, params("a" to 1, "k" to 3))
        assertContains(outOfRange.reasons.single(), "fuera de")
    }

    @Test
    fun isolatingVariableTemplateComportamientoYValidacion() {
        val template = IsolatingVariableTemplate()
        val instance = ready(template, params("a" to 5, "b" to 3, "c" to 2, "k" to 4))
        assertEquals("5x + 3 = 2x + 15", instance.equationText)
        assertEquals(Rational.fromLong(4), instance.solution)
        assertEquals(instance.solution, (engine.solve(instance.equationText) as SolveOutcome.Unique).value)

        val zeroA = rejected(template, params("a" to 0, "b" to 3, "c" to 2, "k" to 4))
        assertContains(zeroA.reasons.single(), "anula la incógnita")

        val zeroC = rejected(template, params("a" to 3, "b" to 3, "c" to 0, "k" to 4))
        assertContains(zeroC.reasons.single(), "un solo miembro")

        val equalAC = rejected(template, params("a" to 3, "b" to 3, "c" to 3, "k" to 4))
        assertContains(equalAC.reasons.single(), "cancela la incógnita")
    }

    @Test
    fun decimalFractionConversionTemplateComportamientoYValidacion() {
        val template = DecimalFractionConversionTemplate()
        val instance25 = ready(template, params("p" to 5))
        assertEquals("x = 0.25", instance25.equationText)
        assertEquals(Rational.of(1, 4), instance25.solution)
        assertEquals(instance25.solution, (engine.solve(instance25.equationText) as SolveOutcome.Unique).value)

        val instance50 = ready(template, params("p" to 10))
        assertEquals("x = 0.5", instance50.equationText)
        assertEquals(Rational.of(1, 2), instance50.solution)

        val outOfRange = rejected(template, params("p" to 20))
        assertContains(outOfRange.reasons.single(), "fuera de")
    }

    @Test
    fun percentageReasoningTemplateComportamientoYValidacion() {
        val template = PercentageReasoningTemplate()
        // p = 5 -> rate = 25%. gcd(25, 100) = 25 -> base = 4. m = 5 -> total = 20. 25% of 20 = 5.
        val instance = ready(template, params("p" to 5, "m" to 5))
        assertEquals("x = 25%*20", instance.equationText)
        assertEquals(Rational.fromLong(5), instance.solution)
        assertEquals(instance.solution, (engine.solve(instance.equationText) as SolveOutcome.Unique).value)

        val outOfRange = rejected(template, params("p" to 0, "m" to 5))
        assertContains(outOfRange.reasons.single(), "fuera de")
    }

    @Test
    fun exponentRulesTemplateComportamientoYValidacion() {
        val template = ExponentRulesTemplate()
        val instance = ready(template, params("b" to 2, "m" to 2, "n" to 3))
        assertEquals("x = 2^2*2^3", instance.equationText)
        assertEquals(Rational.fromLong(32), instance.solution)
        assertEquals(instance.solution, (engine.solve(instance.equationText) as SolveOutcome.Unique).value)

        val smallBase = rejected(template, params("b" to 1, "m" to 2, "n" to 3))
        assertContains(smallBase.reasons.single(), "fuera de")

        val zeroExponent = rejected(template, params("b" to 2, "m" to 0, "n" to 3))
        assertContains(zeroExponent.reasons.single(), "fuera de")
    }

    @Test
    fun domainRestrictionsTemplateComportamientoYValidacion() {
        val template = DomainRestrictionsTemplate()
        val instance = ready(template, params("a" to 6, "b" to 3, "c" to 2))
        assertEquals("6/(x - 3) = 2", instance.equationText)
        assertEquals(Rational.fromLong(3), instance.solution)

        val restrictions = engine.restrictions(instance.equationText)
        val restriction = restrictions.filterIsInstance<Restriction.NotEqualTo>().single()
        assertEquals("x", restriction.variable)
        assertEquals(Rational.fromLong(3), restriction.excluded)

        val zeroB = rejected(template, params("a" to 6, "b" to 0, "c" to 2))
        assertContains(zeroB.reasons.single(), "denominador simple")

        val zeroA = rejected(template, params("a" to 0, "b" to 3, "c" to 2))
        assertContains(zeroA.reasons.single(), "fuera de")
    }

    @Test
    fun solutionVerificationTemplateComportamientoYValidacion() {
        val template = SolutionVerificationTemplate()
        val instance = ready(template, params("a" to 2, "b" to 3, "c" to 4, "k" to 4))
        assertEquals("2(x + 3) + 4 = 18", instance.equationText)
        assertEquals(Rational.fromLong(4), instance.solution)
        assertEquals(instance.solution, (engine.solve(instance.equationText) as SolveOutcome.Unique).value)

        val zeroB = rejected(template, params("a" to 2, "b" to 0, "c" to 4, "k" to 4))
        assertContains(zeroB.reasons.single(), "deja un paréntesis vacío")

        val zeroC = rejected(template, params("a" to 2, "b" to 3, "c" to 0, "k" to 4))
        assertContains(zeroC.reasons.single(), "omite el término exterior")
    }

    @Test
    fun equivalenceTemplateComportamientoYValidacion() {
        val template = EquivalenceTemplate()
        val instance = ready(template, params("a" to 2, "b" to 3, "c" to 3, "d" to -1, "k" to 4))
        assertEquals("2(x + 3) + 3(x - 1) = 23", instance.equationText)
        assertEquals(Rational.fromLong(4), instance.solution)
        assertEquals(instance.solution, (engine.solve(instance.equationText) as SolveOutcome.Unique).value)

        val zeroB = rejected(template, params("a" to 2, "b" to 0, "c" to 3, "d" to 2, "k" to 4))
        assertContains(zeroB.reasons.single(), "sumandos no nulos")

        val zeroD = rejected(template, params("a" to 2, "b" to 2, "c" to 3, "d" to 0, "k" to 4))
        assertContains(zeroD.reasons.single(), "sumandos no nulos")
    }
}
