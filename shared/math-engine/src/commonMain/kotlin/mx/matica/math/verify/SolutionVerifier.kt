package mx.matica.math.verify

import mx.matica.math.ast.Expr
import mx.matica.math.ast.variables
import mx.matica.math.domain.DomainRestrictions
import mx.matica.math.domain.Restriction
import mx.matica.math.eval.EvalOutcome
import mx.matica.math.eval.Evaluator
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ExprParser
import mx.matica.math.parser.ParseOutcome
import mx.matica.math.printer.ExprPrinter

/**
 * Comprobación por sustitución: se reemplaza la incógnita en la ecuación ORIGINAL y se evalúa
 * de forma exacta. `Blocked` es distinto de `DoesNotSatisfy`: en el primero el motor no pudo
 * demostrar nada, y eso no se le cobra al estudiante como un error.
 */
sealed interface VerificationOutcome {
    /** Ambos lados quedaron definidos e iguales. */
    data class Satisfies(val value: Rational) : VerificationOutcome

    /** Ambos lados quedaron definidos y distintos: la propuesta no resuelve la ecuación. */
    data class DoesNotSatisfy(val left: Rational, val right: Rational) : VerificationOutcome

    /** El valor anula algún denominador: es una raíz extraña, no una solución. */
    data class ExcludedFromDomain(val reason: String) : VerificationOutcome

    data class Blocked(val reason: String) : VerificationOutcome

    val isVerified: Boolean get() = this is Satisfies
}

object SolutionVerifier {

    fun verify(equationText: String, variable: String, value: Rational): VerificationOutcome =
        when (val parsed = ExprParser(equationText).parse()) {
            is ParseOutcome.Success -> verify(parsed.expression, variable, value)
            is ParseOutcome.Failure -> VerificationOutcome.Blocked("No se pudo leer la ecuación: ${parsed.reason}.")
        }

    fun verify(equation: Expr, variable: String, value: Rational): VerificationOutcome {
        if (equation !is Expr.Equals) {
            return VerificationOutcome.Blocked("La verificación por sustitución requiere una ecuación con '='.")
        }
        if (variable !in equation.variables()) {
            return VerificationOutcome.Blocked("La ecuación no contiene la incógnita '$variable'.")
        }

        val restrictions = DomainRestrictions.of(equation)
        if (!DomainRestrictions.allows(restrictions, variable, value)) {
            val violated = restrictions.filterIsInstance<Restriction.NotEqualTo>()
                .firstOrNull { it.variable == variable && it.excluded == value }
            return VerificationOutcome.ExcludedFromDomain(
                "$variable = $value hace cero un denominador de ${ExprPrinter.render(equation)} ($violated).",
            )
        }

        val substituted = Evaluator.substitute(equation, variable, value) as Expr.Equals
        val left = Evaluator.evaluate(substituted.left)
        val right = Evaluator.evaluate(substituted.right)

        val undefined = listOf(left to "izquierda", right to "derecha")
            .firstOrNull { (outcome, _) -> outcome is EvalOutcome.Undefined }
        if (undefined != null) {
            val reason = (undefined.first as EvalOutcome.Undefined).reason
            return VerificationOutcome.ExcludedFromDomain(
                "al sustituir $variable = $value, el lado ${undefined.second} queda indefinido: $reason."
            )
        }

        val blocked = listOf(left, right).firstOrNull { it !is EvalOutcome.Value }
        if (blocked != null) {
            return when (blocked) {
                is EvalOutcome.MissingVariable -> VerificationOutcome.Blocked("queda otra incógnita sin valor: ${blocked.variable}.")
                is EvalOutcome.Unsupported -> VerificationOutcome.Blocked(blocked.reason)
                else -> VerificationOutcome.Blocked("la sustitución no se pudo evaluar.")
            }
        }

        val leftValue = (left as EvalOutcome.Value).value
        val rightValue = (right as EvalOutcome.Value).value
        return if (leftValue == rightValue) {
            VerificationOutcome.Satisfies(leftValue)
        } else {
            VerificationOutcome.DoesNotSatisfy(leftValue, rightValue)
        }
    }
}
