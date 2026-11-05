package mx.matica.math.numeric

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Fracción exacta en forma canónica: denominador positivo y mcd(numerador, denominador) = 1.
 * Nunca usa Double, así que 1/3 + 1/6 no depende de redondeos binarios.
 */
class Rational private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) : Comparable<Rational> {

    val isZero: Boolean get() = numerator.signum() == 0
    val isOne: Boolean get() = this == ONE
    val isInteger: Boolean get() = denominator == BigInteger.ONE
    val signum: Int get() = numerator.signum()

    val abs: Rational get() = if (signum < 0) -this else this

    operator fun plus(other: Rational): Rational = of(
        numerator * other.denominator + other.numerator * denominator,
        denominator * other.denominator,
    )

    operator fun minus(other: Rational): Rational = plus(-other)

    operator fun times(other: Rational): Rational = of(numerator * other.numerator, denominator * other.denominator)

    operator fun div(other: Rational): Rational {
        require(!other.isZero) { "División entre cero." }
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    operator fun unaryMinus(): Rational = Rational(-numerator, denominator)

    operator fun times(scalar: Long): Rational = times(Rational.fromLong(scalar))

    fun pow(exponent: Int): Rational {
        if (exponent == 0) return ONE
        if (exponent < 0) {
            require(!isZero) { "División entre cero." }
            return reciprocal().pow(-exponent)
        }
        return Rational(numerator.pow(exponent), denominator.pow(exponent))
    }

    fun reciprocal(): Rational {
        require(!isZero) { "La recíproca de cero no existe." }
        return Rational(denominator, numerator)
    }

    fun toLongOrNull(): Long? = if (isInteger) numerator.longValue() else null

    override fun compareTo(other: Rational): Int = (numerator * other.denominator).compareTo(other.numerator * denominator)

    override fun equals(other: Any?): Boolean =
        other is Rational && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String =
        if (isInteger) numerator.toString() else "$numerator/$denominator"

    companion object {
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)
        val MINUS_ONE = Rational(-BigInteger.ONE, BigInteger.ONE)

        fun fromLong(value: Long): Rational = Rational(BigInteger.fromLong(value), BigInteger.ONE)

        fun of(numerator: BigInteger, denominator: BigInteger): Rational {
            require(denominator.signum() != 0) { "El denominador de una fracción no puede ser cero." }
            val signedNumerator = if (denominator.signum() < 0) -numerator else numerator
            val positiveDenominator = denominator.abs()
            val divisor = signedNumerator.gcd(positiveDenominator)
            return if (divisor == BigInteger.ONE) {
                Rational(signedNumerator, positiveDenominator)
            } else {
                Rational(signedNumerator / divisor, positiveDenominator / divisor)
            }
        }

        fun of(numerator: Long, denominator: Long): Rational =
            of(BigInteger.fromLong(numerator), BigInteger.fromLong(denominator))

        /** `0.75` -> `3/4` exacto: el valor se construye desde los dígitos, no desde un Double. */
        fun fromDecimal(digits: String): Rational {
            val text = digits.trim()
            require(text.isNotEmpty()) { "Número vacío." }
            val negative = text.startsWith("-")
            val unsigned = text.removePrefix("-").removePrefix("+")
            require(unsigned.contains('.')) { "Sin punto decimal: $digits" }
            val parts = unsigned.split('.')
            require(parts.size == 2 && parts[0].all { it.isDigit() } && parts[1].all { it.isDigit() }) {
                "Decimal mal formado: $digits"
            }
            val unscaled = parts.joinToString(separator = "")
            val scale = powerOfTen(parts[1].length)
            val value = of(BigInteger.parseString(unscaled, 10), scale)
            return if (negative) -value else value
        }

        fun lcmOfDenominators(values: List<Rational>): BigInteger =
            values.map { it.denominator }.fold(BigInteger.ONE) { acc, den ->
                if (acc.signum() == 0 || den.signum() == 0) {
                    acc * den
                } else {
                    acc * den / acc.gcd(den)
                }
            }

        private fun powerOfTen(exponent: Int): BigInteger = BigInteger.TEN.pow(exponent)

    }
}
