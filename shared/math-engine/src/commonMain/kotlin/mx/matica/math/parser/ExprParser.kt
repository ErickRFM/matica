package mx.matica.math.parser

import com.ionspin.kotlin.bignum.integer.BigInteger
import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.math.units.MeasurementUnit
import mx.matica.math.units.Units

sealed interface ParseOutcome {
    data class Success(val expression: Expr) : ParseOutcome
    data class Failure(val position: Int, val reason: String, val suggestion: String) : ParseOutcome
}

/**
 * Descenso recursivo con la jerarquía usual: `=`, suma/resta, producto/cociente
 * (incluida la multiplicación implícita `2x` y `2(x+3)`), negativo unary y potencia.
 */
class ExprParser(private val input: String) {
    private var tokens: List<Token> = emptyList()
    private var cursor = 0

    fun parse(): ParseOutcome {
        if (input.isBlank()) return ParseOutcome.Failure(0, "Expresión vacía.", "Escribe una expresión, por ejemplo 2(x+3)=14.")
        return try {
            tokens = Lexer.tokenize(input)
            val expression = parseEquation()
            val current = peek()
            if (current.kind != TokenKind.EOF) {
                throw MathSyntaxException(
                    current.position,
                    "No se esperaba '${current.text}' en este punto.",
                    "Revisa que la expresión esté completa y sin paréntesis sobrantes.",
                )
            }
            ParseOutcome.Success(expression)
        } catch (error: MathSyntaxException) {
            error.asFailure()
        }
    }

    private fun MathSyntaxException.asFailure(): ParseOutcome.Failure =
        ParseOutcome.Failure(position, message?.substringBefore(" (posición") ?: "Expresión inválida.", suggestion)

    private fun peek(): Token = tokens[cursor]

    private fun advance(): Token = tokens[cursor++]

    private fun match(kind: TokenKind): Boolean =
        if (peek().kind == kind) {
            advance()
            true
        } else {
            false
        }

    private fun parseEquation(): Expr {
        val left = parseSum()
        if (peek().kind != TokenKind.EQUALS) return left
        advance()
        val right = parseSum()
        if (peek().kind == TokenKind.EQUALS) {
            throw MathSyntaxException(
                peek().position,
                "Dos signos de igual en la misma línea.",
                "Escribe una sola ecuación, por ejemplo 2(x+3)=14.",
            )
        }
        return Expr.Equals(left, right)
    }

    private fun parseSum(): Expr {
        val terms = mutableListOf(Expr.Sum.Term(Expr.Sum.Term.Operator.PLUS, parseTerm()))
        while (peek().kind == TokenKind.PLUS || peek().kind == TokenKind.MINUS) {
            val operator = when (advance().kind) {
                TokenKind.PLUS -> Expr.Sum.Term.Operator.PLUS
                else -> Expr.Sum.Term.Operator.MINUS
            }
            terms += Expr.Sum.Term(operator, parseTerm())
        }
        return if (terms.size == 1) terms.single().operand else Expr.Sum(terms)
    }

    private fun parseTerm(): Expr {
        val numerator = mutableListOf(Expr.Product.Factor(explicit = true, operand = parseUnary()))
        val denominator = mutableListOf<Expr.Product.Factor>()
        var unidad: MeasurementUnit? = null

        while (true) {
            when (peek().kind) {
                TokenKind.STAR -> {
                    advance()
                    numerator += Expr.Product.Factor(explicit = true, operand = parseUnary())
                }

                TokenKind.SLASH -> {
                    advance()
                    denominator += Expr.Product.Factor(explicit = true, operand = parseUnary())
                }

                TokenKind.NUMBER, TokenKind.IDENT, TokenKind.LPAREN -> {
                    val candidate = peek()
                    val unidadCandidata = if (candidate.kind == TokenKind.IDENT) Units.bySymbol(candidate.text) else null
                    if (unidadCandidata != null && terminaTermino(tokens[cursor + 1].kind)) {
                        if (unidad != null) {
                            throw MathSyntaxException(
                                candidate.position,
                                "El término lleva dos unidades.",
                                "Escribe una sola unidad al final del término, por ejemplo 3 km + 200 m.",
                            )
                        }
                        advance()
                        unidad = unidadCandidata
                    } else {
                        if (unidadCandidata != null) {
                            // Una unidad que no cierra el término no puede interpretarse sin adivinar:
                            // `2 m * 3 m` pide un metro cuadrado que la tabla no tiene, y leer `m` como
                            // incógnita daría un ejercicio distinto al que el estudiante escribió.
                            throw MathSyntaxException(
                                candidate.position,
                                if (tokens[cursor + 1].kind == TokenKind.CARET) {
                                    "El motor no representa unidades elevadas como '${candidate.text}²'."
                                } else {
                                    "'${candidate.text}' es una unidad y va al final del término; " +
                                        "el motor no multiplica unidades entre sí."
                                },
                                "Escribe la conversión con unidades simples, por ejemplo 1 km = 1000 m.",
                            )
                        }
                        val lastOperand = (denominator.lastOrNull()?.operand ?: numerator.last().operand)
                        if (lastOperand is Expr.Const && candidate.kind == TokenKind.NUMBER) {
                            throw MathSyntaxException(
                                candidate.position,
                                "Dos números seguidos sin operador.",
                                "Falta un operador: escribe 2*3, o una fracción como 1/2 en lugar de 1 1/2.",
                            )
                        }
                        numerator += Expr.Product.Factor(explicit = false, operand = parseUnary())
                    }
                }

                else -> break
            }
        }

        val top = if (numerator.size == 1) numerator.single().operand else Expr.Product(numerator)
        if (denominator.isEmpty()) return conUnidad(top, unidad)
        val bottom = if (denominator.size == 1) denominator.single().operand else Expr.Product(denominator)
        // La unidad envuelve al cociente completo: `1/2 m` es medio metro, no `1/(2 m)`.
        return conUnidad(Expr.Quotient(top, bottom), unidad)
    }

    private fun conUnidad(expression: Expr, unidad: MeasurementUnit?): Expr =
        if (unidad == null) expression else Expr.Quantity(expression, unidad)

    /** Tokens que cierran un término: después de la unidad ya no puede venir otro factor. */
    private fun terminaTermino(kind: TokenKind): Boolean = when (kind) {
        TokenKind.PLUS, TokenKind.MINUS, TokenKind.EQUALS, TokenKind.RPAREN, TokenKind.EOF -> true
        else -> false
    }

    private fun parseUnary(): Expr = when (peek().kind) {
        TokenKind.PLUS -> {
            advance()
            parseUnary()
        }

        TokenKind.MINUS -> {
            advance()
            Expr.Neg(parseUnary())
        }

        else -> parsePower()
    }

    private fun parsePower(): Expr {
        val base = parseAtom()
        if (!match(TokenKind.CARET)) return base
        return Expr.Power(base, parseUnary())
    }

    private fun parseAtom(): Expr {
        val token = peek()
        val value = when (token.kind) {
            TokenKind.NUMBER -> {
                advance()
                Expr.Const(numberOf(token))
            }

            TokenKind.IDENT -> {
                advance()
                // `x(x+1)` es multiplicar; `sen(x)` es aplicar una función. Con nombres de una sola
                // letra asumimos variable; a partir de dos letras el significado es ambiguo y lo
                // rechazamos en lugar de reinterpretar lo que el estudiante escribió.
                if (token.text.length > 1 && peek().kind == TokenKind.LPAREN) {
                    throw MathSyntaxException(
                        token.position,
                        "El motor aún no maneja funciones como '${token.text}(...)'.",
                        "Escribe solo expresiones con números, variables y los operadores + - * / ^ %.",
                    )
                }
                val unidad = Units.bySymbol(token.text)
                // `km = 1000 m` es la forma en que un libro escribe una regla de conversión: la
                // unidad sola vale una vez su magnitud. Sólo cuando la unidad cierra el término, para
                // no robarle la letra a una incógnita que siga viva dentro del producto.
                if (unidad != null && terminaTermino(peek().kind)) {
                    Expr.Quantity(Expr.Const(Rational.ONE), unidad)
                } else {
                    Expr.Var(token.text)
                }
            }

            TokenKind.LPAREN -> {
                advance()
                val inner = parseSum()
                if (!match(TokenKind.RPAREN)) {
                    throw MathSyntaxException(
                        peek().position,
                        "Falta cerrar el paréntesis abierto aquí.",
                        "Añade ')' al final del grupo: por ejemplo 2(x+3).",
                    )
                }
                inner
            }

            else -> throw MathSyntaxException(
                token.position,
                "Se esperaba un número, una variable o un paréntesis.",
                "Escribe algo como 14, x o (x+3).",
            )
        }

        var result = value
        while (match(TokenKind.PERCENT)) {
            result = Expr.Percent(result)
        }
        return result
    }

    private fun numberOf(token: Token): Rational {
        val digits = token.text
        require(digits.isNotEmpty()) { "Número vacío en la posición ${token.position}." }
        return if (digits.contains('.')) {
            try {
                Rational.fromDecimal(digits)
            } catch (error: IllegalArgumentException) {
                throw MathSyntaxException(token.position, error.message ?: "Número inválido.", "Revisa los decimales.")
            }
        } else {
            Rational.of(BigInteger.parseString(digits, 10), BigInteger.ONE)
        }
    }
}
