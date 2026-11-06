package mx.matica.math.units

import mx.matica.math.ast.Expr
import mx.matica.math.ast.walk
import mx.matica.math.ast.variables
import mx.matica.math.printer.ExprPrinter

/**
 * El análisis dimensional que B1-09 dejó pendiente.
 *
 * Corre antes de evaluar y antes de normalizar, y no calcula nada: decide si una expresión con
 * unidades **tiene sentido**. Es un paso aparte del evaluador porque allí todo es `Rational` y una
 * suma entre `3 km` y `2 s` daría `3002`: un número que mintió sin enterarse. El motor prefiere un
 * motivo legible antes que ese número.
 *
 * Convención sobre los números y las incógnitas sin unidad: se leen en la unidad base de la familia
 * presente, así `3 km + 500 = 3500 m` y `3 km + x = 7 km` sale `x = 4000`. Es la lectura que coincide
 * con lo que el estudiante quiere decir casi siempre, y sobre todo es la única que deja verificar la
 * respuesta: tras sustituir `x = 4000` en `3 km + x = 7 km` quedaría un número suelto que, con otra
 * regla, el propio motor rechazaría por suelto.
 */
object DimensionalCheck {

    /** `null` cuando la expresión se puede evaluar; si no, el motivo escrito para el estudiante. */
    fun problem(expression: Expr): String? {
        val quantities = expression.walk().filterIsInstance<Expr.Quantity>()
        if (quantities.isEmpty()) return null

        val primera = quantities.first()
        val extrana = quantities.firstOrNull { it.unit.dimension != primera.unit.dimension }
        if (extrana != null) {
            return "mezcla familias distintas: ${primera.unit.symbol} es de ${primera.unit.dimension.labelEs} " +
                "y ${extrana.unit.symbol} es de ${extrana.unit.dimension.labelEs}"
        }
        return compoundProblem(expression)
    }

    /**
     * Multiplicar, dividir o elevar unidades produce unidades compuestas (`m²`, `km/h`) que esta tabla
     * no representa, y guardar `6` como si fuera metros diría una falsedad. Se rechaza en lugar de
     * devolver un número con la familia equivocada.
     */
    private fun compoundProblem(expression: Expr): String? {
        for (product in expression.walk().filterIsInstance<Expr.Product>()) {
            val conUnidad = product.factors.count { factor -> factor.operand.containsUnit() }
            if (conUnidad > 1) {
                return "el motor no multiplica unidades entre sí: '${ExprPrinter.render(product)}' " +
                    "daría una unidad compuesta"
            }
        }
        for (quotient in expression.walk().filterIsInstance<Expr.Quotient>()) {
            if (quotient.numerator.containsUnit() && quotient.denominator.containsUnit()) {
                return "el motor no divide unidades entre sí: '${ExprPrinter.render(quotient)}' " +
                    "daría una unidad compuesta"
            }
        }
        for (power in expression.walk().filterIsInstance<Expr.Power>()) {
            if (power.exponent.containsUnit()) {
                return "una unidad no puede estar en el exponente: '${ExprPrinter.render(power)}'"
            }
            val exponente = power.exponent
            val esUno = exponente is Expr.Const && exponente.value.isOne
            if (power.base.containsUnit() && !esUno) {
                return "elevar una unidad da una unidad compuesta: '${ExprPrinter.render(power)}'"
            }
        }
        return null
    }

    private fun Expr.containsUnit(): Boolean = walk().any { it is Expr.Quantity }
}
