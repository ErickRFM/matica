package mx.matica.math.solve

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.expr
import mx.matica.math.rational

class LinearSolverTest {

    private fun resolver(texto: String): SolveOutcome = LinearSolver.solve(expr(texto))

    @Test
    fun resuelveEcuacionesLineales() {
        val unica = resolver("2(x+3)=14")
        assertIs<SolveOutcome.Unique>(unica)
        assertEquals("x", unica.variable)
        assertEquals(rational(4), unica.value)
    }

    @Test
    fun resuelveConCoeficientesFraccionarios() {
        val unica = resolver("x/2 + 1 = 5")
        assertIs<SolveOutcome.Unique>(unica)
        assertEquals(rational(8), unica.value)
    }

    @Test
    fun detectaIdentidadYEcuacionImposible() {
        assertEquals(SolveOutcome.Identity, resolver("2(x+3)=2x+6"))
        assertEquals(SolveOutcome.NoSolution, resolver("x+1=x+2"))
        assertEquals(SolveOutcome.NoSolution, resolver("0x=5"))
    }

    @Test
    fun descartaSolucionesFueraDelDominio() {
        val rechazada = resolver("x^2/x = 0")
        assertIs<SolveOutcome.RejectedByDomain>(rechazada)
        assertEquals(rational(0), rechazada.value)
        assertTrue(rechazada.reason.contains("denominador"), rechazada.reason)
    }

    @Test
    fun resuelveCuadraticasPurasConRacionalExacta() {
        val raices = resolver("x^2=9")
        assertIs<SolveOutcome.Multiple>(raices)
        assertEquals(listOf(rational(-3), rational(3)), raices.values)

        assertEquals(SolveOutcome.NoSolution, resolver("x^2=-1"))
        assertIs<SolveOutcome.Blocked>(resolver("x^2=2"))
        assertIs<SolveOutcome.Blocked>(resolver("x^2=2x"))
    }

    @Test
    fun admiteSusPropiosLimites() {
        val bloqueado = resolver("x^3=8")
        assertIs<SolveOutcome.Blocked>(bloqueado)
        assertTrue(bloqueado.reason.contains("grado"), bloqueado.reason)

        assertIs<SolveOutcome.Blocked>(LinearSolver.solve(expr("x+1")))
    }

    @Test
    fun verificaPorSustitucionSusPropiasSoluciones() {
        val unica = resolver("2(x+3)=14")
        assertIs<SolveOutcome.Unique>(unica)
        val verificacion = mx.matica.math.verify.SolutionVerifier.verify("2(x+3)=14", unica.variable, unica.value)
        assertIs<mx.matica.math.verify.VerificationOutcome.Satisfies>(verificacion)
        assertEquals(rational(14), verificacion.value)
    }
}
