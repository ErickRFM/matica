package mx.matica.math.contract

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational

/**
 * Contrato `matica.symbolic.v1` entre el motor Kotlin y el servicio de cálculo simbólico.
 *
 * Viaja un AST tipado, nunca texto matemático: el servicio no recibe la expresión escrita por el
 * estudiante, así que no tiene forma de interpretar (ni ejecutar) una cadena arbitraria. Las
 * constantes son cadenas exactas (`-1/2`), jamás números JSON de punto flotante.
 */
const val SYMBOLIC_CONTRACT_VERSION: String = "1"

@Serializable
sealed interface SymbolicNode {

    @Serializable @SerialName("const") data class Const(val value: String) : SymbolicNode

    @Serializable @SerialName("var") data class Var(val name: String) : SymbolicNode

    @Serializable @SerialName("sum") data class Sum(val terms: List<SymbolicNode>) : SymbolicNode

    @Serializable @SerialName("neg") data class Neg(val operand: SymbolicNode) : SymbolicNode

    @Serializable @SerialName("product") data class Product(val factors: List<SymbolicNode>) : SymbolicNode

    @Serializable @SerialName("quotient") data class Quotient(val numerator: SymbolicNode, val denominator: SymbolicNode) : SymbolicNode

    @Serializable @SerialName("power") data class Power(val base: SymbolicNode, val exponent: SymbolicNode) : SymbolicNode

    @Serializable @SerialName("equals") data class Equals(val left: SymbolicNode, val right: SymbolicNode) : SymbolicNode

    @Serializable @SerialName("func") data class Func(val name: SymbolicFunction, val args: List<SymbolicNode>) : SymbolicNode
}

/** Lista cerrada y compartida con el servicio: ampliarla es una decisión de contrato. */
@Serializable
enum class SymbolicFunction {
    @SerialName("sqrt") SQRT,
    @SerialName("sin") SIN,
    @SerialName("cos") COS,
    @SerialName("tan") TAN,
    @SerialName("ln") LN,
    @SerialName("log") LOG,
    @SerialName("exp") EXP,
    @SerialName("abs") ABS,
}

@Serializable
enum class SymbolicCapability {
    @SerialName("simplify") SIMPLIFY,
    @SerialName("isEquivalent") IS_EQUIVALENT,
    @SerialName("solveEquation") SOLVE_EQUATION,
    @SerialName("classifyStep") CLASSIFY_STEP,
}

@Serializable
data class SymbolicRequest(
    /**
     * Sin valor por defecto, a propósito: con `encodeDefaults = false` un campo opcional no viaja,
     * y el servicio rechaza (`contract_version`) cualquier petición que no lo traiga.
     */
    val contractVersion: String,
    val requestId: String,
    val capability: SymbolicCapability,
    val expression: SymbolicNode,
    val after: SymbolicNode? = null,
)

@Serializable
data class SymbolicRejected(val value: String, val reason: String)

@Serializable
data class SymbolicEngine(val name: String = "", val version: String = "")

/**
 * Resultado con todos los campos opcionales: el contrato es uno solo y cada capacidad rellena los
 * suyos. `null` significa "esta capacidad no lo produjo", no "cero" ni "falso".
 */
@Serializable
data class SymbolicResult(
    val supported: Boolean? = null,
    val reason: String? = null,
    val variable: String? = null,
    val solutions: List<String>? = null,
    val rejected: List<SymbolicRejected>? = null,
    val excludedValues: List<String>? = null,
    val domainExcluded: List<String>? = null,
    val equivalent: Boolean? = null,
    val lostSolutions: List<String>? = null,
    val extraneousSolutions: List<String>? = null,
    val verification: String? = null,
    val method: String? = null,
    val expression: String? = null,
    val expanded: String? = null,
    val variables: List<String>? = null,
    val difference: String? = null,

    /**
     * Sólo cuando `status = "invalidInput"`: el servicio describe ahí el rechazo del contrato.
     * `code` es estable (`max_depth`, `var_name`, `unknown_kind`…) y `message` legible.
     */
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class SymbolicResponse(
    val contractVersion: String = "",
    val requestId: String = "",
    val capability: String = "",
    val status: String,
    val result: SymbolicResult? = null,
    val warnings: List<String> = emptyList(),
    val engine: SymbolicEngine = SymbolicEngine(),
)

/** `classDiscriminator = "kind"` es parte del contrato, no un detalle de implementación. */
object SymbolicJson {
    val instance: Json = Json {
        classDiscriminator = "kind"
        explicitNulls = false
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
}

sealed interface SymbolicEncoding {
    data class Ready(val node: SymbolicNode) : SymbolicEncoding
    data class Unsupported(val reason: String) : SymbolicEncoding
}

/**
 * Traduce el AST interno del estudiante al AST del contrato. Un nodo sin representación viaja como
 * `Unsupported` con explicación: la alternativa (enviar una cadena para que Python la analice)
 * rompería justo la restricción que da valor a este contrato.
 */
object SymbolicAstCodec {

    private const val MAX_NODES = 256
    private const val MAX_DEPTH = 32

    fun encode(expression: Expr): SymbolicEncoding {
        val budget = intArrayOf(0)
        return convert(expression, 0, budget)
    }

    private fun convert(expression: Expr, depth: Int, budget: IntArray): SymbolicEncoding {
        budget[0] += 1
        if (budget[0] > MAX_NODES) return SymbolicEncoding.Unsupported("La expresión supera los $MAX_NODES nodos del contrato.")
        if (depth > MAX_DEPTH) return SymbolicEncoding.Unsupported("La expresión supera $MAX_DEPTH niveles de profundidad del contrato.")

        return when (expression) {
            is Expr.Const -> SymbolicEncoding.Ready(SymbolicNode.Const(toContractNumber(expression.value)))
            is Expr.Var -> {
                if (!expression.name.matches(VARIABLE_PATTERN)) {
                    SymbolicEncoding.Unsupported("El nombre '${expression.name}' no cabe en el contrato (identificador corto en minúsculas).")
                } else {
                    SymbolicEncoding.Ready(SymbolicNode.Var(expression.name))
                }
            }

            is Expr.Sum -> {
                val encodedTerms = expression.terms.map { term ->
                    when (val operand = convert(term.operand, depth + 1, budget)) {
                        is SymbolicEncoding.Ready -> if (term.operator == Expr.Sum.Term.Operator.MINUS) {
                            SymbolicEncoding.Ready(SymbolicNode.Neg(operand.node))
                        } else {
                            operand
                        }

                        is SymbolicEncoding.Unsupported -> operand
                    }
                }
                encodeChildren(encodedTerms)?.let { SymbolicEncoding.Ready(SymbolicNode.Sum(it)) }
                    ?: SymbolicEncoding.Unsupported("Algún sumando no tiene representación en el contrato.")
            }

            is Expr.Neg -> when (val operand = convert(expression.operand, depth + 1, budget)) {
                is SymbolicEncoding.Ready -> SymbolicEncoding.Ready(SymbolicNode.Neg(operand.node))
                is SymbolicEncoding.Unsupported -> operand
            }

            is Expr.Product -> encodeChildren(expression.factors.map { convert(it.operand, depth + 1, budget) })
                ?.let { SymbolicEncoding.Ready(SymbolicNode.Product(it)) }
                ?: SymbolicEncoding.Unsupported("Factor sin representación en el contrato.")

            is Expr.Quotient -> encodePair(expression.numerator, expression.denominator, depth, budget) { numerator, denominator ->
                SymbolicNode.Quotient(numerator, denominator)
            }

            is Expr.Power -> encodePair(expression.base, expression.exponent, depth, budget) { base, exponent ->
                SymbolicNode.Power(base, exponent)
            }

            is Expr.Percent -> when (val operand = convert(expression.operand, depth + 1, budget)) {
                is SymbolicEncoding.Ready -> SymbolicEncoding.Ready(
                    SymbolicNode.Quotient(operand.node, SymbolicNode.Const("100")),
                )

                is SymbolicEncoding.Unsupported -> operand
            }

            is Expr.Equals -> encodePair(expression.left, expression.right, depth, budget) { left, right ->
                SymbolicNode.Equals(left, right)
            }
        }
    }

    private fun encodePair(
        left: Expr,
        right: Expr,
        depth: Int,
        budget: IntArray,
        build: (SymbolicNode, SymbolicNode) -> SymbolicNode,
    ): SymbolicEncoding {
        val rawLeft = convert(left, depth + 1, budget)
        val encodedLeft = rawLeft as? SymbolicEncoding.Ready ?: return rawLeft
        val rawRight = convert(right, depth + 1, budget)
        val encodedRight = rawRight as? SymbolicEncoding.Ready ?: return rawRight
        return SymbolicEncoding.Ready(build(encodedLeft.node, encodedRight.node))
    }

    private fun encodeChildren(children: List<SymbolicEncoding>): List<SymbolicNode>? =
        children.map { (it as? SymbolicEncoding.Ready)?.node ?: return null }

    private val VARIABLE_PATTERN = Regex("^[a-z](?:[a-z0-9_]{0,7})$")

    /** Cadena exacta del racional: `4` o `-1/2`. Un float en el contrato perdería la garantía del motor. */
    private fun toContractNumber(value: Rational): String =
        if (value.denominator == BigInteger.ONE) {
            value.numerator.toString()
        } else {
            "${value.numerator}/${value.denominator}"
        }
}
