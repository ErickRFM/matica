package mx.matica.exercises

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.MathEngine
import mx.matica.math.ast.Expr
import mx.matica.math.skills.SkillId
import mx.matica.math.solve.SolveOutcome
import mx.matica.math.verify.VerificationOutcome

/**
 * Pruebas del generador. Lo que se vigila aquí no es la aritmética —esa ya la demuestra el motor— sino
 * dos promesas: que un ejercicio se pueda reproducir con su semilla y que nada salga del generador sin
 * que `MathEngine` lo haya verificado.
 */
class ExerciseGeneratorTest {

    private val engine = MathEngine()
    private val generator = ExerciseGenerator(engine = engine)

    private fun ready(outcome: GenerationOutcome, seed: Long): ExerciseInstance {
        assertIs<GenerationOutcome.Ready>(outcome, "La semilla $seed llegó como $outcome")
        return outcome.instance
    }

    private fun template(id: String): ExerciseTemplate = requireNotNull(ExerciseTemplates.byId(id))

    @Test
    fun laMismaSemillaReproduceElMismoEjercicio() {
        val otroGenerador = ExerciseGenerator(engine = MathEngine())
        ExerciseTemplates.all.forEach { plantilla ->
            (1L..6L).forEach { seed ->
                assertEquals(
                    ready(generator.generate(plantilla.id, seed), seed),
                    ready(otroGenerador.generate(plantilla.id, seed), seed),
                    "El backend tiene que poder rehacer el intento a partir de su semilla.",
                )
            }
        }
    }

    @Test
    fun nadaSaleDelGeneradorSinVerificacionDelMotor() {
        ExerciseTemplates.all.forEach { plantilla ->
            (1L..12L).forEach { seed ->
                val instance = ready(generator.generate(plantilla.id, seed), seed)
                assertEquals(plantilla.skill, generator.skillOf(plantilla.id))
                assertEquals(
                    instance.solution,
                    (engine.solve(instance.equationText) as SolveOutcome.Unique).value,
                    "${instance.equationText} no resuelve en la respuesta guardada.",
                )
                assertIs<VerificationOutcome.Satisfies>(
                    engine.verifySolution(instance.equationText, instance.variable, instance.solution),
                    "Sustituir la respuesta guardada debe satisfacer: ${instance.equationText}",
                )
                assertTrue(engine.restrictions(instance.equationText).isEmpty(), "Ningún ejercicio nace proibido.")
            }
        }
    }

    @Test
    fun losSorteosRespetanElRango() {
        ExerciseTemplates.all.forEach { plantilla ->
            (1L..40L).forEach { seed ->
                val params = generator.parametersFor(plantilla, seed)
                plantilla.ranges.forEach { range ->
                    val value = requireNotNull(params.of(range.name)) { "'${range.name}' no salió sorteado con la semilla $seed" }
                    assertTrue(range.contains(value), "${range.name} = $value fuera de ${range.labelEs()}.")
                }
            }
        }
    }

    @Test
    fun dosParametrosDelMismoRangoNoSalenIgualesSiempre() {
        val gemelos = TwinRangeTemplate()
        val distintas = (0L..20L).count { seed ->
            val params = generator.parametersFor(gemelos, seed)
            params.of("a") != params.of("b")
        }
        assertTrue(distintas >= 5, "Con un `Random` por parámetro los valores gemelos salen idénticos: distintos=$distintas.")
    }

    @Test
    fun unaRespuestaGuardadaFalsaSeBloqueaEnLugarDeEmitirse() {
        val outcome = ExerciseGenerator(templates = listOf(LiarTemplate()), engine = engine).generate("mentirosa", 1L)
        assertIs<GenerationOutcome.Rejected>(outcome)
        assertContains(outcome.reasons.single(), "El resolutor obtuvo")
    }

    @Test
    fun losReintentosInvalidosSeAgotanComoRechazo() {
        val outcome = ExerciseGenerator(templates = listOf(AlwaysZeroTemplate()), engine = engine)
            .generate("siempre-cero", 5L, maxAttempts = 3)
        assertIs<GenerationOutcome.Rejected>(outcome)
        assertEquals(listOf("el parámetro solo vale 0."), outcome.reasons, "El último motivo es el que hay que mostrar.")
    }

    @Test
    fun unaPlantillaDesconocidaSeRechazaConMotivo() {
        val outcome = generator.generate("no-existe", 1L)
        assertIs<GenerationOutcome.Rejected>(outcome)
        assertContains(outcome.reasons.single(), "no está registrada")
    }

    @Test
    fun laSerieDaTextosDistintosSinFallosYEsReproducible() {
        val batch = generator.generateBatch("two-step-linear", seed = 1L, count = 5)
        assertEquals(emptyList(), batch.failures, "Sorteos gemelos o inválidos: ${batch.failures}")
        assertEquals(5, batch.instances.size)
        assertEquals(5, batch.instances.map { it.equationText }.toSet().size)
        assertEquals(batch.instances, generator.generateBatch("two-step-linear", seed = 1L, count = 5).instances)

        batch.instances.forEach { instance ->
            assertEquals("two-step-linear", instance.templateId)
            template(instance.templateId).ranges.forEach { range ->
                assertTrue(range.contains(requireNotNull(instance.parameters.of(range.name))))
            }
        }
    }

    @Test
    fun todaPlantillaConstruyeDesdeUnaSolucionEntera() {
        ExerciseTemplates.all.forEach { plantilla ->
            (1L..6L).forEach { seed ->
                val instance = ready(generator.generate(plantilla.id, seed), seed)
                assertTrue(instance.solution.isInteger, "${plantilla.id} guardó una fracción: ${instance.solution}.")
            }
        }
    }

    private class TwinRangeTemplate : ExerciseTemplate {
        override val id = "gemelos"
        override val labelEs = "Dos parámetros con el mismo rango"
        override val skill = SkillId.INTEGER_OPERATIONS
        override val variable = "x"
        override val ranges = listOf(ParameterRange("a", 1, 6), ParameterRange("b", 1, 6))

        override fun build(params: ParameterSet, seed: Long): GenerationOutcome =
            GenerationOutcome.Rejected(listOf("solo se usan sus rangos"))
    }

    private class AlwaysZeroTemplate : ExerciseTemplate {
        override val id = "siempre-cero"
        override val labelEs = "Plantilla que nunca produce un ejercicio"
        override val skill = SkillId.INTEGER_OPERATIONS
        override val variable = "x"
        override val ranges = listOf(ParameterRange("a", 0, 0))

        override fun build(params: ParameterSet, seed: Long): GenerationOutcome =
            GenerationOutcome.Rejected(listOf("el parámetro solo vale 0."))
    }

    /** Escribe una ecuación correcta y guarda la respuesta equivocada: el generador no debe tragárselo. */
    private class LiarTemplate : ExerciseTemplate {
        override val id = "mentirosa"
        override val labelEs = "Plantilla que miente sobre su respuesta"
        override val skill = SkillId.BALANCE_PRINCIPLE
        override val variable = "x"
        override val ranges = listOf(ParameterRange("k", 1, 1))

        override fun build(params: ParameterSet, seed: Long): GenerationOutcome = ready(
            Expr.Equals(left = ExerciseAst.linearSum(1, 2, variable), right = ExerciseAst.const(5)),
            solution = 99L,
            params = params,
            seed = seed,
        )
    }
}
