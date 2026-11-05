package mx.matica.math.solve

import com.ionspin.kotlin.bignum.integer.BigInteger
import mx.matica.math.ast.Expr
import mx.matica.math.domain.DomainRestrictions
import mx.matica.math.domain.Restriction
import mx.matica.math.numeric.Rational
import mx.matica.math.poly.Monomial
import mx.matica.math.poly.Normalizer
import mx.matica.math.poly.PolyOutcome
import mx.matica.math.poly.Polynomial

sealed interface SolveOutcome {
    data class Unique(val variable: String, val value: Rational) : SolveOutcome

    /** Raíces exactas de una ecuación tipo `x² = k` cuando k es un cuadrado perfecto racional. */
    data class Multiple(val variable: String, val values: List<Rational>) : SolveOutcome

    /** Todo valor satisface la ecuación. */
    data object Identity : SolveOutcome

    /** Ningún valor satisface la ecuación. */
    data object NoSolution : SolveOutcome

    /** La única solución algebraica queda prohibida por el dominio: raíz extraña. */
    data class RejectedByDomain(val variable: String, val value: Rational, val reason: String) : SolveOutcome

    data class Blocked(val reason: String) : SolveOutcome
}

/**
 * Resolutor exacto para ecuaciones de una incógnita hasta grado 2 con raíz racional exacta.
 * Fuera de ese territorio devuelve `Blocked`: es preferible admitir el límite que dar una
 * respuesta numerada sin fundamento.
 */
object LinearSolver {

    fun solve(expression: Expr): SolveOutcome {
        if (expression !is Expr.Equals) {
            return SolveOutcome.Blocked("Se necesita una ecuación con '=' para resolver.")
        }
        val restrictions = DomainRestrictions.of(expression)
        val canonical = (Normalizer.ofEquation(expression) as? PolyOutcome.Value)?.polynomial
            ?: return SolveOutcome.Blocked(
                (Normalizer.ofEquation(expression) as? PolyOutcome.Blocked)?.reason
                    ?: "La ecuación no se pudo llevar a forma polinómica exacta.",
            )

        return when {
            canonical.isZero -> SolveOutcome.Identity
            canonical.isConstant -> SolveOutcome.NoSolution
            canonical.degree == 1 && canonical.variables.size == 1 -> solveLinear(canonical, restrictions)
            canonical.degree == 2 && canonical.variables.size == 1 -> solvePureQuadratic(canonical, restrictions)
            else -> SolveOutcome.Blocked(
                "El resolutor actual cubre ecuaciones lineales y cuadráticas puras con raíz exacta; esta ecuación es de grado ${canonical.degree} con ${canonical.variables.size} incógnita(s).",
            )
        }
    }

    private fun solveLinear(polynomial: Polynomial, restrictions: Set<Restriction>): SolveOutcome {
        val variable = polynomial.variables.single()
        val slope = polynomial.coefficientOf(Monomial.of(variable, 1))
        if (slope.isZero) return SolveOutcome.NoSolution
        val candidate = -polynomial.constantTerm / slope
        return rejectIfProhibited(variable, candidate, restrictions)
    }

    private fun solvePureQuadratic(polynomial: Polynomial, restrictions: Set<Restriction>): SolveOutcome {
        val variable = polynomial.variables.single()
        val quadratic = polynomial.coefficientOf(Monomial.of(variable, 2))
        val linear = polynomial.coefficientOf(Monomial.of(variable, 1))
        if (quadratic.isZero) return SolveOutcome.NoSolution
        if (!linear.isZero) {
            return SolveOutcome.Blocked("La ecuación cuadrática tiene término lineal; falta el método de factorización o fórmula general.")
        }
        val squared = -polynomial.constantTerm / quadratic
        if (squared.signum < 0) return SolveOutcome.NoSolution
        val root = exactRationalSqrt(squared)
            ?: return SolveOutcome.Blocked("El resolutor solo devuelve raíces cuadráticas exactas; ${squared} no es un cuadrado racional perfecto.")
        val values = if (root.isZero) listOf(root) else listOf(-root, root)
        val accepted = values.filter { restrictions.none { restriction ->
            restriction is Restriction.NotEqualTo && restriction.variable == variable && restriction.excluded == it
        } }
        return when {
            accepted.isEmpty() -> SolveOutcome.NoSolution
            accepted.size == 1 -> SolveOutcome.Unique(variable, accepted.single())
            else -> SolveOutcome.Multiple(variable, accepted)
        }
    }

    private fun rejectIfProhibited(variable: String, value: Rational, restrictions: Set<Restriction>): SolveOutcome {
        val violated = restrictions.filterIsInstance<Restriction.NotEqualTo>()
            .firstOrNull { it.variable == variable && it.excluded == value }
        return if (violated == null) {
            SolveOutcome.Unique(variable, value)
        } else {
            SolveOutcome.RejectedByDomain(variable, value, "anula el denominador ($violated)")
        }
    }

    /** Raíz cuadrada exacta de una fracción: requiere cuadrados perfectos en numerador y denominador. */
    internal fun exactRationalSqrt(value: Rational): Rational? {
        val numeratorRoot = exactIntegerSqrt(value.numerator) ?: return null
        val denominatorRoot = exactIntegerSqrt(value.denominator) ?: return null
        return Rational.of(numeratorRoot, denominatorRoot)
    }

    internal fun exactIntegerSqrt(value: BigInteger): BigInteger? {
        if (value.signum() < 0) return null
        if (value.signum() == 0) return BigInteger.ZERO
        var guess = initialGuess(value)
        while (true) {
            val next = (guess + value / guess) / BigInteger.fromLong(2)
            if (next >= guess) {
                return if (guess * guess == value) guess else null
            }
            guess = next
        }
    }

    private fun initialGuess(value: BigInteger): BigInteger {
        val digits = value.toString().length
        val half = (digits + 1) / 2
        return BigInteger.TEN.pow(half)
    }
}
