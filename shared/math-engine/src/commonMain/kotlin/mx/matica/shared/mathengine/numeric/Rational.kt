package mx.matica.shared.mathengine.numeric

/**
 * Fracción exacta normalizada: siempre mantiene el denominador positivo y mcd(num, den) = 1.
 */
data class Rational(val numerator: Long, val denominator: Long) : Comparable<Rational> {
    init {
        require(denominator != 0L) { "El denominador de una fracción no puede ser cero." }
    }

    companion object {
        fun of(numerator: Long, denominator: Long): Rational {
            require(denominator != 0L) { "El denominador de una fracción no puede ser cero." }
            val sign = if (denominator < 0) -1L else 1L
            val n = numerator * sign
            val d = denominator * sign
            val g = gcd(abs(n), d)
            return if (g <= 1L) Rational(n, d) else Rational(n / g, d / g)
        }

        fun fromInt(value: Long): Rational = Rational(value, 1L)

        private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

        private fun abs(value: Long): Long = if (value < 0) -value else value
    }

    operator fun plus(other: Rational): Rational =
        of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)

    operator fun minus(other: Rational): Rational = plus(of(-other.numerator, other.denominator))

    operator fun times(other: Rational): Rational =
        of(numerator * other.numerator, denominator * other.denominator)

    operator fun div(other: Rational): Rational {
        require(other.numerator != 0L) { "División entre cero." }
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    operator fun unaryMinus(): Rational = of(-numerator, denominator)

    fun isZero(): Boolean = numerator == 0L

    override fun compareTo(other: Rational): Int =
        (numerator * other.denominator).compareTo(other.numerator * denominator)

    override fun toString(): String = if (denominator == 1L) numerator.toString() else "$numerator/$denominator"
}
