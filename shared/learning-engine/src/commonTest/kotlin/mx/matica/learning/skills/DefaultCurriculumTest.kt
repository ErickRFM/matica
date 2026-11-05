package mx.matica.learning.skills

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import mx.matica.math.skills.SkillId
import mx.matica.math.skills.SkillId.BALANCE_PRINCIPLE
import mx.matica.math.skills.SkillId.COMBINING_LIKE_TERMS
import mx.matica.math.skills.SkillId.DISTRIBUTIVE_PROPERTY
import mx.matica.math.skills.SkillId.DOMAIN_RESTRICTIONS
import mx.matica.math.skills.SkillId.INTEGER_OPERATIONS
import mx.matica.math.skills.SkillId.ISOLATING_VARIABLE
import mx.matica.math.skills.SkillId.PERCENTAGE_REASONING
import mx.matica.math.skills.SkillId.SOLUTION_VERIFICATION

/**
 * El currículo por defecto es datos revisables, no una lista escondida: estas pruebas obligan a que
 * cubra cada habilidad que el motor sabe emitir y a que las aristas tengan sentido matemático.
 */
class DefaultCurriculumTest {

    private val graph = DefaultCurriculum.graph

    @Test
    fun cubreTodasLasHabilidadesDelMotor() {
        assertEquals(emptySet(), DefaultCurriculum.uncoveredSkills, "Habilidades del motor sin nodo en el currículo.")
        assertEquals(SkillId.entries.size, graph.skills.size)
    }

    @Test
    fun esAciclicoYOrdenable() {
        val order = graph.order()
        assertEquals(SkillId.entries.size, order.size)
        order.forEachIndexed { index, skill ->
            graph.prerequisitesOf(skill).forEach { prerequisite ->
                assertTrue(order.indexOf(prerequisite) < index, "$prerequisite debe preceder a $skill.")
            }
        }
    }

    @Test
    fun laUnicaRaizEsOperarConEnteros() {
        assertEquals(listOf(INTEGER_OPERATIONS), graph.skills.filter { graph.isRoot(it) })
    }

    @Test
    fun elEquilibrioExigeOperarConExpresionesAntes() {
        assertContains(graph.prerequisitesOf(BALANCE_PRINCIPLE), DISTRIBUTIVE_PROPERTY)
        assertContains(graph.prerequisitesOf(BALANCE_PRINCIPLE), COMBINING_LIKE_TERMS)
    }

    @Test
    fun lasRestriccionesDeDominioLleganDespuesDelDespeje() {
        assertContains(graph.transitivePrerequisites(DOMAIN_RESTRICTIONS), ISOLATING_VARIABLE)
        assertContains(graph.transitivePrerequisites(DOMAIN_RESTRICTIONS), SkillId.FRACTION_ARITHMETIC)
    }

    @Test
    fun verificarPorSustitucionNoSeOfreceAntesDeSaberDespejar() {
        val recienIniciado = graph.availableSkills(setOf(INTEGER_OPERATIONS))
        assertFalse(SOLUTION_VERIFICATION in recienIniciado)
        assertTrue(ISOLATING_VARIABLE in graph.transitivePrerequisites(SOLUTION_VERIFICATION))
    }

    @Test
    fun elPorcentajeCuelgaDeLaConversionDecimal() {
        assertEquals(setOf(SkillId.DECIMAL_FRACTION_CONVERSION), graph.prerequisitesOf(PERCENTAGE_REASONING))
    }

    @Test
    fun conCeroDominioLaPropuestaEsEmpezarPorLaRaiz() {
        assertEquals(listOf(INTEGER_OPERATIONS), graph.availableSkills(emptySet()))
    }

    @Test
    fun elCaminoALaUltimaHabilidadEsUnaSecuenciaCompleta() {
        val camino = graph.pathTo(DOMAIN_RESTRICTIONS, emptySet())
        assertContains(camino.orderedSkills, INTEGER_OPERATIONS)
        assertEquals(DOMAIN_RESTRICTIONS, camino.orderedSkills.last())
        assertTrue(camino.reason.startsWith("Para llegar a"), camino.reason)
    }
}
