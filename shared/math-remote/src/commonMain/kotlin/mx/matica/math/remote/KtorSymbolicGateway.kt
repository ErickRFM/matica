package mx.matica.math.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.random.Random
import mx.matica.math.ast.Expr
import mx.matica.math.contract.SYMBOLIC_CONTRACT_VERSION
import mx.matica.math.contract.SymbolicAstCodec
import mx.matica.math.contract.SymbolicCapability
import mx.matica.math.contract.SymbolicEncoding
import mx.matica.math.contract.SymbolicNode
import mx.matica.math.contract.SymbolicRequest
import mx.matica.math.contract.SymbolicResponse
import mx.matica.math.eval.EvalOutcome
import mx.matica.math.eval.Evaluator
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ExprParser
import mx.matica.math.parser.ParseOutcome

/**
 * Adaptador Ktor del puerto [SymbolicGateway]. No decide nada por su cuenta: traduce el AST del
 * motor al contrato, envía y devuelve lo que el servicio afirmó, o un fallo explícito.
 *
 * Los valores del servicio son cadenas exactas; si alguna no admite un racional Kotlin, el
 * veredicto se convierte en `NotSupported` en lugar de descartar el valor en silencio.
 */
class KtorSymbolicGateway(
    private val client: HttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : SymbolicGateway {

    override suspend fun classifyStep(before: Expr.Equals, after: Expr.Equals): RemoteStep {
        val beforeNode = SymbolicAstCodec.encode(before).asNodeOrUnsupported()
            ?: return RemoteStep.NotSupported(unsupportedReason(before))
        val afterNode = SymbolicAstCodec.encode(after).asNodeOrUnsupported()
            ?: return RemoteStep.NotSupported(unsupportedReason(after))

        return when (val outcome = post(SymbolicCapability.CLASSIFY_STEP, beforeNode, afterNode)) {
            is GatewayFailure -> RemoteStep.TransportFailure(outcome.reason)
            is GatewayReply -> outcome.response.toRemoteStep()
        }
    }

    override suspend fun solve(equation: Expr.Equals): RemoteSolutions {
        val node = SymbolicAstCodec.encode(equation).asNodeOrUnsupported()
            ?: return RemoteSolutions.NotSupported(unsupportedReason(equation))

        return when (val outcome = post(SymbolicCapability.SOLVE_EQUATION, node, null)) {
            is GatewayFailure -> RemoteSolutions.TransportFailure(outcome.reason)
            is GatewayReply -> outcome.response.toRemoteSolutions()
        }
    }

    private suspend fun post(
        capability: SymbolicCapability,
        expression: SymbolicNode,
        after: SymbolicNode?,
    ): GatewayOutcome {
        val request = SymbolicRequest(
            contractVersion = SYMBOLIC_CONTRACT_VERSION,
            requestId = "kotlin-${Random.nextLong().toULong().toString(36)}",
            capability = capability,
            expression = expression,
            after = after,
        )
        return try {
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            // El sobre se lee incluso en 4xx/5xx: 422 y 504 traen el código del rechazo, que dice
            // más que un "HTTP 422". Solo cuando no hay sobre válido se reporta fallo de transporte.
            val envelope = runCatching { response.body<SymbolicResponse>() }
            when {
                envelope.isSuccess -> GatewayReply(envelope.getOrThrow())
                response.status.isSuccess() -> GatewayFailure("El servicio respondió sin un cuerpo válido del contrato.")
                else -> GatewayFailure("HTTP ${response.status.value}: el servicio rechazó la petición sin sobre del contrato.")
            }
        } catch (failure: Throwable) {
            GatewayFailure(failure.message ?: failure::class.simpleName ?: "fallo de transporte")
        }
    }

    private fun SymbolicResponse.toRemoteStep(): RemoteStep {
        val mismatch = contractMismatchReason()
        if (mismatch != null) return RemoteStep.TransportFailure(mismatch)
        if (status != "ok") return when (status) {
            "unsupported" -> RemoteStep.NotSupported(result?.reason ?: "El servicio no clasificó esta línea.")
            "invalidInput" -> RemoteStep.TransportFailure(contractRejection())
            else -> RemoteStep.TransportFailure(serviceFailure())
        }

        val body = result ?: return RemoteStep.TransportFailure(serviceFailure())
        val equivalent = body.equivalent ?: return RemoteStep.TransportFailure("'classifyStep' respondió sin 'equivalent'.")
        val lostValues = body.lostSolutions.orEmpty().toRationalsOrNull() ?: return RemoteStep.NotSupported(NON_EXACT_REASON)
        val extraneousValues = body.extraneousSolutions.orEmpty().toRationalsOrNull() ?: return RemoteStep.NotSupported(NON_EXACT_REASON)
        val excludedValues = body.domainExcluded.orEmpty().toRationalsOrNull() ?: return RemoteStep.NotSupported(NON_EXACT_REASON)

        return if (equivalent) {
            RemoteStep.Equivalent(body.method ?: "metodo_no_reportado", excludedValues)
        } else {
            RemoteStep.NotEquivalent(
                lost = lostValues,
                extraneous = extraneousValues,
                domainExcluded = excludedValues,
                method = body.method ?: "metodo_no_reportado",
            )
        }
    }

    private fun SymbolicResponse.toRemoteSolutions(): RemoteSolutions {
        val mismatch = contractMismatchReason()
        if (mismatch != null) return RemoteSolutions.TransportFailure(mismatch)
        if (status != "ok") return when (status) {
            "unsupported" -> RemoteSolutions.NotSupported(result?.reason ?: "El servicio no resolvió esta ecuación.")
            "invalidInput" -> RemoteSolutions.TransportFailure(contractRejection())
            else -> RemoteSolutions.TransportFailure(serviceFailure())
        }

        val body = result ?: return RemoteSolutions.TransportFailure(serviceFailure())
        if (body.supported != true) return RemoteSolutions.NotSupported(body.reason ?: "El servicio marcó la ecuación como no resoluble.")
        val variable = body.variable ?: return RemoteSolutions.TransportFailure("'solveEquation' respondió sin 'variable'.")

        val solutionValues = body.solutions.orEmpty().toRationalsOrNull()
            ?: return RemoteSolutions.NotSupported(NON_EXACT_REASON)
        val excludedValues = body.excludedValues.orEmpty().toRationalsOrNull()
            ?: return RemoteSolutions.NotSupported(NON_EXACT_REASON)
        val rejectedValues = body.rejected.orEmpty().map { entry ->
            val value = exactRational(entry.value) ?: return RemoteSolutions.NotSupported(NON_EXACT_REASON)
            RemoteSolutions.Rejected(value, entry.reason)
        }

        return RemoteSolutions.Resolved(
            variable = variable,
            solutions = solutionValues,
            rejected = rejectedValues,
            excludedValues = excludedValues,
        )
    }

    private fun SymbolicResponse.contractMismatchReason(): String? =
        if (contractVersion != SYMBOLIC_CONTRACT_VERSION) {
            "El servicio responde al contrato '$contractVersion' y el motor habla del '$SYMBOLIC_CONTRACT_VERSION'."
        } else {
            null
        }

    private fun SymbolicResponse.contractRejection(): String =
        "El AST enviado violó el contrato: ${result?.code ?: "sin codigo"} ${result?.message ?: ""}".trim()

    private fun SymbolicResponse.serviceFailure(): String =
        result?.reason ?: "Respuesta inesperada del servicio (status='$status')."

    private fun unsupportedReason(expression: Expr): String = when (val encoding = SymbolicAstCodec.encode(expression)) {
        is SymbolicEncoding.Unsupported -> encoding.reason
        is SymbolicEncoding.Ready -> "La expresión '${expression::class.simpleName}' sí cabe en el contrato."
    }

    companion object {
        const val DEFAULT_ENDPOINT: String = "/v1/symbolic"

        private const val NON_EXACT_REASON =
            "El servicio devolvió un valor que no es un número exacto y el motor Kotlin no lo puede comparar sin perder precisión."
    }
}

private sealed interface GatewayOutcome

private data class GatewayReply(val response: SymbolicResponse) : GatewayOutcome

private data class GatewayFailure(val reason: String) : GatewayOutcome

private fun SymbolicEncoding.asNodeOrUnsupported(): SymbolicNode? = (this as? SymbolicEncoding.Ready)?.node

/** Cadena del contrato a racional exacto, usando el parser del propio motor: no se añade un segundo lector de números. */
private fun exactRational(text: String): Rational? {
    val parsed = ExprParser(text).parse() as? ParseOutcome.Success ?: return null
    return (Evaluator.evaluate(parsed.expression) as? EvalOutcome.Value)?.value
}

private fun List<String>.toRationalsOrNull(): List<Rational>? = map { exactRational(it) ?: return null }
