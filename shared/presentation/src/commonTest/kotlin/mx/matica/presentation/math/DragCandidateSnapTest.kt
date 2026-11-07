package mx.matica.presentation.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import mx.matica.math.numeric.Rational
import mx.matica.presentation.exercise.numberLineAround

/**
 * Las tres superficies interactivas —recta, plano y figura— enganchaban el dedo cada una a su
 * manera: la figura medía la distancia en unidades de valor y las otras dos en fracción del ancho.
 * Aquí no se mira el dibujo: se exige que el mismo gesto sobre el mismo intervalo y las mismas marcas
 * devuelva la misma marca exacta por las tres entradas, que es lo que dejó de estar garantizado en
 * cuanto existieron dos implementaciones.
 */
class DragCandidateSnapTest {

    private fun whole(value: Long) = Rational.fromLong(value)

    private val model = NumberLineModel(
        from = whole(-4),
        to = whole(4),
        ticks = (-4L..4L).map { whole(it) },
    )

    private val fractions = listOf(0f, 0.07f, 0.13f, 0.5f, 0.62f, 0.87f, 1f, -1.4f, 2.3f)

    @Test
    fun elMismoGestoDaLaMismaMarcaEnLasTresSuperficies() {
        val candidates = model.orderedTicks
        fractions.forEach { fraction ->
            val recta = valueAt(fraction, model)
            assertEquals(
                recta,
                trackerXAt(fraction, model.from, model.to, candidates),
                "El plano se despegó de la recta arrastrando en $fraction.",
            )
            assertEquals(
                recta,
                figureValueAt(fraction, candidates),
                "La figura se despegó de la recta arrastrando en $fraction.",
            )
        }
    }

    @Test
    fun sinMarcasNingunaSuperficieInventaUnValor() {
        assertNull(trackerXAt(0.5f, whole(-4), whole(4), emptyList()), "Sin marcas el plano no coloca punto.")
        assertNull(figureValueAt(0.5f, emptyList()), "Sin marcas la figura no lee lado.")
    }

    @Test
    fun unaUnicaMarcaEsElUnicoSitioAlQueSePuedeIr() {
        // Caso real: la recta de un ejercicio con solución −3 va de −7 a 1, así que su única marca
        // positiva es 1 y la figura no tiene más lado que ofrecer. Arrastrar no puede inventar otro.
        val candidates = figureCandidates(numberLineAround(whole(-3)))
        assertEquals(listOf(whole(1)), candidates, "Se esperaba una figura con una sola marca posible.")
        assertEquals(whole(1), figureValueAt(0f, candidates))
        assertEquals(whole(1), figureValueAt(1f, candidates))
        assertEquals(whole(1), figureValueAt(0.42f, candidates))
    }
}
