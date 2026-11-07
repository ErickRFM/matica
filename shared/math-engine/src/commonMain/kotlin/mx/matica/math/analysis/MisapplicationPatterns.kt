package mx.matica.math.analysis

import mx.matica.math.ast.Expr
import mx.matica.math.ast.variables
import mx.matica.math.ast.walk
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.Lexer
import mx.matica.math.parser.TokenKind
import mx.matica.math.poly.Normalizer
import mx.matica.math.poly.PolyOutcome
import mx.matica.math.poly.Polynomial

/**
 * Genera las formas incorrectas típicas de un paso: mal distribuir, perder un signo al quitar
 * paréntesis y leer de izquierda a derecha ignorando la jerarquía. Si el paso del estudiante
 * coincide con una candidata, el diagnóstico deja de ser una conjetura.
 */
internal object MisapplicationPatterns {
    /** Candidatas del lado del estudiante cuando distribuyó el coeficiente solo sobre el primer término. */
    fun distributionDeltas(side: Expr): List<Polynomial> {
        val sideOutcome = Normalizer.of(side) as? PolyOutcome.Value ?: return emptyList()
        val results = mutableListOf<Polynomial>()
        side.walk().forEach { expression ->
            val product = expression as? Expr.Product ?: return@forEach
            if (product.factors.none { it.operand is Expr.Sum }) return@forEach
            val correct = Normalizer.of(product) as? PolyOutcome.Value ?: return@forEach
            val wrong = misexpandedProduct(product) ?: return@forEach
            val wrongOutcome = Normalizer.of(wrong) as? PolyOutcome.Value ?: return@forEach
            results += sideOutcome.polynomial - correct.polynomial + wrongOutcome.polynomial
        }
        return results
    }

    /** Candidatas de `-(a - b) → -a - b`: el signo se aplicó solo al primer término. */
    fun signDeltas(side: Expr): List<Polynomial> {
        val sideOutcome = Normalizer.of(side) as? PolyOutcome.Value ?: return emptyList()
        val results = mutableListOf<Polynomial>()
        side.walk().forEach { expression ->
            val negated = expression as? Expr.Neg ?: return@forEach
            val sum = negated.operand as? Expr.Sum ?: return@forEach
            if (sum.terms.size < 2) return@forEach
            val correct = Normalizer.of(negated) as? PolyOutcome.Value ?: return@forEach
            val wrong = Expr.Sum(
                listOf(Expr.Sum.Term(Expr.Sum.Term.Operator.MINUS, sum.terms.first().operand)) + sum.terms.drop(1),
            )
            val wrongOutcome = Normalizer.of(wrong) as? PolyOutcome.Value ?: return@forEach
            results += sideOutcome.polynomial - correct.polynomial + wrongOutcome.polynomial
        }
        return results
    }

    /**
     * Lee la cadena de operadores estrictamente de izquierda a derecha. `2 + 3 * 4` da 20, que es
     * la firma del error de jerarquía; si el estudiante escribió ese valor, el origen es la
     * precedencia y no una distracción aritmética.
     */
    fun leftToRightValue(text: String): Rational? {
        val tokens = try {
            Lexer.tokenize(text)
        } catch (error: IllegalArgumentException) {
            return null
        }
        var accumulator: Rational? = null
        var pending = TokenKind.PLUS
        for (token in tokens) {
            when {
                token.kind == TokenKind.EOF -> break
                token.kind == TokenKind.NUMBER -> {
                    val value = numberFrom(token.text) ?: return null
                    val current = accumulator
                    accumulator = if (current == null) value else current.applyOperator(pending, value) ?: return null
                }

                token.kind in OPERATORS -> pending = token.kind
                else -> return null
            }
        }
        return accumulator
    }

    /**
     * Detecta el error canónico de sumar numeradores con numeradores y denominadores con
     * denominadores: 1/2 + 1/3 -> (1+1)/(2+3) = 2/5.
     */
    fun fractionDenominatorAdditionValue(expression: Expr): Rational? {
        val sum = expression as? Expr.Sum ?: return null
        if (sum.terms.size < 2) return null
        var numSum = 0L
        var denSum = 0L
        for (term in sum.terms) {
            val quotient = term.operand as? Expr.Quotient ?: return null
            val numConst = quotient.numerator as? Expr.Const ?: return null
            val denConst = quotient.denominator as? Expr.Const ?: return null
            val num = numConst.value.toLongOrNull() ?: return null
            val den = denConst.value.toLongOrNull() ?: return null
            if (den == 0L) return null
            val signedNum = if (term.operator == Expr.Sum.Term.Operator.MINUS) -num else num
            numSum += signedNum
            denSum += den
        }
        if (denSum == 0L) return null
        return try {
            Rational.of(numSum, denSum)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Cadena enteramente numérica: se usa para comparar el valor correcto contra el mal ordenado. */
    fun isPurelyNumeric(expression: Expr): Boolean = expression.variables().isEmpty()

    private val OPERATORS = setOf(TokenKind.PLUS, TokenKind.MINUS, TokenKind.STAR, TokenKind.SLASH, TokenKind.CARET)

    private fun Rational.applyOperator(kind: TokenKind, other: Rational): Rational? = when (kind) {
        TokenKind.PLUS -> this + other
        TokenKind.MINUS -> this - other
        TokenKind.STAR -> this * other
        TokenKind.SLASH -> if (other.isZero) null else this / other
        TokenKind.CARET -> {
            val exponent = other.toLongOrNull() ?: return null
            if (exponent < -64 || exponent > 64) null else pow(exponent.toInt())
        }

        else -> null
    }

    private fun numberFrom(text: String): Rational? = try {
        if (text.contains('.')) Rational.fromDecimal(text) else Rational.of(text.toLong(), 1L)
    } catch (error: IllegalArgumentException) {
        null
    }

    private fun misexpandedProduct(product: Expr.Product): Expr? {
        val sumIndex = product.factors.indexOfFirst { it.operand is Expr.Sum }
        if (sumIndex < 0) return null
        val sum = product.factors[sumIndex].operand as Expr.Sum
        val coefficientFactors = product.factors.filterIndexed { index, _ -> index != sumIndex }
        if (coefficientFactors.isEmpty()) return null
        val wrongFirst = Expr.Product(
            coefficientFactors + Expr.Product.Factor(explicit = true, operand = sum.terms.first().operand),
        )
        val terms = listOf(Expr.Sum.Term(Expr.Sum.Term.Operator.PLUS, wrongFirst)) +
            sum.terms.drop(1).map { Expr.Sum.Term(it.operator, it.operand) }
        return Expr.Sum(terms)
    }
}
