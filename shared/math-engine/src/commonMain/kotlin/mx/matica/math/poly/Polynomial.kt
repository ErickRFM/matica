package mx.matica.math.poly

import mx.matica.math.numeric.Rational

/** Producto de variables con exponentes enteros no negativos: `x^2*y`. */
data class Monomial(val exponents: Map<String, Int>) : Comparable<Monomial> {
    private val canonical: Map<String, Int> = exponents.filterValues { it != 0 }.toSortedMap()
    val key: String = if (canonical.isEmpty()) {
        "1"
    } else {
        canonical.entries.joinToString("*") { (variable, exponent) ->
            if (exponent == 1) variable else "$variable^$exponent"
        }
    }

    val totalDegree: Int get() = canonical.values.sum()
    val variables: Set<String> get() = canonical.keys
    val isConstant: Boolean get() = canonical.isEmpty()

    operator fun times(other: Monomial): Monomial {
        val merged = canonical.toMutableMap()
        other.canonical.forEach { (variable, exponent) ->
            merged[variable] = (merged[variable] ?: 0) + exponent
        }
        return Monomial(merged)
    }

    fun pow(exponent: Int): Monomial = Monomial(canonical.mapValues { (_, value) -> value * exponent })

    /** `true` cuando este monomio contiene a `other` (exponentes mayores o iguales en cada variable). */
    fun contains(other: Monomial): Boolean = other.canonical.all { (variable, exponent) ->
        (canonical[variable] ?: 0) >= exponent
    }

    override fun compareTo(other: Monomial): Int = key.compareTo(other.key)

    override fun toString(): String = key

    companion object {
        val CONSTANT = Monomial(emptyMap())
        fun of(variable: String, exponent: Int = 1): Monomial = Monomial(mapOf(variable to exponent))
    }
}

/**
 * Combinación lineal exacta de monomios con coeficientes racionales. Los coeficientes cero se
 * descartan, así que dos expresiones algebraicamente iguales terminan en el mismo mapa.
 */
class Polynomial(terms: Map<Monomial, Rational>) {
    private val storage: Map<Monomial, Rational> = terms.filterValues { !it.isZero }.toSortedMap()

    val sortedTerms: List<Pair<Monomial, Rational>> get() = storage.entries.map { it.key to it.value }
    val isEmpty: Boolean get() = storage.isEmpty()
    val isZero: Boolean get() = storage.isEmpty()
    val variables: Set<String> get() = storage.keys.flatMap { it.variables }.toSet()
    val degree: Int get() = storage.keys.maxOfOrNull { it.totalDegree } ?: 0
    val isConstant: Boolean get() = storage.keys.all { it.isConstant }
    val isMonomial: Boolean get() = storage.size <= 1

    val constantTerm: Rational get() = storage[Monomial.CONSTANT] ?: Rational.ZERO

    fun coefficientOf(monomial: Monomial): Rational = storage[monomial] ?: Rational.ZERO

    /** Único monomio no constante con exponente 1 en una variable, si existe. */
    fun linearCoefficientOf(variable: String): Rational? {
        val monomial = Monomial.of(variable, 1)
        if (storage.keys.any { !it.isConstant && it != monomial }) return null
        return coefficientOf(monomial)
    }

    operator fun plus(other: Polynomial): Polynomial {
        val merged = storage.toMutableMap()
        other.storage.forEach { (monomial, coefficient) ->
            merged[monomial] = (merged[monomial] ?: Rational.ZERO) + coefficient
        }
        return Polynomial(merged)
    }

    operator fun minus(other: Polynomial): Polynomial = plus(-other)

    operator fun times(other: Polynomial): Polynomial {
        val product = mutableMapOf<Monomial, Rational>()
        storage.forEach { (leftMonomial, leftCoefficient) ->
            other.storage.forEach { (rightMonomial, rightCoefficient) ->
                val target = leftMonomial * rightMonomial
                product[target] = (product[target] ?: Rational.ZERO) + leftCoefficient * rightCoefficient
            }
        }
        return Polynomial(product)
    }

    operator fun times(scalar: Rational): Polynomial = Polynomial(storage.mapValues { it.value * scalar })

    operator fun div(scalar: Rational): Polynomial = times(scalar.reciprocal())

    operator fun unaryMinus(): Polynomial = times(Rational.MINUS_ONE)

    fun pow(exponent: Int): Polynomial {
        require(exponent >= 0) { "Exponente negativo en un polinomio." }
        var result = Polynomial(mapOf(Monomial.CONSTANT to Rational.ONE))
        var base = this
        var remaining = exponent
        while (remaining > 0) {
            if (remaining % 2 == 1) result *= base
            base *= base
            remaining /= 2
        }
        return result
    }

    /**
     * Dos ecuaciones lineales tienen el mismo conjunto solución cuando sus polinomios canónicos
     * son proporcionales por un factor no nulo. Devuelve ese factor o `null` si no existe.
     */
    fun proportionalityFactorTo(other: Polynomial): Rational? {
        if (isZero && other.isZero) return Rational.ONE
        if (isZero || other.isZero) return null
        if (variables != other.variables) return null
        val anchor = storage.keys.minByOrNull { it.key } ?: return null
        val reference = storage.getValue(anchor)
        val counterpart = other.coefficientOf(anchor)
        if (counterpart.isZero) return null
        val factor = counterpart / reference
        val expected = this * factor
        return if (expected == other) factor else null
    }

    override fun equals(other: Any?): Boolean = other is Polynomial && storage == other.storage

    override fun hashCode(): Int = storage.hashCode()

    override fun toString(): String = if (isZero) {
        "0"
    } else {
        sortedTerms.joinToString(" + ") { (monomial, coefficient) ->
            if (monomial.isConstant) coefficient.toString() else "$coefficient$monomial"
        }
    }
}
