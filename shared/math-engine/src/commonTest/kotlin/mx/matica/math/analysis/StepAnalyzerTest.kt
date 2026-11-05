package mx.matica.math.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertIs
import mx.matica.math.transform.RuleId

/**
 * Pruebas del analizador de pasos. Cada caso reproduce un error real de estudiante y exige que el
 * motor nombre el error concreto, no un genérico "está mal".
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

    @Test
    fun elEjemploDelPromptAceptaLaDivisionCorrectaEntreDos() {
        val paso = valido("2(x+3)=14", "x+3=7")
        assertEquals(RuleId.DIVIDE_BOTH_SIDES_BY, paso.rule)
        assertContains(paso.skills, mx.matica.math.skills.SkillId.BALANCE_PRINCIPLE)
    }

    @Test
    fun detectaQueEliminoElFactorSinDividirElOtroLado() {
        val error = invalido("2(x+3)=14", "x+3=14")
        assertEquals(StepError.ONE_SIDED_OPERATION, error.error)
        assertContains(error.detail, "lado")
        assertContains(error.repairHint, "dos lados")
    }

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
    fun mueveTerminosConservandoElEquilibrio() {
        assertEquals(RuleId.SUBTRACT_FROM_BOTH_SIDES, valido("2x+6=14", "2x=8").rule)
        assertEquals(RuleId.ADD_TO_BOTH_SIDES, valido("2x-6=14", "2x=20").rule)
        assertEquals(RuleId.SUBTRACT_FROM_BOTH_SIDES, valido("x+5=2x-1", "5=x-1").rule)
    }

    @Test
    fun multiplicaAmbosLadosParaLimpiarDenominadores() {
        val paso = valido("x/2=3", "x=6")
        assertEquals(RuleId.MULTIPLY_BOTH_SIDES_BY, paso.rule)
    }

    @Test
    fun distingueErrorAritmeticoDeJerarquia() {
        val jerarquia = invalido("2 + 3 * 4", "20")
        assertEquals(StepError.ORDER_OF_OPERATIONS, jerarquia.error)

        val aritmetica = invalido("2 + 3 * 4", "24")
        assertEquals(StepError.ARITHMETIC_ERROR, aritmetica.error)

        valido("2 + 3 * 4", "14")
    }

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
        val error = invalido("0x=5", "0=5/0")
        assertEquals(StepError.DIVISION_BY_ZERO, error.error)
    }

    @Test
    fun noAcusaLoQueNoPuedeDemostrar() {
        val fraccionAlgebraica = paso("(x+1)/x = 2", "x+1 = 2")
        assertIs<StepVerdict.Indeterminate>(fraccionAlgebraica)

        // `sen(x)` no es `sen multiplicado por x`: el motor lo rechaza en lugar de reinterpretarlo.
        assertIs<StepVerdict.SyntaxProblem>(paso("sen(x)=1/2", "x=pi/6"))
    }

    @Test
    fun informaProblemasDeSintaxisSeparadosDeErroresMatematicos() {
        val verdict = paso("2(x+3)=14", "2(x+3=14")
        assertIs<StepVerdict.SyntaxProblem>(verdict)
        kotlin.test.assertTrue(verdict.suggestion.isNotBlank())
    }

    @Test
    fun validaReescriturasEquivalentesDeFracciones() {
        valido("2/4", "1/2")
        valido("0.5", "1/2")
        valido("1/3 + 1/6", "1/2")

        // Sumar numeradores y denominadores da exactamente lo que arrojaría leer de izquierda a
        // derecha: (1/3 + 1)/6 = 2/9, así que la firma detectada es de jerarquía.
        assertEquals(StepError.ORDER_OF_OPERATIONS, invalido("1/3 + 1/6", "2/9").error)

        val distraccion = invalido("2 + 3 * 4", "15")
        assertEquals(StepError.ARITHMETIC_ERROR, distraccion.error)
    }

    @Test
    fun registraHabilidadesUsadasEnCadaPaso() {
        val paso = valido("2(x+3)=14", "2x+6=14")
        assertContains(paso.skills, mx.matica.math.skills.SkillId.DISTRIBUTIVE_PROPERTY)
    }
}
