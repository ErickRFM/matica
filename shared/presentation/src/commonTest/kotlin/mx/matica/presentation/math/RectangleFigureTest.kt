package mx.matica.presentation.math

import mx.matica.exercises.ExerciseGenerator
import mx.matica.exercises.ExerciseTemplates
import mx.matica.exercises.GenerationOutcome
import mx.matica.math.MathEngine
import mx.matica.math.ast.Expr
import mx.matica.math.numeric.Rational
import mx.matica.presentation.exercise.ExerciseSlot
import mx.matica.presentation.exercise.numberLineAround
import mx.matica.presentation.exercise.renderPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La figura geométrica no es un adorno: sólo puede aparecer cuando la ecuación escrita dice un
 * rectángulo. Estas pruebas fijan qué formas se leen, cuáles se rechazan y qué valores exactos
 * salen del arrastre.
 */
class RectangleFigureTest {

    private val engine = MathEngine()
    private val x = "x"

    private fun figureOf(text: String, solution: Long): RectangleFigure? {
        val equals = engine.parseOrNull(text) as? Expr.Equals
        assertNotNull(equals, "El motor no leyó «$text» como ecuación.")
        return rectangleFigureOf(equals, x, Rational.fromLong(solution))
    }

    @Test
    fun elPerimetroEscritoSeLeeComoRectangulo() {
        val figure = assertNotNull(figureOf("2(x + 3) = 14", 4L))
        assertEquals(FigureMeasure.PERIMETER, figure.measure)
        assertEquals(Rational.fromLong(3L), figure.knownSide, "El lado conocido es el sumando del paréntesis.")
        assertEquals(Rational.fromLong(14L), figure.total)
        assertEquals(Rational.fromLong(14L), figure.totalAt(Rational.fromLong(4L)))
        assertEquals("perímetro 14", figure.goalEs)
    }

    @Test
    fun elProductoConConstanteSeLeeComoArea() {
        val figure = assertNotNull(figureOf("2x = 6", 3L))
        assertEquals(FigureMeasure.AREA, figure.measure)
        assertEquals(Rational.fromLong(2L), figure.knownSide)
        assertEquals(Rational.fromLong(6L), figure.total)
        assertEquals("área 6", figure.goalEs)
    }

    @Test
    fun elOrdenDeLosLadosNoCambiaLaLectura() {
        val figure = assertNotNull(figureOf("14 = 2(x + 3)", 4L))
        assertEquals(FigureMeasure.PERIMETER, figure.measure)
        assertEquals(Rational.fromLong(3L), figure.knownSide)
    }

    @Test
    fun unFactorDistintoDeDosNoEsUnPerimetro() {
        assertNull(figureOf("3(x + 2) = 15", 3L), "«3 veces una suma» no es el perímetro de nada que un libro dibuje.")
    }

    @Test
    fun unLadoEscritoComoRestaNoTieneLongitud() {
        assertNull(figureOf("2(x - 3) = 14", 10L), "El lado escribiría x−3, no un valor marcado en la figura.")
    }

    @Test
    fun unaSumaSinProductoNoEsUnaFigura() {
        assertNull(figureOf("x + 5 = 9", 4L))
        assertNull(figureOf("x = 7 - 3(-5) + 4", 22L), "Con la incógnita ya aislada no hay rectángulo que dibujar.")
    }

    @Test
    fun laIncognitaNegativaNoDibujaUnLado() {
        assertNull(figureOf("2(x + 3) = 2", -2L), "Un lado negativo no se puede rotular.")
    }

    @Test
    fun unaEcuacionQueMienteSobreSuTotalNoSeDibuja() {
        // 2(4+3) = 14, no 13: una figura con un total que la propia solución no alcanza mentiría.
        assertNull(figureOf("2(x + 3) = 13", 4L))
    }

    @Test
    fun elAreaSigueExactaConLadosFraccionarios() {
        val figure = RectangleFigure(
            measure = FigureMeasure.AREA,
            variable = x,
            knownSide = Rational.fromLong(2L),
            total = Rational.fromLong(3L),
        )
        assertEquals(Rational.fromLong(3L), figure.totalAt(Rational.of(3L, 2L)), "2 · 3/2 = 3, sin coma flotante.")
        val perimeter = RectangleFigure(
            measure = FigureMeasure.PERIMETER,
            variable = x,
            knownSide = Rational.of(1L, 2L),
            total = Rational.fromLong(5L),
        )
        assertEquals(Rational.fromLong(5L), perimeter.totalAt(Rational.fromLong(2L)), "2 · (2 + 1/2) = 5, en fracciones exactas.")
    }

    @Test
    fun losCandidatosSonLasMarcasPositivasDeLaRectaDelEjercicio() {
        val candidates = figureCandidates(numberLineAround(Rational.fromLong(4L)))
        assertTrue(candidates.isNotEmpty(), "Sin marcas positivas el lado no se puede mover.")
        assertTrue(candidates.all { it > Rational.ZERO }, "Un lado negativo no entra entre los candidatos: $candidates")
        assertEquals(candidates.sorted(), candidates, "Los candidatos viajan ordenados: el arrastre los recorre de izquierda a derecha.")
        assertTrue(Rational.fromLong(4L) in candidates, "La solución del ejercicio tiene que ser alcanzable.")
    }

    @Test
    fun arrastrarDevuelveElCandidatoExactoNuncaUnValorIntermedio() {
        val candidates = figureCandidates(numberLineAround(Rational.fromLong(4L)))
        assertEquals(candidates.first(), figureValueAt(0f, candidates))
        assertEquals(candidates.last(), figureValueAt(1f, candidates))
        assertEquals(candidates.first(), figureValueAt(-2f, candidates), "El dedo fuera del marco se queda en el extremo.")
        assertEquals(candidates.last(), figureValueAt(9f, candidates))
        val middle = figureValueAt(0.5f, candidates)
        assertEquals(middle, figureValueAt(0.48f, candidates), "Un pixel más allá no cambia un valor que ya estaba pegado a su marca.")
        assertNull(figureValueAt(0.5f, emptyList()), "Sin candidatos no hay lectura que dar.")
    }

    @Test
    fun laEscalaRecortaLaDesproporcionPeroNoLosValores() {
        val honesta = figureScales(Rational.fromLong(3L), Rational.fromLong(4L))
        assertEquals(Rational.fromLong(3L), honesta.first)
        assertEquals(Rational.fromLong(4L), honesta.second, "Proporción razonable: se dibuja tal cual.")

        val extremo = figureScales(Rational.fromLong(12L), Rational.fromLong(1L))
        assertEquals(Rational.of(4L, 1L), extremo.first)
        assertEquals(Rational.ONE, extremo.second, "12×1 dibujado a escala real sería una raya: se recorta el dibujo.")

        val invertido = figureScales(Rational.fromLong(1L), Rational.fromLong(12L))
        assertEquals(Rational.ONE, invertido.first)
        assertEquals(Rational.of(4L, 1L), invertido.second)

        val figure = RectangleFigure(
            measure = FigureMeasure.AREA,
            variable = x,
            knownSide = Rational.fromLong(12L),
            total = Rational.fromLong(12L),
        )
        assertEquals(Rational.fromLong(12L), figure.totalAt(Rational.fromLong(1L)), "El recorte no toca la aritmética rotulada.")
    }

    @Test
    fun laPlantillaDePerimetroSiempreDaUnaFigura() {
        val generator = ExerciseGenerator()
        (1L..40L).forEach { seed ->
            val instance = (generator.generate("rectangle-perimeter", seed) as? GenerationOutcome.Ready)?.instance
                ?: return@forEach
            val plan = assertNotNull(renderPlan(instance, engine), "Sin plan para ${instance.equationText}")
            val figure = assertNotNull(plan.figure, "${instance.equationText} tiene que dibujarse como rectángulo.")
            assertTrue(plan.hasGeometry, "La plantilla geométrica sin su sección: ${instance.equationText}")
            assertEquals(FigureMeasure.PERIMETER, figure.measure)
            assertEquals(figure.total, figure.totalAt(plan.solution), "La figura miente sobre su perímetro: ${instance.equationText}")
            assertTrue(figure.knownSide > Rational.ZERO && plan.solution > Rational.ZERO)
        }
    }

    @Test
    fun todaFiguraDeCatalogoCuadraConSuPropiaEcuacion() {
        val generator = ExerciseGenerator()
        var figuras = 0
        ExerciseTemplates.all.forEach { template ->
            (1L..24L).forEach { seed ->
                val instance = (generator.generate(template.id, seed) as? GenerationOutcome.Ready)?.instance
                    ?: return@forEach
                val plan = assertNotNull(renderPlan(instance, engine), "Sin plan para ${instance.equationText}")
                assertEquals(
                    ExerciseSlot.GEOMETRY in plan.slots && plan.figure != null,
                    plan.hasGeometry,
                    "${template.id} seed $seed: la sección no puede contradecir al plan.",
                )
                val figure = plan.figure ?: return@forEach
                figuras += 1
                assertEquals(figure.total, figure.totalAt(plan.solution), "${instance.equationText} no cierra con su figura.")
                assertTrue(figure.knownSide > Rational.ZERO, "Lado conocido positivo: ${instance.equationText}")
                assertTrue(plan.solution > Rational.ZERO, "La solución es un lado: ${instance.equationText}")
                assertEquals(
                    figure.measure,
                    if (instance.equationText.contains("(")) FigureMeasure.PERIMETER else FigureMeasure.AREA,
                    "La lectura depende de la forma escrita: ${instance.equationText}",
                )
            }
        }
        assertTrue(figuras > 0, "Ninguna ecuación del catálogo se leyó como figura: el componente no tendría para qué existir.")
    }
}
