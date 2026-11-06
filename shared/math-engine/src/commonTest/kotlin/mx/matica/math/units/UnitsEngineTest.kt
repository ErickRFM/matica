package mx.matica.math.units

import mx.matica.math.MathEngine
import mx.matica.math.QuantityOutcome
import mx.matica.math.analysis.StepVerdict
import mx.matica.math.ast.Expr
import mx.matica.math.eval.EvalOutcome
import mx.matica.math.numeric.Rational
import mx.matica.math.parser.ParseOutcome
import mx.matica.math.solve.SolveOutcome
import mx.matica.math.verify.VerificationOutcome
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * B1-09 cerrada: `km` y `m` ya no se leen como incógnitas. Cada aserción viene de una corrida previa
 * del motor sobre el texto citado, no de lo que el autor supuso que debería dar.
 */
class UnitsEngineTest {

    private val engine = MathEngine()

    private fun expression(text: String): Expr =
        requireNotNull((engine.parse(text) as? ParseOutcome.Success)?.expression) { "'$text' no leyó: $text" }

    private fun valor(text: String): Rational =
        when (val evaluated = engine.evaluate(text)) {
            is EvalOutcome.Value -> evaluated.value
            else -> error("'$text' no dejó un valor: $evaluated")
        }

    @Test
    fun tresKilometresMasDoscientosMetrosSonTresMilDoscientosMetros() {
        val leido = assertIs<Expr.Sum>(expression("3 km + 200 m"))
        val primero = assertIs<Expr.Quantity>(leido.terms[0].operand)
        assertEquals("km", primero.unit.symbol)
        assertEquals(Rational.fromLong(3), assertIs<Expr.Const>(primero.operand).value)
        assertEquals(Rational.fromLong(3_200), valor("3 km + 200 m"), "Se reduce a la unidad base antes de sumar.")
        assertEquals("3 km + 200 m", engine.render(leido))
    }

    @Test
    fun laUnidadEnvuelveAlTerminoCompleto() {
        // `1/2 m` es medio metro. Si la unidad sólo se pegara al número pegado, leería `1/(2 m)`.
        val leido = assertIs<Expr.Quantity>(expression("1/2 m"))
        assertEquals("m", leido.unit.symbol)
        assertIs<Expr.Quotient>(leido.operand)
        assertEquals(Rational.of(1, 2), valor("1/2 m"))
        assertEquals(Rational.of(1, 2), valor("0.5 kg"), "El kilogramo es la base de la masa.")
    }

    @Test
    fun elTextoVuelveALeerElMismoArbol() {
        listOf("3 km + 200 m", "1/2 m", "2x km", "3 km + x = 7 km", "x km + 2 km = 5 km", "5 m - 20 cm").forEach { texto ->
            val original = expression(texto)
            val releido = expression(engine.render(original))
            assertEquals(original, releido, "Renderizar y releer cambió el significado de '$texto'.")
        }
    }

    @Test
    fun laIncognitaDeUnaEcuacionConUnidadesSeResuelveYSeVerifica() {
        val solved = assertIs<SolveOutcome.Unique>(engine.solve("3 km + x = 7 km"))
        assertEquals("x", solved.variable)
        assertEquals(Rational.fromLong(4_000), solved.value, "La respuesta sale en la unidad base: metros.")
        assertIs<VerificationOutcome.Satisfies>(
            engine.verifySolution("3 km + x = 7 km", "x", Rational.fromLong(4_000)),
            "Sustituir la respuesta correcta no puede quedar fuera del dominio.",
        )
        assertIs<VerificationOutcome.DoesNotSatisfy>(engine.verifySolution("3 km + x = 7 km", "x", Rational.fromLong(4_001)))
    }

    @Test
    fun laUnidadDeLaIncognitaPuedeIrEscritaEnElTermino() {
        val solved = assertIs<SolveOutcome.Unique>(engine.solve("x km + 2 km = 5 km"))
        assertEquals(Rational.fromLong(3), solved.value, "Aquí la incógnita ya está en kilómetros: la respuesta es 3.")
        assertIs<VerificationOutcome.Satisfies>(engine.verifySolution("x km + 2 km = 5 km", "x", Rational.fromLong(3)))
    }

    @Test
    fun unNumeroSueltoSeLeeEnLaUnidadBaseDeLaFamilia() {
        // Convención documentada: `3 km + 500` suma 500 metros. Es la lectura que coincide con lo que
        // el estudiante quiere decir y la única que deja verificar las respuestas de las ecuaciones.
        assertEquals(Rational.fromLong(3_500), valor("3 km + 500"))
        assertIs<SolveOutcome.Identity>(engine.solve("3 km + 200 = 3200 m"))
    }

    @Test
    fun mezclarFamiliasSeRechazaConUnMotivoLegible() {
        val evaluado = assertIs<EvalOutcome.Undefined>(engine.evaluate("3 km + 2 s"))
        assertContains(evaluado.reason, "mezcla familias distintas")
        assertContains(evaluado.reason, "longitud")
        assertContains(evaluado.reason, "tiempo")

        val resuelto = assertIs<SolveOutcome.Blocked>(engine.solve("3 km = 2 s"))
        assertContains(resuelto.reason, "mezcla familias distintas", false, "Una ecuación de dos familias no tiene solución que el motor pueda afirmar.")
    }

    @Test
    fun multiplicarUnidadesNoSeConfundeConUnaIncognita() {
        val fallo = assertIs<ParseOutcome.Failure>(engine.parse("2 m * 3 m"))
        assertContains(fallo.reason, "va al final del término")
        assertTrue(fallo.suggestion.isNotBlank())
        // `3 km/2 h` sería una velocidad: unidad compuesta, fuera de la tabla.
        assertIs<ParseOutcome.Failure>(engine.parse("3 km/2 h"))
    }

    @Test
    fun lasUnidadesElevadasSeRechazanALaVista() {
        val fallo = assertIs<ParseOutcome.Failure>(engine.parse("5 m^2"))
        assertContains(fallo.reason, "unidades elevadas")
    }

    @Test
    fun convertirEscribeElResultadoEnLaUnidadMasFinaPresente() {
        val metros = assertIs<QuantityOutcome.Ready>(engine.convert("3 km + 200 m"))
        assertEquals(Rational.fromLong(3_200), metros.value)
        assertEquals("m", metros.unit.symbol, "3.2 km se lee peor en pantalla que 3200 m.")

        val gramos = assertIs<QuantityOutcome.Ready>(engine.convert("2 kg + 500 g"))
        assertEquals(Rational.fromLong(2_500), gramos.value)
        assertEquals("g", gramos.unit.symbol)
    }

    @Test
    fun convertirRespetaLaUnidadPedida() {
        val kilometros = assertIs<QuantityOutcome.Ready>(engine.convert("3 km + 200 m", "km"))
        assertEquals(Rational.of(16, 5), kilometros.value)
        val centimetros = assertIs<QuantityOutcome.Ready>(engine.convert("3 km + 200 m", "cm"))
        assertEquals(Rational.fromLong(320_000), centimetros.value)
    }

    @Test
    fun convertirRechazaLoQueNoPuedeDecir() {
        assertContains(
            assertIs<QuantityOutcome.Rejected>(engine.convert("3 km + 200 m", "s")).reason,
            "longitud",
        )
        assertContains(assertIs<QuantityOutcome.Rejected>(engine.convert("3 km", "xyz")).reason, "catálogo")
        assertContains(assertIs<QuantityOutcome.Rejected>(engine.convert("3 + 2")).reason, "no lleva unidades")
        assertIs<QuantityOutcome.Rejected>(engine.convert("2x km"))
    }

    @Test
    fun elPasoDeConversionSeApruebaYCualNoCierraNo() {
        val conversion = assertIs<StepVerdict.Valid>(engine.checkStep("3 km = 3000 m", "km = 1000 m"))
        assertTrue(conversion.skills.isNotEmpty(), "Un paso válido debe poder nombrar la regla que lo sostiene.")

        val mal = engine.checkStep("3 km = 3000 m", "3000 km = 3 m")
        assertTrue(mal !is StepVerdict.Valid, "'3000 km = 3 m' no equivale a la línea anterior.")

        assertIs<StepVerdict.Valid>(engine.checkStep("3 km + 200 m = 3200 m", "3200 m = 3200 m"))
        assertIs<StepVerdict.Invalid>(engine.checkStep("3 km + 200 m = 3200 m", "3 km + 200 m = 3200 km"))
    }

    @Test
    fun unaUnidadSolaValeUnaVezSuMagnitud() {
        // `km = 1000 m` es como un libro escribe una regla de conversión.
        val leido = assertIs<Expr.Equals>(expression("km = 1000 m"))
        val uno = assertIs<Expr.Quantity>(leido.left)
        assertEquals(Rational.ONE, assertIs<Expr.Const>(uno.operand).value)
        assertEquals("1 km = 1000 m", engine.render(leido), "El uno queda explícito al reescribir.")
        assertIs<SolveOutcome.Identity>(engine.solve("km = 1000 m"))
    }

    @Test
    fun lasUnidadesConvivenConLasFraccionesYLosPorcentajes() {
        assertEquals(Rational.fromLong(1_000), valor("50% 2 km"), "El 50% de 2 km son 1000 m.")
        assertEquals(Rational.fromLong(1_100), valor("1 km + 1 hm"))
        assertEquals(Rational.fromLong(750), valor("1/2 km + 250 m"))
    }
}
