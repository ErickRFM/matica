package mx.matica.math.analysis

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.skills.SkillId
import mx.matica.math.transform.RuleId

/**
 * Pruebas del analizador de pasos. Cada caso reproduce un error real de estudiante y exige que el
 * motor nombre el error concreto, no un genérico "está mal".
 *
 * Dos propiedades vigiladas: `Valid` solo cuando la equivalencia queda demostrada, e
 * `Indeterminate` cuando el motor no puede demostrar nada. Acusar de error un paso que podría ser
 * correcto es igual de falso que aprobarlo.
 */
class StepAnalyzerTest {

    private val analyzer = StepAnalyzer()

    private fun paso(before: String, after: String): StepVerdict = analyzer.analyze(before, after)

    private fun invalido(before: String, after: String): StepVerdict.Invalid {
        val verdict = paso(before, after)
        assertIs<StepVerdict.Invalid>(verdict, "Se esperaba un paso inválido: $before -> $after, vino $verdict")
        return verdict
    }

    private fun valido(before: String, after: String): StepVerdict.Valid {
        val verdict = paso(before, after)
        assertIs<StepVerdict.Valid>(verdict, "Se esperaba un paso válido: $before -> $after, vino $verdict")
        return verdict
    }

    private fun indeterminado(before: String, after: String): StepVerdict.Indeterminate {
        val verdict = paso(before, after)
        assertIs<StepVerdict.Indeterminate>(verdict, "Se esperaba un veredicto indeterminado: $before -> $after, vino $verdict")
        assertTrue(verdict.reason.isNotBlank(), "Indeterminate debe explicar por qué no pudo demostrar nada.")
        return verdict
    }

    // ------------------------------------------------------------- equilibrio y reglas

    @Test
    fun elEjemploDelPromptAceptaLaDivisionCorrectaEntreDos() {
        val paso = valido("2(x+3)=14", "x+3=7")
        assertEquals(RuleId.DIVIDE_BOTH_SIDES_BY, paso.rule)
        assertContains(paso.skills, SkillId.BALANCE_PRINCIPLE)
    }

    @Test
    fun detectaQueEliminoElFactorSinDividirElOtroLado() {
        val error = invalido("2(x+3)=14", "x+3=14")
        assertEquals(StepError.ONE_SIDED_OPERATION, error.error)
        assertContains(error.detail, "lado")
        assertContains(error.repairHint, "dos lados")
    }

    @Test
    fun mueveTerminosConservandoElEquilibrio() {
        assertEquals(RuleId.SUBTRACT_FROM_BOTH_SIDES, valido("2x+6=14", "2x=8").rule)
        assertEquals(RuleId.ADD_TO_BOTH_SIDES, valido("2x-6=14", "2x=20").rule)
        assertEquals(RuleId.SUBTRACT_FROM_BOTH_SIDES, valido("x+5=2x-1", "5=x-1").rule)
        assertEquals(RuleId.SUBTRACT_FROM_BOTH_SIDES, valido("3x + 2 = 3x + 5", "2 = 5").rule)
    }

    @Test
    fun multiplicaAmbosLadosParaLimpiarDenominadores() {
        assertEquals(RuleId.MULTIPLY_BOTH_SIDES_BY, valido("x/2=3", "x=6").rule)
        assertEquals(RuleId.MULTIPLY_BOTH_SIDES_BY, valido("(x+2)/4 = 1", "x+2 = 4").rule)
    }

    @Test
    fun intercambiaLosLadosComoReescrituraEquivalente() {
        // `5 = x` no es "multiplicar ambos lados": atribuir esa regla fingiría una operación no hecha.
        assertEquals(RuleId.EQUIVALENT_REWRITE, valido("5 = x", "x = 5").rule)
        assertEquals(RuleId.EQUIVALENT_REWRITE, valido("x + 1 = 1 + x", "1 + x = x + 1").rule)
    }

    @Test
    fun resuelveCasosFronteraDeCeroYSinSolucion() {
        assertEquals(RuleId.DIVIDE_BOTH_SIDES_BY, valido("2x=0", "x=0").rule)
        assertEquals(RuleId.SUBTRACT_FROM_BOTH_SIDES, valido("x^2 = x", "x^2 - x = 0").rule)

        // `0x = 5` queda en `0 = 5`: es una línea equivalente, no un paso mal equilibrado. En cambio
        // `x = 5` solo tocó el lado izquierdo.
        assertEquals(RuleId.EQUIVALENT_REWRITE, valido("0x=5", "0=5").rule)
        assertEquals(StepError.ONE_SIDED_OPERATION, invalido("0x=5", "x=5").error)
    }

    // ------------------------------------------------------------- errores típicos

    @Test
    fun reconoceDistribuirBienYMal() {
        assertEquals(RuleId.DISTRIBUTE, valido("2(x+3)=14", "2x+6=14").rule)

        val malDistribuido = invalido("2(x+3)=14", "2x+3=14")
        assertEquals(StepError.DISTRIBUTION_ERROR, malDistribuido.error)
        assertContains(malDistribuido.repairHint, "2x+6")
    }

    @Test
    fun reconoceQuitarParentesisConSignoNegativo() {
        assertEquals(StepError.SIGN_ERROR, invalido("-(x-2)=5", "-x-2=5").error)
        valido("-(x-2)=5", "-x+2=5")
    }

    @Test
    fun distingueErrorAritmeticoDeJerarquia() {
        assertEquals(StepError.ORDER_OF_OPERATIONS, invalido("2 + 3 * 4", "20").error)
        assertEquals(StepError.ARITHMETIC_ERROR, invalido("2 + 3 * 4", "24").error)
        assertEquals(StepError.ARITHMETIC_ERROR, invalido("2 + 3 * 4", "15").error)
        valido("2 + 3 * 4", "14")
    }

    @Test
    fun reunionDeTerminosSemejantesSumaCoeficientesNoProporcionalidad() {
        valido("2x + 3x", "5x")

        // `6x` es proporcional a `5x`, pero dos expresiones sueltas no son una ecuación: aceptar la
        // proporcionalidad aquí aprobaba `2x + 3x -> 6x` como paso correcto.
        val error = invalido("2x + 3x", "6x")
        assertEquals(StepError.ARITHMETIC_ERROR, error.error)
        assertContains(error.detail, "5")
    }

    @Test
    fun validaReescriturasEquivalentesDeFracciones() {
        valido("2/4", "1/2")
        valido("0.5", "1/2")
        valido("1/3 + 1/6", "1/2")

        // Sumar numeradores y denominadores da exactamente lo que arrojaría leer de izquierda a
        // derecha: (1/3 + 1)/6 = 2/9, así que la firma detectada es de jerarquía.
        assertEquals(StepError.ORDER_OF_OPERATIONS, invalido("1/3 + 1/6", "2/9").error)
        assertEquals(StepError.ARITHMETIC_ERROR, invalido("1/2 + 1/3", "2/5").error)
    }

    @Test
    fun marcaRedondeoPresentadoComoIgualdadExacta() {
        val error = invalido("1/3", "0.33")
        assertEquals(StepError.ARITHMETIC_ERROR, error.error)
        assertContains(error.detail, "1/3")

        invalido("0.33", "1/3")
        valido("0.1 + 0.2", "0.3")
    }

    @Test
    fun potenciasSoloApruebaLoQuePuedeExpandir() {
        valido("x^2 * x^3", "x^5")

        // Confundir "multiplicar exponentes" con "sumarlos" deja `x^6`; el motor no puede atribuirlo
        // a una firma típica, así que admite su límite en lugar de adivinar la culpa.
        indeterminado("x^2 * x^3", "x^6")
        indeterminado("(x^2)^3", "x^5")
    }

    // ------------------------------------------------------------- soluciones y dominio

    @Test
    fun marcaPasoQueDescartaUnaSolucion() {
        val error = invalido("x^2=x", "x=1")
        assertEquals(StepError.LOST_SOLUTION, error.error)
        assertContains(error.detail, "x = 0")
    }

    @Test
    fun marcaPasoQueIntroduceUnaSolucionExtrana() {
        val error = invalido("x-1=3", "x(x-1)=3x")
        assertEquals(StepError.ADDED_SOLUTION, error.error)
        assertContains(error.repairHint, "original")
    }

    @Test
    fun detectaDivisionEntreCeroEnUnPaso() {
        assertEquals(StepError.DIVISION_BY_ZERO, invalido("0x=5", "0=5/0").error)
    }

    @Test
    fun noAcusaLoQueNoPuedeDemostrar() {
        // Extraer una raíz de una cuadrática está fuera de la validación lineal offline: marcar
        // `x^2 = 4 -> x = 2` como operación desbalanceada sería una acusación falsa.
        indeterminado("x^2=4", "x=2")
        indeterminado("x^2=4", "x=-2")

        val fraccionAlgebraica = indeterminado("(x+1)/x = 2", "x+1 = 2")
        assertContains(fraccionAlgebraica.reason, "fracción algebraica")

        // `sen(x)` no es `sen` multiplicado por `x`: el parser lo rechaza en vez de reinterpretarlo.
        assertIs<StepVerdict.SyntaxProblem>(paso("sen(x)=1/2", "x=pi/6"))
    }

    @Test
    fun funcionesYConstantesTrascendentesQuedanFueraDelMotorOffline() {
        indeterminado("3.14", "pi")
        indeterminado("2/x = 4", "2 = 4x")
        indeterminado("x/0 = 1", "x = 0")
        assertIs<StepVerdict.SyntaxProblem>(paso("sqrt(x)=4", "x=16"))
    }

    @Test
    fun expresionesEquivalentesBajoRestriccionesDistintasNoSeAprueban() {
        // (x²−1)/(x−1) coincide con x+1 salvo en x = 1: dominios distintos, así que el motor offline
        // no puede afirmar equivalencia entre ellas.
        indeterminado("(x^2-1)/(x-1)", "x+1")
        indeterminado("(x^2-1)/(x-1) = x+1", "x^2-1 = (x+1)(x-1)")
    }

    @Test
    fun lasUnidadesSeValidanPorDimensionAdemasDePorAlgebra() {
        // B1-09 estaba limitado a leer `km` y `m` como incógnitas. Ahora son unidades: el paso de
        // conversión se aprueba porque 3 km y 3000 m valen lo mismo en la unidad base, y la variante
        // invertida se rechaza porque 3000 km no son 3 m.
        valido("3 km = 3000 m", "km = 1000 m")
        assertFalse(paso("3 km = 3000 m", "3000 km = 3 m") is StepVerdict.Valid)
        // Mezclar familias no es un error aritmético demostrable: el motor admite que no puede.
        val mezcladas = indeterminado("3 km + 2 s = 5", "3 km + 2 s = 3002")
        assertContains(mezcladas.reason, "mezcla familias distintas")
    }

    // ------------------------------------------------------------- sintaxis y habilidades

    @Test
    fun informaProblemasDeSintaxisSeparadosDeErroresMatematicos() {
        val verdict = paso("2(x+3)=14", "2(x+3=14")
        assertIs<StepVerdict.SyntaxProblem>(verdict)
        assertTrue(verdict.suggestion.isNotBlank())
        assertTrue(verdict.reason.isNotBlank())
    }

    @Test
    fun unaLineaSinIgualFrenteAUnaConIgualNoEsComparable() {
        val verdict = paso("2x + 3x", "x = 5")
        assertIs<StepVerdict.Indeterminate>(verdict)
        assertContains(verdict.reason, "=")
    }

    @Test
    fun registraHabilidadesUsadasEnCadaPaso() {
        assertContains(valido("2(x+3)=14", "2x+6=14").skills, SkillId.DISTRIBUTIVE_PROPERTY)
        assertContains(valido("3x - 3 = 2x + 4", "x = 7").skills, SkillId.COMBINING_LIKE_TERMS)
        assertContains(valido("2(x+3)=14", "x+3=7").skills, SkillId.ISOLATING_VARIABLE)
    }

    @Test
    fun expandirYAnularUnaIdentidadPolinomica() {
        assertEquals(RuleId.DISTRIBUTE, valido("x(x+1) = x^2+x", "0=0").rule)
        assertEquals(RuleId.DISTRIBUTE, valido("3(x-1) = 2x+4", "3x-3 = 2x+4").rule)
    }
}
