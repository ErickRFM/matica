package mx.matica.math.contract

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertIs
import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ExprParser
import mx.matica.math.parser.ParseOutcome

/**
 * Pruebas del codec AST -> contrato. Son offline y sin red: lo que se juega aquí es que el viaje
 * nunca lleve texto interpretable por Python y que un AST imposible de representar se declare en
 * lugar de degradarse.
 */
class SymbolicAstCodecTest {

    private fun expr(text: String): Expr = (ExprParser(text).parse() as ParseOutcome.Success).expression

    private fun ready(text: String): SymbolicNode = encode(expr(text))

    private fun encode(expression: Expr): SymbolicNode =
        (SymbolicAstCodec.encode(expression) as SymbolicEncoding.Ready).node

    @Test
    fun `una resta viaja como neg, conservando el signo explicito del estudiante`() {
        val node = assertIs<SymbolicNode.Equals>(ready("x - 2 = 4"))
        val left = assertIs<SymbolicNode.Sum>(node.left)

        assertEquals(2, left.terms.size)
        assertEquals(SymbolicNode.Var("x"), left.terms[0])
        assertEquals(SymbolicNode.Neg(SymbolicNode.Const("2")), left.terms[1])
        assertEquals(SymbolicNode.Const("4"), node.right)
    }

    @Test
    fun `las constantes viajan como cadenas exactas, nunca como flotantes`() {
        // El AST conserva cómo escribió el estudiante: "-1/2" llega como negativo de un cociente.
        assertEquals(SymbolicNode.Quotient(SymbolicNode.Neg(SymbolicNode.Const("1")), SymbolicNode.Const("2")), ready("-1/2"))
        assertEquals(SymbolicNode.Const("-1/2"), encode(Expr.Const(Rational.of(-1, 2))))
        assertEquals(SymbolicNode.Const("3"), encode(Expr.Const(Rational.fromLong(3))))
        val quotient = assertIs<SymbolicNode.Quotient>(ready("x/3"))

        assertEquals(SymbolicNode.Var("x"), quotient.numerator)
        assertEquals(SymbolicNode.Const("3"), quotient.denominator)
    }

    @Test
    fun `un porcentaje se representa como fraccion sobre cien`() {
        assertEquals(SymbolicNode.Quotient(SymbolicNode.Const("25"), SymbolicNode.Const("100")), ready("25%"))
    }

    @Test
    fun `un producto con suma se conserva como producto de suma`() {
        val product = assertIs<SymbolicNode.Product>(ready("2(x+3)"))

        assertEquals(listOf(SymbolicNode.Const("2"), SymbolicNode.Sum(listOf(SymbolicNode.Var("x"), SymbolicNode.Const("3")))), product.factors)
    }

    @Test
    fun `una potencia conserva base y exponente`() {
        val power = assertIs<SymbolicNode.Power>(ready("x^2"))

        assertEquals(SymbolicNode.Var("x"), power.base)
        assertEquals(SymbolicNode.Const("2"), power.exponent)
    }

    @Test
    fun `un nombre de variable fuera del contrato se declara, no se recorta`() {
        val encoding = SymbolicAstCodec.encode(Expr.Equals(Expr.Var("variable_muy_larga"), Expr.Const(Rational.ONE)))

        val unsupported = assertIs<SymbolicEncoding.Unsupported>(encoding)
        assertTrue("contrato" in unsupported.reason, unsupported.reason)
    }

    @Test
    fun `la profundidad maxima del contrato se respeta`() {
        var expression: Expr = Expr.Const(Rational.ONE)
        repeat(40) { expression = Expr.Neg(expression) }

        val encoding = SymbolicAstCodec.encode(expression)

        assertIs<SymbolicEncoding.Unsupported>(encoding)
    }

    @Test
    fun `el presupuesto de nodos evita un AST absurdo`() {
        val terms = List(300) { Expr.Sum.Term(Expr.Sum.Term.Operator.PLUS, Expr.Const(Rational.ONE)) }

        val encoding = SymbolicAstCodec.encode(Expr.Sum(terms))

        assertIs<SymbolicEncoding.Unsupported>(encoding)
    }

    @Test
    fun `el json del viaje usa el discriminador kind del contrato`() {
        val json = SymbolicJson.instance.encodeToString(SymbolicNode.serializer(), ready("x - 2 = 4"))

        assertTrue("\"kind\":\"equals\"" in json, json)
        assertTrue("\"kind\":\"neg\"" in json, json)
        assertTrue("null" !in json, "el viaje llevó null explícito: $json")
    }

    @Test
    fun `la peticion siempre lleva la version del contrato`() {
        val request = SymbolicRequest(
            contractVersion = SYMBOLIC_CONTRACT_VERSION,
            requestId = "r1",
            capability = SymbolicCapability.CLASSIFY_STEP,
            expression = ready("x = 1"),
            after = ready("x = 1"),
        )

        val json = SymbolicJson.instance.encodeToString(SymbolicRequest.serializer(), request)

        assertTrue("\"contractVersion\":\"$SYMBOLIC_CONTRACT_VERSION\"" in json, json)
        assertTrue("\"capability\":\"classifyStep\"" in json, json)
        // `after` nulo no debe aparecer: el servicio exige el campo solo cuando la capacidad lo necesita.
        assertTrue("null" !in json, json)
    }

    @Test
    fun `un nodo fuera de la lista cerrada no se deserializa`() {
        assertFailsWith<SerializationException> {
            SymbolicJson.instance.decodeFromString(SymbolicNode.serializer(), """{"kind":"tensor","args":[]}""")
        }
    }
}
