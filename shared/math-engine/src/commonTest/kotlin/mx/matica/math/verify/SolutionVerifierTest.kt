package mx.matica.math.verify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.eq
import mx.matica.math.rational
import mx.matica.math.solve.LinearSolver
import mx.matica.math.solve.SolveOutcome

class SolutionVerifierTest {

    @Test
    fun confirmaUnaSolucionSustituyendolaEnLaEcuacionOriginal() {
        val verificacion = SolutionVerifier.verify("2(x+3)=14", "x", rational(4))
        assertIs<VerificationOutcome.Satisfies>(verificacion)
        assertEquals(rational(14), verificacion.value)
        assertTrue(verificacion.isVerified)
    }

    @Test
    fun rechazaUnaPropuestaQueNoSatisface() {
        val verificacion = SolutionVerifier.verify("2(x+3)=14", "x", rational(5))
        assertIs<VerificationOutcome.DoesNotSatisfy>(verificacion)
        assertEquals(rational(16), verificacion.left)
        assertEquals(rational(14), verificacion.right)
        assertTrue(!verificacion.isVerified)
    }

    @Test
    fun marcaComoExtranaLaQueAnulaElDenominador() {
        val verificacion = SolutionVerifier.verify("x^2/x = 0", "x", rational(0))
        assertIs<VerificationOutcome.ExcludedFromDomain>(verificacion)
        assertTrue(verificacion.reason.contains("denominador"), verificacion.reason)
    }

    @Test
    fun noConfundeNoPoderVerificarConNoSatisfacer() {
        val otraIncognita = SolutionVerifier.verify("x + y = 4", "x", rational(1))
        assertIs<VerificationOutcome.Blocked>(otraIncognita)

        val sinIgual = SolutionVerifier.verify("x + 1", "x", rational(1))
        assertIs<VerificationOutcome.Blocked>(sinIgual)

        val variableInexistente = SolutionVerifier.verify("x + 1 = 2", "z", rational(1))
        assertIs<VerificationOutcome.Blocked>(variableInexistente)

        assertIs<VerificationOutcome.Blocked>(SolutionVerifier.verify("2(x+3", "x", rational(1)))
    }

    @Test
    fun cadaSolucionDelResolutorSatisfaceSuEcuacion() {
        val ecuaciones = listOf("2(x+3)=14", "x/2 + 1 = 5", "3x - 7 = 2x + 1", "x^2=16")
        for (texto in ecuaciones) {
            val solucion = LinearSolver.solve(eq(texto))
            val valores = when (solucion) {
                is SolveOutcome.Unique -> listOf(solucion.value)
                is SolveOutcome.Multiple -> solucion.values
                else -> error("Se esperaba una solución concreta para '$texto', vino $solucion")
            }
            valores.forEach { valor ->
                assertIs<VerificationOutcome.Satisfies>(
                    SolutionVerifier.verify(texto, "x", valor),
                    "'$texto' no se verificó en x=$valor",
                )
            }
        }
    }

    @Test
    fun unaEcuacionSinIgualNoSePuedeVerificar() {
        assertFailsWith<IllegalStateException> { eq("x + 1") }
    }
}
