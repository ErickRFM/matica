package mx.matica.math.eval

import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.math.printer.ExprPrinter

sealed interface EvalOutcome {
    data class Value(val value: Rational) : EvalOutcome

    /** La expresión existe pero no está definida: división entre cero, 0 elevado a un negativo. */
    data class Undefined(val reason: String) : EvalOutcome

    data class MissingVariable(val variable: String) : EvalOutcome
    data class Unsupported(val reason: String) : EvalOutcome
}

object Evaluator {
    fun evaluate(expression: Expr, environment: Map<String, Rational> = emptyMap()): EvalOutcome = when (expression) {
        is Expr.Const -> EvalOutcome.Value(expression.value)
        is Expr.Var -> environment[expression.name]?.let { EvalOutcome.Value(it) } ?: EvalOutcome.MissingVariable(expression.name)
        is Expr.Sum -> evaluateSum(expression, environment)
        is Expr.Neg -> when (val operand = evaluate(expression.operand, environment)) {
            is EvalOutcome.Value -> EvalOutcome.Value(-operand.value)
            else -> operand
        }

        is Expr.Product -> evaluateProduct(expression, environment)
        is Expr.Quotient -> evaluateQuotient(expression, environment)
        is Expr.Power -> evaluatePower(expression, environment)
        is Expr.Percent -> when (val operand = evaluate(expression.operand, environment)) {
            is EvalOutcome.Value -> EvalOutcome.Value(operand.value / Rational.of(100, 1))
            else -> operand
        }

        is Expr.Equals -> when (val equality = equalityHolds(expression, environment)) {
            null -> EvalOutcome.Undefined("algún lado de la ecuación no está definido")
            true -> EvalOutcome.Value(Rational.ONE)
            false -> EvalOutcome.Value(Rational.ZERO)
        }
    }

    fun equalityHolds(equals: Expr.Equals, environment: Map<String, Rational>): Boolean? {
        val left = evaluate(equals.left, environment) as? EvalOutcome.Value ?: return null
        val right = evaluate(equals.right, environment) as? EvalOutcome.Value ?: return null
        return left.value == right.value
    }

    /** Sustitución exacta: se usa para comprobar soluciones por sustitución, no para resolver. */
    fun substitute(expression: Expr, variable: String, value: Rational): Expr = when (expression) {
        is Expr.Const -> expression
        is Expr.Var -> if (expression.name == variable) Expr.Const(value) else expression
        is Expr.Sum -> Expr.Sum(expression.terms.map { it.copy(operand = substitute(it.operand, variable, value)) })
        is Expr.Neg -> Expr.Neg(substitute(expression.operand, variable, value))
        is Expr.Product -> Expr.Product(expression.factors.map { it.copy(operand = substitute(it.operand, variable, value)) })
        is Expr.Quotient -> Expr.Quotient(
            substitute(expression.numerator, variable, value),
            substitute(expression.denominator, variable, value),
        )

        is Expr.Power -> Expr.Power(substitute(expression.base, variable, value), substitute(expression.exponent, variable, value))
        is Expr.Percent -> Expr.Percent(substitute(expression.operand, variable, value))
        is Expr.Equals -> Expr.Equals(substitute(expression.left, variable, value), substitute(expression.right, variable, value))
    }

    private fun evaluateSum(sum: Expr.Sum, environment: Map<String, Rational>): EvalOutcome {
        var accumulator = Rational.ZERO
        for (term in sum.terms) {
            when (val evaluated = evaluate(term.operand, environment)) {
                is EvalOutcome.Value -> accumulator = if (term.operator == Expr.Sum.Term.Operator.MINUS) {
                    accumulator - evaluated.value
                } else {
                    accumulator + evaluated.value
                }

                else -> return evaluated
            }
        }
        return EvalOutcome.Value(accumulator)
    }

    private fun evaluateProduct(product: Expr.Product, environment: Map<String, Rational>): EvalOutcome {
        var accumulator = Rational.ONE
        for (factor in product.factors) {
            when (val evaluated = evaluate(factor.operand, environment)) {
                is EvalOutcome.Value -> accumulator *= evaluated.value
                else -> return evaluated
            }
        }
        return EvalOutcome.Value(accumulator)
    }

    private fun evaluateQuotient(quotient: Expr.Quotient, environment: Map<String, Rational>): EvalOutcome {
        val dividend = evaluate(quotient.numerator, environment)
        if (dividend !is EvalOutcome.Value) return propagate(dividend)
        val divisor = evaluate(quotient.denominator, environment)
        if (divisor !is EvalOutcome.Value) return propagate(divisor)
        if (divisor.value.isZero) {
            return EvalOutcome.Undefined("división entre cero en ${ExprPrinter.render(quotient.denominator)}.")
        }
        return EvalOutcome.Value(dividend.value / divisor.value)
    }

    private fun evaluatePower(power: Expr.Power, environment: Map<String, Rational>): EvalOutcome {
        val baseValue = evaluate(power.base, environment)
        if (baseValue !is EvalOutcome.Value) return propagate(baseValue)
        val exponentValue = evaluate(power.exponent, environment)
        if (exponentValue !is EvalOutcome.Value) return propagate(exponentValue)
        val asLong = exponentValue.value.toLongOrNull()
            ?: return EvalOutcome.Unsupported("el exponente ${exponentValue.value} no es entero")
        if (asLong < 0 && baseValue.value.isZero) {
            return EvalOutcome.Undefined("0 elevado a un exponente negativo no está definido.")
        }
        if (asLong < -64 || asLong > 64) {
            return EvalOutcome.Unsupported("exponente fuera del rango soportado (±64).")
        }
        return try {
            EvalOutcome.Value(baseValue.value.pow(asLong.toInt()))
        } catch (error: IllegalArgumentException) {
            EvalOutcome.Undefined(error.message ?: "potencia no definida")
        }
    }

    private fun propagate(outcome: EvalOutcome): EvalOutcome = when (outcome) {
        is EvalOutcome.Undefined, is EvalOutcome.Unsupported, is EvalOutcome.MissingVariable -> outcome
        else -> EvalOutcome.Undefined("la expresión no se pudo evaluar")
    }
}
