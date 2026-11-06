package mx.matica.presentation.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mx.matica.design.a11y.Contrast
import mx.matica.design.theme.MaticaDarkColorScheme
import mx.matica.design.theme.MaticaLightColorScheme
import mx.matica.design.theme.MaticaSemanticColors
import mx.matica.learning.mastery.MasteryLevel
import mx.matica.learning.skills.DefaultCurriculum
import mx.matica.math.skills.SkillId

class RouteMapTest {

    private val graph = DefaultCurriculum.graph

    private val noEvidence: (SkillId) -> MasteryLevel = { MasteryLevel.NOT_INTRODUCED }

    @Test
    fun hayUnNudoPorHabilidadDelGrafo() {
        val nodes = routeNodes(graph, noEvidence, emptySet())
        assertEquals(graph.order().size, nodes.size)
        assertEquals(graph.order().toSet(), nodes.map { it.skill }.toSet())
    }

    @Test
    fun sinEvidenciaSoloSeAbrenLasRaices() {
        val nodes = routeNodes(graph, noEvidence, emptySet())
        nodes.forEach { node ->
            assertEquals(graph.isRoot(node.skill), node.unlocked, "en ${node.label}")
        }
    }

    @Test
    fun dominarUnPrerrequisitoAbreSuDependiente() {
        val root = graph.order().first { graph.isRoot(it) && graph.dependentsOf(it).isNotEmpty() }
        val dependent = graph.dependentsOf(root).first()
        val nodes = routeNodes(graph, noEvidence, setOf(root))
        assertTrue(nodes.first { it.skill == dependent }.unlocked)
    }

    @Test
    fun laSugerenciaEsUnicaYPuedeEmpezarse() {
        val nodes = routeNodes(graph, noEvidence, emptySet())
        assertEquals(1, nodes.count { it.suggested })
        assertTrue(nodes.first { it.suggested }.unlocked)
    }

    @Test
    fun laSugerenciaNuncaSenalaAlgoQueNoSePuedeAbrir() {
        val roots = graph.order().filter { graph.isRoot(it) }
        val abierta = roots.first()
        val nodes = routeNodes(graph, noEvidence, emptySet(), openable = { it == abierta })
        assertEquals(1, nodes.count { it.suggested })
        assertEquals(abierta, nodes.first { it.suggested }.skill)
        assertEquals(0, routeNodes(graph, noEvidence, emptySet(), openable = { false }).count { it.suggested })
    }

    @Test
    fun todoDominadoNoSugiereNada() {
        val mastered = graph.order().toSet()
        val fluent: (Any) -> MasteryLevel = { MasteryLevel.FLUENT }
        assertEquals(0, routeNodes(graph, fluent, mastered).count { it.suggested })
    }

    @Test
    fun lasFilasImparesCaminanHaciaLaIzquierda() {
        val nodes = routeNodes(graph, noEvidence, emptySet())
        val firstRow = nodes.filter { it.row == 0 }.map { it.column }
        val secondRow = nodes.filter { it.row == 1 }.map { it.column }
        assertEquals(firstRow, firstRow.sorted())
        assertEquals(secondRow, secondRow.sortedDescending())
    }

    @Test
    fun todosLosNudosCabenEnLaRejilla() {
        routeNodes(graph, noEvidence, emptySet()).forEach { node ->
            assertTrue(node.column in 0 until ROUTE_COLUMNS, "columna ${node.column}")
            assertTrue(node.row >= 0)
        }
    }

    @Test
    fun cadaNudoSeNombraConLaEtiquetaDeLaHabilidad() {
        routeNodes(graph, noEvidence, emptySet()).forEach { node ->
            assertEquals(node.skill.labelEs, node.label)
            assertTrue(node.label.isNotBlank())
        }
    }

    @Test
    fun losCuatroNivelesTienenParMedidoEnAmbosTemas() {
        listOf(
            MaticaLightColorScheme to MaticaSemanticColors.Light,
            MaticaDarkColorScheme to MaticaSemanticColors.Dark,
        ).forEach { (scheme, semantic) ->
            MaticaLevelPalette.levels().forEach { level ->
                val pair = MaticaLevelPalette.pairOf(level, scheme, semantic)
                assertTrue(
                    Contrast.meetsAa(pair.foreground, pair.background),
                    "nivel $level con ${pair.usage}: ${Contrast.ratio(pair.foreground, pair.background)}",
                )
            }
        }
    }
}
