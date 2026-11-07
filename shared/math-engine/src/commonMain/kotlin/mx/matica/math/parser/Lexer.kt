package mx.matica.math.parser

internal enum class TokenKind { NUMBER, IDENT, PLUS, MINUS, STAR, SLASH, CARET, LPAREN, RPAREN, EQUALS, PERCENT, EOF }

internal data class Token(val kind: TokenKind, val text: String, val position: Int)

class MathSyntaxException(val position: Int, message: String, val suggestion: String) :
    IllegalArgumentException("$message (posición $position). Sugerencia: $suggestion")

/**
 * Tokenizador. Acepta enteros, decimales con un solo punto, identificadores de una o más letras
 * y los operadores `+ - * / ^ = ( ) %`. Los espacios se ignoran.
 */
internal object Lexer {
    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                char.isWhitespace() -> index++
                char.isDigit() || (char == '.' && index + 1 < input.length && input[index + 1].isDigit()) -> {
                    val start = index
                    var dots = 0
                    while (index < input.length && (input[index].isDigit() || input[index] == '.')) {
                        if (input[index] == '.') dots++
                        index++
                    }
                    val text = input.substring(start, index)
                    if (dots > 1) {
                        throw MathSyntaxException(start, "Número con más de un punto decimal.", "Escribe algo como 0.75 o 3/4.")
                    }
                    if (text == ".") {
                        throw MathSyntaxException(start, "Punto decimal suelto.", "Escribe un dígito, por ejemplo 0.5.")
                    }
                    tokens += Token(TokenKind.NUMBER, text, start)
                }

                char.isLetter() || char == '_' || char == 'ñ' || char == 'Ñ' -> {
                    val start = index
                    while (index < input.length && (input[index].isLetterOrDigit() || input[index] == '_')) index++
                    tokens += Token(TokenKind.IDENT, input.substring(start, index), start)
                }

                char == '²' -> {
                    tokens += Token(TokenKind.CARET, "^", index)
                    tokens += Token(TokenKind.NUMBER, "2", index)
                    index++
                }

                else -> {
                    val kind = when (char) {
                        '+' -> TokenKind.PLUS
                        '-' -> TokenKind.MINUS
                        '*' -> TokenKind.STAR
                        '/' -> TokenKind.SLASH
                        '^' -> TokenKind.CARET
                        '(' -> TokenKind.LPAREN
                        ')' -> TokenKind.RPAREN
                        '=' -> TokenKind.EQUALS
                        '%' -> TokenKind.PERCENT
                        else -> throw MathSyntaxException(
                            index,
                            "Carácter no soportado: '$char'.",
                            "Usa solo números, letras y los operadores + - * / ^ = ( ) %.",
                        )
                    }
                    tokens += Token(kind, char.toString(), index)
                    index++
                }
            }
        }
        tokens += Token(TokenKind.EOF, "", input.length)
        return tokens
    }
}
