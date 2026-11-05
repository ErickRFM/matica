package mx.matica.exercises

import kotlin.math.abs
import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational

/**
 * Constructores del AST compartidos por las plantillas. Escriben la ecuación como la escribiría un
 * libro de texto: sin `1x`, sin `x + 0` y con el signo en el operador del término.
 */
internal object ExerciseAst {
    fun const(value: Long): Expr.Const = Expr.Const(Rational.fromLong(value))

    fun variable(name: String): Expr.Var = Expr.Var(name)

    /** `3x`, o `x` cuando el coeficiente vale 1: "1x" enseñaría una regla que no existe. */
    fun coefficientTerm(coefficient: Long, variable: String): Expr {
        require(coefficient > 0) { "El coeficiente se recibe en valor absoluto, llegó $coefficient." }
        if (coefficient == 1L) return variable(variable)
        return Expr.Product(
            listOf(
                Expr.Product.Factor(explicit = false, const(coefficient)),
                Expr.Product.Factor(explicit = false, variable(variable)),
            ),
        )
    }

    fun term(negative: Boolean, operand: Expr): Expr.Sum.Term = Expr.Sum.Term(
        operator = if (negative) Expr.Sum.Term.Operator.MINUS else Expr.Sum.Term.Operator.PLUS,
        operand = operand,
    )

    /** `ax + b` omitiendo el sumando constante cuando vale 0. */
    fun linearSum(coefficient: Long, constant: Long, variable: String): Expr {
        val terms = mutableListOf(term(coefficient < 0, coefficientTerm(abs(coefficient), variable)))
        if (constant != 0L) terms += term(constant < 0, const(abs(constant)))
        return Expr.Sum(terms)
    }

    /** `x + b` entre paréntesis, lista para que un factor la multiplique. */
    fun innerSum(constant: Long, variable: String): Expr = linearSum(1L, constant, variable)
}
