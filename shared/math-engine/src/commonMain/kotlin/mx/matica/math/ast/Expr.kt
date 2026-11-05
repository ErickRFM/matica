package mx.matica.math.ast

import mx.matica.math.numeric.Rational

/**
 * AST tipado. Preserva la forma en que el estudiante escribió la expresión: la resta y el
 * negativo se guardan como nodos propios en lugar de colapsarse en una suma con coeficientes,
 * porque el análisis de errores necesita ver el signo explícito.
 */
sealed interface Expr {
    data class Const(val value: Rational) : Expr
    data class Var(val name: String) : Expr

    /** `a + b - c`: cada sumando conserva su operador. */
    data class Sum(val terms: List<Term>) : Expr {
        data class Term(val operator: Operator, val operand: Expr) {
            enum class Operator { PLUS, MINUS }
        }

        init {
            require(terms.isNotEmpty()) { "Una suma necesita al menos un término." }
        }
    }

    data class Neg(val operand: Expr) : Expr

    /** `2(x+3)`, `2*x`, `-x`: los factores mantienen el orden y el tipo de unión. */
    data class Product(val factors: List<Factor>) : Expr {
        data class Factor(val explicit: Boolean, val operand: Expr)

        init {
            require(factors.isNotEmpty()) { "Un producto necesita al menos un factor." }
        }
    }

    data class Quotient(val numerator: Expr, val denominator: Expr) : Expr
    data class Power(val base: Expr, val exponent: Expr) : Expr
    data class Percent(val operand: Expr) : Expr
    data class Equals(val left: Expr, val right: Expr) : Expr
}

fun Expr.variables(): Set<String> = when (this) {
    is Expr.Var -> setOf(name)
    is Expr.Const -> emptySet()
    is Expr.Sum -> terms.flatMap { it.operand.variables() }.toSet()
    is Expr.Neg -> operand.variables()
    is Expr.Product -> factors.flatMap { it.operand.variables() }.toSet()
    is Expr.Quotient -> numerator.variables() + denominator.variables()
    is Expr.Power -> base.variables() + exponent.variables()
    is Expr.Percent -> operand.variables()
    is Expr.Equals -> left.variables() + right.variables()
}

fun Expr.isConstantExpression(): Boolean = variables().isEmpty()

fun Expr.constant(value: Rational): Expr = Expr.Const(value)

/** Descendientes directos, en el orden en que el estudiante los escribió. */
private fun Expr.children(): List<Expr> = when (this) {
    is Expr.Const, is Expr.Var -> emptyList()
    is Expr.Sum -> terms.map { it.operand }
    is Expr.Neg -> listOf(operand)
    is Expr.Product -> factors.map { it.operand }
    is Expr.Quotient -> listOf(numerator, denominator)
    is Expr.Power -> listOf(base, exponent)
    is Expr.Percent -> listOf(operand)
    is Expr.Equals -> listOf(left, right)
}

/** Recorrido en preorder: evita duplicar el `when` estructural en cada análisis. */
fun Expr.walk(): List<Expr> {
    val nodes = mutableListOf<Expr>()
    collectNodes(this, nodes)
    return nodes
}

private fun collectNodes(expression: Expr, into: MutableList<Expr>) {
    into += expression
    expression.children().forEach { collectNodes(it, into) }
}

/** `2(x+3)`: hay un factor que multiplica a toda una suma, así que el paso siguiente es distribuir. */
fun Expr.containsProductWithSum(): Boolean = walk().any { node ->
    node is Expr.Product && node.factors.any { factor -> factor.operand is Expr.Sum }
}

/** Lado de una ecuación, o la propia expresión cuando el estudiante simplifica sin igualar. */
fun Expr.sides(): List<Expr> = when (this) {
    is Expr.Equals -> listOf(left, right)
    else -> listOf(this)
}
