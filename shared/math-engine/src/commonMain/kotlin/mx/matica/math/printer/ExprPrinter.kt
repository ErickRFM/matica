package mx.matica.math.printer

import mx.matica.math.ast.Expr

/**
 * Renderiza el AST preservando la notación del estudiante y añadiendo paréntesis solo cuando son
 * necesarios para que el texto se vuelva a leer con el mismo significado.
 */
object ExprPrinter {
    private const val TOP = 0
    private const val EQUALS = 1
    private const val SUM = 2
    private const val UNARY = 3
    private const val PRODUCT = 4
    private const val POWER = 5
    private const val ATOM = 6

    fun render(expression: Expr): String = render(expression, TOP)

    private fun render(expression: Expr, context: Int): String {
        val rendered = when (expression) {
            is Expr.Const -> expression.value.toString()
            is Expr.Var -> expression.name
            // La unidad se escribe al final del término completo: `1/2 m`, `2x km`.
            is Expr.Quantity -> render(expression.operand, PRODUCT) + " " + expression.unit.symbol
            is Expr.Sum -> renderSum(expression)
            is Expr.Neg -> "-" + render(expression.operand, UNARY)
            is Expr.Product -> renderProduct(expression)
            is Expr.Quotient -> render(expression.numerator, PRODUCT) + "/" + render(expression.denominator, PRODUCT)
            is Expr.Power -> render(expression.base, POWER) + "^" + render(expression.exponent, UNARY)
            is Expr.Percent -> render(expression.operand, ATOM) + "%"
            is Expr.Equals -> render(expression.left, SUM) + " = " + render(expression.right, SUM)
        }
        return if (needsParentheses(expression, context)) "($rendered)" else rendered
    }

    private fun needsParentheses(expression: Expr, context: Int): Boolean {
        val own = when (expression) {
            is Expr.Sum -> SUM
            is Expr.Neg -> UNARY
            is Expr.Product -> if (expression.factors.any { !it.explicit }) PRODUCT else PRODUCT
            is Expr.Quotient -> PRODUCT
            is Expr.Power -> POWER
            is Expr.Equals -> EQUALS
            is Expr.Quantity -> PRODUCT
            else -> ATOM
        }
        return own < context
    }

    private fun renderSum(sum: Expr.Sum): String = buildString {
        sum.terms.forEachIndexed { index, term ->
            val operand = render(term.operand, SUM)
            when {
                index == 0 -> append(if (term.operator == Expr.Sum.Term.Operator.MINUS) "-$operand" else operand)
                term.operator == Expr.Sum.Term.Operator.MINUS -> append(" - $operand")
                else -> append(" + $operand")
            }
        }
    }

    private fun renderProduct(product: Expr.Product): String = buildString {
        product.factors.forEachIndexed { index, factor ->
            val ultimo = index == product.factors.lastIndex
            // Una unidad que no cierra el término necesita paréntesis: sin ellos `2 km*3` volvería a
            // leerse como `2 · km · 3`, porque el analizador atadura la unidad al final del término.
            val operand = if (factor.operand is Expr.Quantity && !ultimo) {
                "(${render(factor.operand, TOP)})"
            } else {
                render(factor.operand, PRODUCT + if (factor.explicit) 0 else 1)
            }
            when {
                index == 0 -> append(operand)
                factor.explicit -> append("*$operand")
                else -> append(operand)
            }
        }
    }
}
