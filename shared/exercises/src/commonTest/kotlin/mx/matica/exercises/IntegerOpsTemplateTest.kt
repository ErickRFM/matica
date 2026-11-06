package mx.matica.exercises

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.MathEngine
import mx.matica.math.numeric.Rational
import mx.matica.math.solve.SolveOutcome
import mx.matica.math.verify.VerificationOutcome

/**
 * Plantilla raíz del currículo: enteros con un producto de signos. Los parámetros se escriben a
 * mano, nunca se sortean, para que el texto esperado y la respuesta sean los de un libro.
 */
class IntegerOpsTemplateTest {

    private val engine = MathEngine()
    private val template = IntegerOpsTemplate()

    private fun params(vararg pairs: Pair<String, Int>): ParameterSet = ParameterSet(pairs.toMap())

    private fun ready(vararg pairs: Pair<String, Int>): ExerciseInstance {
        val outcome = template.build(params(*pairs), seed = 0L)
        assertIs<GenerationOutcome.Ready>(outcome, "Se esperaba una instancia lista, llegó $outcome")
        return outcome.instance
    }

    @Test
    fun elProductoNegativoSeLeeComoEnElLibro() {
        val instance = ready("a" to 7, "b" to 3, "c" to 5, "d" to 4)
        assertEquals("x = 7 - 3(-5) + 4", instance.equationText)
        assertEquals(Rational.fromLong(26), instance.solution)
    }

    @Test
    fun elMotorConfirmaCadaTextoConstruido() {
        listOf(
            intArrayOf(7, 3, 5, 4),
            intArrayOf(-7, 2, 9, -15),
            intArrayOf(12, 9, 8, 0),
            intArrayOf(-1, 4, 7, 6),
        ).forEach { (a, b, c, d) ->
            val instance = ready("a" to a, "b" to b, "c" to c, "d" to d)
            val solved = engine.solve(instance.equationText)
            assertIs<SolveOutcome.Unique>(solved, "${instance.equationText} no dejó una solución única: $solved")
            assertEquals(instance.solution, solved.value, "La respuesta con la que se armó es la que lee el motor.")
            assertIs<VerificationOutcome.Satisfies>(
                engine.verifySolution(instance.equationText, "x", instance.solution),
                "Sustituir la respuesta debía satisfacer: ${instance.equationText}",
            )
            assertTrue(engine.restrictions(instance.equationText).isEmpty(), "El ejercicio no nace con trampa de dominio.")
        }
    }

    @Test
    fun elSignoDeAYViveEnElOperadorDelTermino() {
        val instance = ready("a" to -7, "b" to 3, "c" to 5, "d" to 0)
        assertEquals("x = -7 - 3(-5)", instance.equationText, "El signo va en el operador, no pegado al número.")
        assertEquals(Rational.fromLong(8), instance.solution)
    }

    @Test
    fun dCeroDejaDeEscribirse() {
        val instance = ready("a" to 6, "b" to 2, "c" to 3, "d" to 0)
        assertEquals("x = 6 - 2(-3)", instance.equationText, "'+ 0' enseñaría un término que no existe.")
        assertEquals(Rational.fromLong(12), instance.solution)
    }

    @Test
    fun aCeroAbreUnaSumaVaciaYSeRechaza() {
        val outcome = template.build(params("a" to 0, "b" to 3, "c" to 5, "d" to 4), seed = 0L)
        assertIs<GenerationOutcome.Rejected>(outcome, "a = 0 debía rechazarse, llegó $outcome")
        assertTrue(outcome.reasons.isNotEmpty())
    }

    @Test
    fun unFactorUnoSeriaUnCambioDeSignoDisfrazado() {
        val outcome = template.build(params("a" to 7, "b" to 1, "c" to 5, "d" to 4), seed = 0L)
        assertIs<GenerationOutcome.Rejected>(outcome, "b = 1 está fuera del rango declarado, llegó $outcome")
        assertTrue(outcome.reasons.any { "'b'" in it }, "El motivo debe nombrar el parámetro: ${outcome.reasons}")
    }
}
