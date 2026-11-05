package mx.matica.math.hints

import com.ionspin.kotlin.bignum.integer.BigInteger
import mx.matica.math.ast.Expr
import mx.matica.math.ast.containsProductWithSum
import mx.matica.math.ast.variables
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ExprParser
import mx.matica.math.parser.ParseOutcome
import mx.matica.math.poly.Monomial
import mx.matica.math.poly.Normalizer
import mx.matica.math.poly.PolyOutcome
import mx.matica.math.poly.Polynomial
import mx.matica.math.printer.ExprPrinter
import mx.matica.math.transform.RuleId

enum class HintLevel { CONCEPT, LOCATE, NAME_RULE, SHOW_LINE }

data class Hint(val level: HintLevel, val text: String)

/** Plan del siguiente paso: regla, obstáculo detectado y línea resultante. */
data class NextStep(val rule: RuleId, val obstacle: String, val result: Expr.Equals)

/**
 * Escalera de pistas que no entrega la respuesta por defecto. `SHOW_LINE` solo aparece cuando el
 * estudiante lo pide; los niveles previos razonan sobre la estructura de la ecuación.
 */
class HintProvider {

    fun hintsFor(equationText: String, upTo: HintLevel = HintLevel.NAME_RULE): List<Hint> {
        val parsed = ExprParser(equationText).parse()
        val expression = (parsed as? ParseOutcome.Success)?.expression
            ?: return listOf(Hint(HintLevel.CONCEPT, "No puedo leer '$equationText' como expresión; revisa paréntesis y operadores."))
        if (expression !is Expr.Equals) {
            return listOf(Hint(HintLevel.CONCEPT, "Escribe una ecuación con '=' para sugerir el siguiente paso."))
        }

        val next = nextStep(expression)
            ?: return listOf(Hint(HintLevel.CONCEPT, "La incógnita ya está aislada: comprueba el resultado sustituyéndolo en la ecuación original."))

        val variable = expression.variables().sorted().firstOrNull() ?: "la incógnita"
        val ladder = listOf(
            Hint(HintLevel.CONCEPT, "¿Qué operación deja a $variable sola sin romper el equilibrio de la balanza?"),
            Hint(HintLevel.LOCATE, next.obstacle),
            Hint(HintLevel.NAME_RULE, next.rule.labelEs.lowercase() + "."),
            Hint(HintLevel.SHOW_LINE, "La línea quedaría ${ExprPrinter.render(next.result)}."),
        )
        return ladder.filter { it.level.ordinal <= upTo.ordinal }
    }

    fun nextStep(equation: Expr.Equals): NextStep? {
        val left = (Normalizer.of(equation.left) as? PolyOutcome.Value)?.polynomial ?: return null
        val right = (Normalizer.of(equation.right) as? PolyOutcome.Value)?.polynomial ?: return null

        if (equation.left.containsProductWithSum() || equation.right.containsProductWithSum()) {
            return NextStep(
                rule = RuleId.DISTRIBUTE,
                obstacle = "Un factor multiplica a todo un paréntesis en ${ExprPrinter.render(equation)}.",
                result = Expr.Equals(rewrite(equation.left), rewrite(equation.right)),
            )
        }

        val coefficients = left.sortedTerms.map { it.second } + right.sortedTerms.map { it.second }
        val fractional = coefficients.filter { !it.isInteger }
        if (fractional.isNotEmpty()) {
            val factor = Rational.of(Rational.lcmOfDenominators(fractional), BigInteger.ONE)
            return NextStep(
                rule = RuleId.MULTIPLY_BOTH_SIDES_BY,
                obstacle = "Hay denominadores que se pueden eliminar multiplicando los dos lados por ${factor}.",
                result = Expr.Equals(toExpression(left * factor), toExpression(right * factor)),
            )
        }

        val variableSide: Polynomial
        val otherSide: Polynomial
        val variableOnLeft = left.variables.isNotEmpty() && right.variables.isEmpty()
        val variableOnRight = right.variables.isNotEmpty() && left.variables.isEmpty()
        if (!variableOnLeft && !variableOnRight) return null
        variableSide = if (variableOnLeft) left else right
        otherSide = if (variableOnLeft) right else left

        val constant = variableSide.constantTerm
        if (!constant.isZero) {
            val rule = if (constant.signum > 0) RuleId.SUBTRACT_FROM_BOTH_SIDES else RuleId.ADD_TO_BOTH_SIDES
            val adjustment = -constant
            val newVariableSide = variableSide.withConstantShift(adjustment)
            val newOtherSide = otherSide.withConstantShift(adjustment)
            return NextStep(
                rule = rule,
                obstacle = "El término independiente $constant está junto a la incógnita; hay que moverlo al otro lado.",
                result = Expr.Equals(
                    toExpression(if (variableOnLeft) newVariableSide else newOtherSide),
                    toExpression(if (variableOnLeft) newOtherSide else newVariableSide),
                ),
            )
        }

        val variable = variableSide.variables.firstOrNull() ?: return null
        val slope = variableSide.coefficientOf(Monomial.of(variable, 1))
        return if (slope != Rational.ONE && !slope.isZero) {
            NextStep(
                rule = RuleId.DIVIDE_BOTH_SIDES_BY,
                obstacle = "La incógnita aparece multiplicada por $slope.",
                result = Expr.Equals(
                    toExpression(if (variableOnLeft) left / slope else right / slope),
                    toExpression(if (variableOnLeft) right / slope else left / slope),
                ),
            )
        } else {
            null
        }
    }

    private fun Polynomial.withConstantShift(delta: Rational): Polynomial =
        this + Polynomial(mapOf(Monomial.CONSTANT to delta))

    private fun rewrite(expression: Expr): Expr = when (expression) {
        is Expr.Const, is Expr.Var -> expression
        is Expr.Sum -> Expr.Sum(expression.terms.map { it.copy(operand = rewrite(it.operand)) })
        is Expr.Neg -> Expr.Neg(rewrite(expression.operand))
        is Expr.Product -> if (expression.factors.any { it.operand is Expr.Sum }) {
            expandProduct(expression)
        } else {
            Expr.Product(expression.factors.map { it.copy(operand = rewrite(it.operand)) })
        }

        is Expr.Quotient -> Expr.Quotient(rewrite(expression.numerator), rewrite(expression.denominator))
        is Expr.Power -> Expr.Power(rewrite(expression.base), rewrite(expression.exponent))
        is Expr.Percent -> Expr.Percent(rewrite(expression.operand))
        is Expr.Equals -> Expr.Equals(rewrite(expression.left), rewrite(expression.right))
    }

    private fun expandProduct(product: Expr.Product): Expr {
        val sumIndex = product.factors.indexOfFirst { it.operand is Expr.Sum }
        if (sumIndex < 0) return product
        val sum = product.factors[sumIndex].operand as Expr.Sum
        val others = product.factors.filterIndexed { index, _ -> index != sumIndex }
        val coefficient = when {
            others.isEmpty() -> null
            others.size == 1 -> rewrite(others.single().operand)
            else -> Expr.Product(others.map { it.copy(operand = rewrite(it.operand)) })
        }
        val terms = sum.terms.map { term ->
            val operand = if (coefficient == null) {
                rewrite(term.operand)
            } else {
                Expr.Product(
                    listOf(
                        Expr.Product.Factor(explicit = true, operand = coefficient),
                        // `2x` se yuxtapone; `2*3` necesita el signo para no leerse como un solo número.
                        Expr.Product.Factor(explicit = term.operand is Expr.Const, operand = rewrite(term.operand)),
                    ),
                )
            }
            Expr.Sum.Term(term.operator, operand)
        }
        return if (terms.size == 1) terms.single().operand else Expr.Sum(terms)
    }

    private fun toExpression(polynomial: Polynomial): Expr {
        if (polynomial.isZero) return Expr.Const(Rational.ZERO)
        val ordered = polynomial.sortedTerms.sortedWith(
            compareByDescending<Pair<Monomial, Rational>> { it.first.totalDegree }.thenBy { it.first.key },
        )
        val terms = ordered.map { (monomial, coefficient) ->
            val operator = if (coefficient.signum < 0) Expr.Sum.Term.Operator.MINUS else Expr.Sum.Term.Operator.PLUS
            Expr.Sum.Term(operator, termOperand(monomial, if (coefficient.signum < 0) -coefficient else coefficient))
        }
        return if (terms.size == 1 && terms.single().operator == Expr.Sum.Term.Operator.PLUS) {
            terms.single().operand
        } else {
            Expr.Sum(terms)
        }
    }

    private fun termOperand(monomial: Monomial, coefficient: Rational): Expr {
        if (monomial.isConstant) return Expr.Const(coefficient)
        val variablePart: Expr = if (monomial.exponents.size == 1 && monomial.exponents.values.single() == 1) {
            Expr.Var(monomial.exponents.keys.single())
        } else {
            Expr.Product(
                monomial.exponents.entries.map { (variable, exponent) ->
                    Expr.Product.Factor(
                        explicit = true,
                        operand = if (exponent == 1) {
                            Expr.Var(variable)
                        } else {
                            Expr.Power(Expr.Var(variable), Expr.Const(Rational.fromLong(exponent.toLong())))
                        },
                    )
                },
            )
        }
        return if (coefficient == Rational.ONE) {
            variablePart
        } else {
            Expr.Product(
                listOf(
                    Expr.Product.Factor(explicit = true, operand = Expr.Const(coefficient)),
                    Expr.Product.Factor(explicit = false, operand = variablePart),
                ),
            )
        }
    }
}
