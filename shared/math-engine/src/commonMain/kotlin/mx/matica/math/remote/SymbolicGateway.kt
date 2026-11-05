package mx.matica.math.remote

import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational

/**
 * Puerto de cálculo ampliado. El motor define la interfaz; la implementación viaja en
 * `:shared:math-remote` con Ktor, de modo que `:shared:math-engine` sigue sin dependencias de red
 * y puede funcionar offline.
 */
sealed interface RemoteStep {
    data class Equivalent(val method: String, val domainExcluded: List<Rational>) : RemoteStep
    data class NotEquivalent(val lost: List<Rational>, val extraneous: List<Rational>, val domainExcluded: List<Rational>, val method: String) : RemoteStep
    data class NotSupported(val reason: String) : RemoteStep
    data class TransportFailure(val reason: String) : RemoteStep
}

sealed interface RemoteSolutions {
    data class Resolved(
        val variable: String,
        val solutions: List<Rational>,
        val rejected: List<Rejected>,
        val excludedValues: List<Rational>,
    ) : RemoteSolutions

    data class Rejected(val value: Rational, val reason: String)

    data class NotSupported(val reason: String) : RemoteSolutions
    data class TransportFailure(val reason: String) : RemoteSolutions
}

interface SymbolicGateway {
    suspend fun classifyStep(before: Expr.Equals, after: Expr.Equals): RemoteStep

    suspend fun solve(equation: Expr.Equals): RemoteSolutions
}
