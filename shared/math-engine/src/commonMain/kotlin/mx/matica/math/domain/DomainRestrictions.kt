package mx.matica.math.domain

import mx.matica.math.ast.Expr
import mx.matica.math.ast.variables
import mx.matica.math.numeric.Rational
import mx.matica.math.poly.Normalizer
import mx.matica.math.poly.PolyOutcome
import mx.matica.math.printer.ExprPrinter

/**
 * Valores prohibidos por los denominadores de una expresión. Sin esto, una solución obtenida
 * multiplicando por `x-1` se aceptaría aunque hicera cero el denominador original.
 */
sealed interface Restriction {
    data class NotEqualTo(val variable: String, val excluded: Rational) : Restriction {
        override fun toString(): String = "$variable ≠ $excluded"
    }

    data class Unresolved(val description: String) : Restriction

    companion object {
        fun key(restriction: Restriction): String = when (restriction) {
            is NotEqualTo -> "${restriction.variable}!=${restriction.excluded}"
            is Unresolved -> "unresolved:${restriction.description}"
        }
    }
}

object DomainRestrictions {
    fun of(expression: Expr): Set<Restriction> {
        val restrictions = linkedSetOf<Restriction>()
        collect(expression, restrictions)
        return restrictions
    }

    private fun collect(expression: Expr, into: MutableSet<Restriction>) {
        when (expression) {
            is Expr.Const, is Expr.Var -> Unit
            is Expr.Sum -> expression.terms.forEach { collect(it.operand, into) }
            is Expr.Neg -> collect(expression.operand, into)
            is Expr.Product -> expression.factors.forEach { collect(it.operand, into) }
            is Expr.Quotient -> {
                addFromDenominator(expression.denominator, into)
                collect(expression.numerator, into)
            }

            is Expr.Power -> {
                collect(expression.base, into)
                collect(expression.exponent, into)
                if (expression.exponent is Expr.Neg) {
                    expression.variables().forEach { variable ->
                        into += Restriction.NotEqualTo(variable, Rational.ZERO)
                    }
                }
            }

            is Expr.Percent -> collect(expression.operand, into)
            is Expr.Equals -> {
                collect(expression.left, into)
                collect(expression.right, into)
            }
        }
    }

    private fun addFromDenominator(denominator: Expr, into: MutableSet<Restriction>) {
        val rendered = ExprPrinter.render(denominator)
        if (denominator.variables().isEmpty()) {
            val outcome = Normalizer.of(denominator)
            if (outcome is PolyOutcome.Value && outcome.polynomial.isConstant && outcome.polynomial.constantTerm.isZero) {
                into += Restriction.Unresolved("el denominador '$rendered' es cero: la expresión no está definida")
            }
            return
        }
        when (val outcome = Normalizer.of(denominator)) {
            is PolyOutcome.Blocked -> into += Restriction.Unresolved(
                "los valores que anulan el denominador $rendered",
            )

            is PolyOutcome.Value -> {
                val polynomial = outcome.polynomial
                when {
                    polynomial.isZero -> into += Restriction.Unresolved("el denominador es idénticamente cero")

                    polynomial.isMonomial -> polynomial.variables.forEach { variable ->
                        into += Restriction.NotEqualTo(variable, Rational.ZERO)
                    }

                    polynomial.variables.size == 1 && polynomial.degree == 1 -> {
                        val variable = polynomial.variables.single()
                        val linear = polynomial.linearCoefficientOf(variable)
                        if (linear != null && !linear.isZero) {
                            into += Restriction.NotEqualTo(variable, -polynomial.constantTerm / linear)
                        } else {
                            into += Restriction.Unresolved("los valores que anulan $rendered")
                        }
                    }

                    else -> into += Restriction.Unresolved("los valores que anulan $rendered")
                }
            }
        }
    }

    fun allows(restrictions: Set<Restriction>, variable: String, value: Rational): Boolean =
        restrictions.none { it is Restriction.NotEqualTo && it.variable == variable && it.excluded == value }

    fun keys(restrictions: Set<Restriction>): Set<String> = restrictions.map { Restriction.key(it) }.toSet()
}
