package mx.matica.math.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import mx.matica.math.ast.Expr
import mx.matica.math.contract.SYMBOLIC_CONTRACT_VERSION
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ExprParser
import mx.matica.math.parser.ParseOutcome

/**
 * Pruebas del adaptador Ktor contra un servidor simulado: verifican el mapeo contrato -> puerto sin
 * depender de que el servicio Python esté levantado. La comparación real con SymPy vive en
 * `EquivalenciaKotlinSymPyTest`, que sí exige el servicio encendido.
 */
class KtorSymbolicGatewayTest {

    private var lastRequestBody: String = ""

    private fun gateway(status: HttpStatusCode, body: String): KtorSymbolicGateway {
        val engine = MockEngine { request ->
            lastRequestBody = (request.body as? TextContent)?.text ?: request.body.toString()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return KtorSymbolicGateway(symbolicHttpClient(engine, BASE_URL))
    }

    private fun envelope(status: String, result: String, capability: String = "classifyStep", version: String = SYMBOLIC_CONTRACT_VERSION): String =
        """{"contractVersion":"$version","requestId":"r1","capability":"$capability","status":"$status","result":$result,"warnings":[],"engine":{"name":"sympy","version":"1.14.0"}}"""

    private fun equation(text: String): Expr.Equals =
        ((ExprParser(text).parse()) as ParseOutcome.Success).expression as Expr.Equals

    @Test
    fun `un paso equivalente llega como Equivalent con su exclusion de dominio`() = runTest {
        val gateway = gateway(
            HttpStatusCode.OK,
            envelope("ok", """{"supported":true,"equivalent":true,"lostSolutions":[],"extraneousSolutions":[],"domainExcluded":["2"],"method":"conjuntos_de_solucion_sobre_reales"}"""),
        )

        val verdict = assertIs<RemoteStep.Equivalent>(
            gateway.classifyStep(equation("(x^2-4)/(x-2) = x+2"), equation("x^2-4 = (x+2)(x-2)")),
        )

        assertEquals("conjuntos_de_solucion_sobre_reales", verdict.method)
        assertEquals(listOf(Rational.fromLong(2)), verdict.domainExcluded)
    }

    @Test
    fun `el viaje lleva AST tipado, nunca texto del estudiante`() = runTest {
        val gateway = gateway(
            HttpStatusCode.OK,
            envelope("ok", """{"supported":true,"equivalent":true,"lostSolutions":[],"extraneousSolutions":[],"domainExcluded":[],"method":"m"}"""),
        )

        gateway.classifyStep(equation("(x^2-4)/(x-2) = x+2"), equation("x^2-4 = (x+2)(x-2)"))

        assertTrue("\"contractVersion\":\"$SYMBOLIC_CONTRACT_VERSION\"" in lastRequestBody, "falta la versión del contrato: $lastRequestBody")
        assertTrue("\"kind\":\"equals\"" in lastRequestBody, "el AST no viajó tipado: $lastRequestBody")
        assertTrue("\"kind\":\"quotient\"" in lastRequestBody, "falta el nodo de cociente: $lastRequestBody")
        assertTrue("(x^2-4)/(x-2)" !in lastRequestBody, "viajó la cadena escrita por el estudiante: $lastRequestBody")
    }

    @Test
    fun `una linea con soluciones perdidas llega como NotEquivalent`() = runTest {
        val gateway = gateway(
            HttpStatusCode.OK,
            envelope("ok", """{"supported":true,"equivalent":false,"lostSolutions":["0"],"extraneousSolutions":[],"domainExcluded":[],"method":"conjuntos_de_solucion_sobre_reales"}"""),
        )

        val verdict = assertIs<RemoteStep.NotEquivalent>(gateway.classifyStep(equation("x^2 = x"), equation("x = 1")))

        assertEquals(listOf(Rational.ZERO), verdict.lost)
        assertTrue(verdict.extraneous.isEmpty())
    }

    @Test
    fun `un unsupported del servicio no se traduce en error del estudiante`() = runTest {
        val gateway = gateway(
            HttpStatusCode.OK,
            envelope("unsupported", """{"supported":false,"reason":"La comparación de pasos requiere una sola incógnita en las dos líneas."}"""),
        )

        val verdict = assertIs<RemoteStep.NotSupported>(gateway.classifyStep(equation("x = y"), equation("y = x")))

        assertEquals("La comparación de pasos requiere una sola incógnita en las dos líneas.", verdict.reason)
    }

    @Test
    fun `un 422 con sobre del contrato se reporta como fallo de alambrado`() = runTest {
        val gateway = gateway(
            HttpStatusCode.UnprocessableEntity,
            envelope("invalidInput", """{"code":"max_depth","message":"AST más profundo de 32"}"""),
        )

        val verdict = assertIs<RemoteStep.TransportFailure>(gateway.classifyStep(equation("x = 1"), equation("x = 1")))

        assertTrue("max_depth" in verdict.reason, verdict.reason)
    }

    @Test
    fun `una solucion irracional se declara no soportada en lugar de aproximarse`() = runTest {
        val gateway = gateway(
            HttpStatusCode.OK,
            envelope(
                "ok",
                """{"supported":true,"variable":"x","solutions":["sqrt(2)"],"rejected":[],"excludedValues":[],"verification":"sustitucion_en_la_ecuacion_original"}""",
                capability = "solveEquation",
            ),
        )

        assertIs<RemoteSolutions.NotSupported>(gateway.solve(equation("x^2 = 2")))
    }

    @Test
    fun `las soluciones racionales y rechazadas llegan exactas`() = runTest {
        val gateway = gateway(
            HttpStatusCode.OK,
            envelope(
                "ok",
                """{"supported":true,"variable":"x","solutions":["-1/2"],"rejected":[{"value":"3","reason":"viola una restricción de dominio"}],"excludedValues":["3"],"verification":"sustitucion_en_la_ecuacion_original"}""",
                capability = "solveEquation",
            ),
        )

        val solutions = assertIs<RemoteSolutions.Resolved>(gateway.solve(equation("2x + 1 = 0")))

        assertEquals("x", solutions.variable)
        assertEquals(listOf(Rational.of(-1, 2)), solutions.solutions)
        assertEquals(Rational.fromLong(3), solutions.rejected.single().value)
        assertEquals("viola una restricción de dominio", solutions.rejected.single().reason)
        assertEquals(listOf(Rational.fromLong(3)), solutions.excludedValues)
    }

    @Test
    fun `una version distinta del contrato bloquea la comparacion`() = runTest {
        val gateway = gateway(
            HttpStatusCode.OK,
            envelope("ok", """{"supported":true,"equivalent":true,"lostSolutions":[],"extraneousSolutions":[],"domainExcluded":[],"method":"m"}""", version = "2"),
        )

        val verdict = assertIs<RemoteStep.TransportFailure>(gateway.classifyStep(equation("x = 1"), equation("x = 1")))

        assertTrue("contrato" in verdict.reason, verdict.reason)
    }

    @Test
    fun `sin servicio la fachada reporta transporte y nunca inventa un veredicto`() = runTest {
        val gateway = KtorSymbolicGateway(symbolicHttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }, BASE_URL))

        val verdict = assertIs<RemoteStep.TransportFailure>(gateway.classifyStep(equation("x = 1"), equation("x = 1")))

        assertTrue(verdict.reason.isNotEmpty())
    }

    @Test
    fun `un AST que no cabe en el contrato no sale a la red`() = runTest {
        var peticiones = 0
        val engine = MockEngine {
            peticiones += 1
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val gateway = KtorSymbolicGateway(symbolicHttpClient(engine, BASE_URL))
        val tooLong = Expr.Equals(Expr.Var("variable_larga"), Expr.Const(Rational.ONE))

        val verdict = assertIs<RemoteSolutions.NotSupported>(gateway.solve(tooLong))

        assertEquals(0, peticiones, "el codec debió bloquear el viaje antes de tocar la red")
        assertTrue("contrato" in verdict.reason, verdict.reason)
    }

    private companion object {
        const val BASE_URL = "http://localhost:8099"
    }
}
