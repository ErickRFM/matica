package mx.matica.presentation.math

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import mx.matica.learning.mastery.MasteryLevel
import mx.matica.learning.skills.DefaultCurriculum
import mx.matica.math.numeric.Rational
import mx.matica.math.skills.SkillId

/**
 * Las claves de los `LaunchedEffect` de revelado deciden si la animación vuelve a empezar. Tienen que
 * ser estables mientras el contenido dibujado no cambie, aunque la pantalla reconstruya la lista o la
 * serie desde cero en cada recomposición.
 */
class RevealKeyTest {

    private val graph = DefaultCurriculum.graph

    private val noEvidence: (SkillId) -> MasteryLevel = { MasteryLevel.NOT_INTRODUCED }

    private fun whole(value: Long) = Rational.fromLong(value)

    private fun lineSeries(slope: Rational): PlotSeries =
        plotSeries(
            points = samplePlot({ x -> x * slope }, Rational.ZERO, whole(2), samples = 5),
            xFrom = Rational.ZERO,
            xTo = whole(2),
            yFrom = Rational.ZERO,
            yTo = whole(4),
        )

    @Test
    fun laClaveDelCaminoNoCambiaCuandoLaMismaRutaSeReconstruye() {
        val first = routeNodes(graph, noEvidence, emptySet())
        val rebuilt = routeNodes(graph, noEvidence, emptySet())
        assertTrue(rebuilt !== first, "la ruta se recicló: la prueba necesita dos instancias distintas")
        assertEquals(first, rebuilt, "dos construcciones de la misma ruta debían ser estructuralmente iguales")
        assertEquals(
            routeRevealKey(first),
            routeRevealKey(rebuilt),
            "reconstruir la misma ruta reinició el trazado del mapa",
        )
    }

    @Test
    fun subirElNivelDeUnNudoNoReiniciaElTrazadoDelMapa() {
        val before = routeNodes(graph, noEvidence, emptySet())
        val moved = before.first().skill
        val after = routeNodes(graph, { skill ->
            if (skill == moved) MasteryLevel.PROFICIENT else MasteryLevel.NOT_INTRODUCED
        }, emptySet())
        assertNotEquals(before, after, "el nivel debía cambiar para que la prueba valga algo")
        assertEquals(
            routeRevealKey(before),
            routeRevealKey(after),
            "el mapa volvió a dibujarse solo porque un nudo subió de dominio",
        )
    }

    @Test
    fun moverUnNudoDeCasillaSiCambiaLaFormaDelCamino() {
        val base = routeNodes(graph, noEvidence, emptySet())
        val shifted = base.mapIndexed { index, node ->
            if (index == 0) node.copy(column = (node.column + 1) % ROUTE_COLUMNS) else node
        }
        assertNotEquals(routeRevealKey(base), routeRevealKey(shifted), "la ruta cambió de forma y el revelado no se enteró")
    }

    @Test
    fun laClaveDelPlanoNoCambiaConOtraInstanciaDeLaMismaCurva() {
        val first = lineSeries(Rational.of(1, 1))
        val rebuilt = lineSeries(Rational.of(1, 1))
        assertEquals(first, rebuilt, "la serie reconstruida debía contener lo mismo")
        assertEquals(
            plotRevealKey(first),
            plotRevealKey(rebuilt),
            "trazar la misma función otra vez reinició la animación de la curva",
        )
    }

    @Test
    fun cambiarDeFuncionSiReiniciaElTrazadoDelPlano() {
        val gentle = lineSeries(Rational.of(1, 1))
        val steep = lineSeries(Rational.of(3, 1))
        assertNotEquals(plotRevealKey(gentle), plotRevealKey(steep), "una función distinta quedó congelada en la curva anterior")
    }

    @Test
    fun elMarcadorDeLaRectaSoloViajaCuandoCambiaSuPosicionObjetivo() {
        val model = NumberLineModel(
            from = whole(-1),
            to = whole(1),
            ticks = listOf(whole(-1), Rational.of(-1, 2), Rational.ZERO, Rational.of(1, 2), whole(1)),
        )
        val rebuilt = NumberLineModel(from = model.from, to = model.to, ticks = model.ticks.toList())
        assertEquals(
            markerPositionOf(Rational.ZERO, model),
            markerPositionOf(Rational.ZERO, rebuilt),
            "otra instancia del mismo modelo movió el marcador",
        )
        val withExtraMark = model.copy(ticks = model.ticks + Rational.of(1, 4))
        assertEquals(
            markerPositionOf(Rational.ZERO, model),
            markerPositionOf(Rational.ZERO, withExtraMark),
            "añadir una marca reinició el viaje del marcador que ya estaba en su sitio",
        )
        assertNotEquals(
            markerPositionOf(Rational.ZERO, model),
            markerPositionOf(Rational.of(1, 2), model),
            "cambiar el valor marcado no cambió el objetivo del marcador",
        )
    }

    @Test
    fun sinTamanoMedidoONivelCeroLaMedicionNoTrazaNada() {
        val nodes = routeNodes(graph, noEvidence, emptySet())
        assertNull(
            routeVisiblePath(ordered = nodes, rows = 2, sizePx = IntSize.Zero, reveal = 0.5f),
            "el camino se midió antes de que el canvas tuviera tamaño",
        )
        assertNull(
            routeVisiblePath(ordered = nodes.take(1), rows = 1, sizePx = IntSize(400, 200), reveal = 1f),
            "con un solo nudo no hay trazo que recorrer",
        )
        assertEquals(
            0,
            plotVisibleSegments(lineSeries(Rational.ONE), IntSize.Zero, 1f).size,
            "el plano trazó segmentos sin tamaño medido",
        )
        assertEquals(
            0,
            plotVisibleSegments(lineSeries(Rational.ONE), IntSize(400, 200), 0f).size,
            "el plano trazó algo con el revelado en cero",
        )
    }

    @Test
    fun elReboteDeLaBarraNoLaEnsanchaMasQueElNumerador() {
        assertEquals(0f, fractionBarExtent(-0.2f), "la barra se dibujó con longitud negativa")
        assertEquals(0.5f, fractionBarExtent(0.5f), "el tramo intermedio debe pintarse tal cual")
        assertEquals(
            1f,
            fractionBarExtent(1.18f),
            "el rebote del muelle ensanchó la barra más allá del numerador que separa",
        )
    }
}
