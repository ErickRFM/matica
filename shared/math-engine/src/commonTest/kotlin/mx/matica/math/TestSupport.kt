package mx.matica.math

import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ExprParser
import mx.matica.math.parser.ParseOutcome
import mx.matica.math.poly.Normalizer
import mx.matica.math.poly.PolyOutcome
import mx.matica.math.poly.Polynomial

/** Utilidades de prueba: si una entrada no parsea, el fallo debe verse como tal, no como un `ClassCastException`. */
internal fun expr(text: String): Expr = when (val outcome = ExprParser(text).parse()) {
    is ParseOutcome.Success -> outcome.expression
    is ParseOutcome.Failure -> error("La prueba construyó una entrada inválida: '$text' -> ${outcome.reason}")
}

internal fun equation(text: String): Expr.Equals = expr(text) as? Expr.Equals
    ?: error("La prueba esperaba una ecuación con '=': '$text'")

internal fun eq(text: String): Expr.Equals = equation(text)

/** El polinomio como `monomio -> coeficiente`, para que un fallo muestre el término concreto. */
internal fun Polynomial.terms(): Map<String, Rational> =
    sortedTerms.associate { (monomial, coefficient) -> monomial.key to coefficient }

internal fun poly(text: String): Polynomial = when (val outcome = Normalizer.of(expr(text))) {
    is PolyOutcome.Value -> outcome.polynomial
    is PolyOutcome.Blocked -> error("El normalizador se bloqueó en '$text': ${outcome.reason}")
}

internal fun rational(numerator: Long, denominator: Long = 1): Rational = Rational.of(numerator, denominator)
