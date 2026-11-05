package mx.matica.math.poly

import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.math.printer.ExprPrinter

/**
 * Resultado de intentar escribir una expresión como polinomio exacto.
 * `Blocked` es una salida legítima: el motor prefiere admitir que no puede verificar algo
 * antes que afirmar una equivalencia que no domina.
 */
sealed interface PolyOutcome {
    data class Value(val polynomial: Polynomial) : PolyOutcome
    data class Blocked(val reason: String) : PolyOutcome
}

object Normalizer {
    private const val MAX_EXPANSION = 8

    fun of(expression: Expr): PolyOutcome = when (expression) {
        is Expr.Const -> PolyOutcome.Value(Polynomial(mapOf(Monomial.CONSTANT to expression.value)))
        is Expr.Var -> PolyOutcome.Value(Polynomial(mapOf(Monomial.of(expression.name) to Rational.ONE)))
        is Expr.Sum -> sumOf(expression)
        is Expr.Neg -> negateOf(expression.operand)
        is Expr.Product -> productOf(expression)
        is Expr.Quotient -> quotientOf(expression.numerator, expression.denominator)
        is Expr.Power -> powerOf(expression)
        is Expr.Percent -> scale(of(expression.operand), Rational.of(1, 100))
        is Expr.Equals -> equationDifference(expression)
    }

    /** L - R para una ecuación; para una expresión sin `=` devuelve su propio polinomio. */
    fun ofEquation(expression: Expr): PolyOutcome = when (expression) {
        is Expr.Equals -> equationDifference(expression)
        else -> of(expression)
    }

    private fun sumOf(sum: Expr.Sum): PolyOutcome {
        var accumulator = Polynomial(emptyMap())
        for (term in sum.terms) {
            val termOutcome = of(term.operand)
            val value = (termOutcome as? PolyOutcome.Value)?.polynomial ?: return termOutcome
            accumulator = if (term.operator == Expr.Sum.Term.Operator.MINUS) {
                accumulator - value
            } else {
                accumulator + value
            }
        }
        return PolyOutcome.Value(accumulator)
    }

    private fun negateOf(operand: Expr): PolyOutcome = when (operand) {
        is Expr.Sum -> sumOf(flattenNegatedSum(operand))
        else -> scale(of(operand), Rational.MINUS_ONE)
    }

    /** `-(a - b)` se reescribe como `-a + b`, que es la forma que un alumno debe poder seguir. */
    private fun flattenNegatedSum(sum: Expr.Sum): Expr.Sum = Expr.Sum(
        sum.terms.map { term ->
            val flipped = if (term.operator == Expr.Sum.Term.Operator.MINUS) {
                Expr.Sum.Term.Operator.PLUS
            } else {
                Expr.Sum.Term.Operator.MINUS
            }
            Expr.Sum.Term(flipped, term.operand)
        },
    )

    private fun productOf(product: Expr.Product): PolyOutcome {
        var accumulator = Polynomial(mapOf(Monomial.CONSTANT to Rational.ONE))
        for (factor in product.factors) {
            val factorOutcome = of(factor.operand)
            val value = (factorOutcome as? PolyOutcome.Value)?.polynomial ?: return factorOutcome
            accumulator *= value
            if (accumulator.degree > MAX_EXPANSION * 2) {
                return PolyOutcome.Blocked("La expansión supera el grado máximo soportado (${MAX_EXPANSION * 2}).")
            }
        }
        return PolyOutcome.Value(accumulator)
    }

    private fun quotientOf(numerator: Expr, denominator: Expr): PolyOutcome {
        val dividend = of(numerator) as? PolyOutcome.Value ?: return PolyOutcome.Blocked(describeBlocked(numerator))
        val divisor = of(denominator) as? PolyOutcome.Value ?: return PolyOutcome.Blocked(describeBlocked(denominator))
        val divisorPolynomial = divisor.polynomial

        if (divisorPolynomial.isConstant) {
            val scalar = divisorPolynomial.constantTerm
            return if (scalar.isZero) {
                PolyOutcome.Blocked("División entre cero: el denominador vale 0.")
            } else {
                PolyOutcome.Value(dividend.polynomial / scalar)
            }
        }

        if (divisorPolynomial.sortedTerms.size == 1) {
            val (monomial, coefficient) = divisorPolynomial.sortedTerms.single()
            if (coefficient.isZero) return PolyOutcome.Blocked("División entre cero.")
            val quotients = mutableMapOf<Monomial, Rational>()
            for ((termMonomial, termCoefficient) in dividend.polynomial.sortedTerms) {
                if (!termMonomial.contains(monomial)) {
                    return PolyOutcome.Blocked(
                        "La expresión es una fracción algebraica: '${monomial.key}' no divide exactamente a todos los términos.",
                    )
                }
                val remaining = subtractExponents(termMonomial, monomial)
                quotients[remaining] = (quotients[remaining] ?: Rational.ZERO) + termCoefficient / coefficient
            }
            return PolyOutcome.Value(Polynomial(quotients))
        }

        return PolyOutcome.Blocked(
            "División entre una suma de términos variables; hace falta factorizar antes de dividir.",
        )
    }

    private fun subtractExponents(from: Monomial, by: Monomial): Monomial {
        val result = from.exponents.toMutableMap()
        by.exponents.forEach { (variable, exponent) -> result[variable] = (result[variable] ?: 0) - exponent }
        return Monomial(result)
    }

    private fun powerOf(power: Expr.Power): PolyOutcome {
        val base = of(power.base) as? PolyOutcome.Value ?: return PolyOutcome.Blocked(describeBlocked(power.base))
        val exponentValue = of(power.exponent)
        val exponentPolynomial = (exponentValue as? PolyOutcome.Value)?.polynomial
            ?: return PolyOutcome.Blocked("El exponente no es una expresión numérica manejable.")
        if (!exponentPolynomial.isConstant) {
            return PolyOutcome.Blocked("Exponente con variable: fuera del alcance del normalizador actual.")
        }
        val exponent = exponentPolynomial.constantTerm.toLongOrNull()
            ?: return PolyOutcome.Blocked("Exponente fraccionario o decimal: se requiere un entero.")
        if (exponent < 0) {
            return PolyOutcome.Blocked("Exponente negativo: produce una fracción algebraica.")
        }
        if (exponent > MAX_EXPANSION) {
            return PolyOutcome.Blocked("Exponente demasiado grande para expandir (máximo $MAX_EXPANSION).")
        }
        return PolyOutcome.Value(base.polynomial.pow(exponent.toInt()))
    }

    private fun equationDifference(equals: Expr.Equals): PolyOutcome {
        val left = of(equals.left) as? PolyOutcome.Value ?: return PolyOutcome.Blocked(describeBlocked(equals.left))
        val right = of(equals.right) as? PolyOutcome.Value ?: return PolyOutcome.Blocked(describeBlocked(equals.right))
        return PolyOutcome.Value(left.polynomial - right.polynomial)
    }

    private fun scale(outcome: PolyOutcome, factor: Rational): PolyOutcome = when (outcome) {
        is PolyOutcome.Value -> PolyOutcome.Value(outcome.polynomial * factor)
        is PolyOutcome.Blocked -> outcome
    }

    private fun describeBlocked(expression: Expr): String =
        "No se puede convertir '${ExprPrinter.render(expression)}' en polinomio exacto."
}
