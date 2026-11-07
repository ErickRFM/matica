package mx.matica.learning.skills

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import mx.matica.math.skills.SkillId
import mx.matica.math.skills.SkillId.ALGEBRAIC_EXPRESSIONS
import mx.matica.math.skills.SkillId.BALANCE_PRINCIPLE
import mx.matica.math.skills.SkillId.COMBINING_LIKE_TERMS
import mx.matica.math.skills.SkillId.DISTRIBUTIVE_PROPERTY
import mx.matica.math.skills.SkillId.FRACTION_ARITHMETIC
import mx.matica.math.skills.SkillId.INTEGER_OPERATIONS
import mx.matica.math.skills.SkillId.ISOLATING_VARIABLE
import mx.matica.math.skills.SkillId.ORDER_OF_OPERATIONS
import mx.matica.math.skills.SkillId.SIGN_HANDLING

/**
 * Pruebas del grafo de habilidades. Se vigila la promesa del producto: nadie recibe una actividad
 * cuyo prerrequisito no está dominado, y el orden propuesto siempre es el mismo.
 */
class SkillGraphTest {

    private val miniatura: SkillGraph = buildGraph(
        ALGEBRAIC_EXPRESSIONS to ORDER_OF_OPERATIONS,
        BALANCE_PRINCIPLE to DISTRIBUTIVE_PROPERTY,
        BALANCE_PRINCIPLE to COMBINING_LIKE_TERMS,
        BALANCE_PRINCIPLE to SIGN_HANDLING,
        COMBINING_LIKE_TERMS to ALGEBRAIC_EXPRESSIONS,
        DISTRIBUTIVE_PROPERTY to ALGEBRAIC_EXPRESSIONS,
        FRACTION_ARITHMETIC to INTEGER_OPERATIONS,
        ISOLATING_VARIABLE to BALANCE_PRINCIPLE,
        ORDER_OF_OPERATIONS to INTEGER_OPERATIONS,
    )

    @Test
    fun rechazaQueUnaHabilidadSeaSuPropioPrerrequisito() {
        val rejected = assertIs<GraphBuild.Rejected>(buildGraphBuild(INTEGER_OPERATIONS to INTEGER_OPERATIONS))
        assertEquals(listOf(GraphProblem.SelfPrerequisite(INTEGER_OPERATIONS)), rejected.problems)
    }

    @Test
    fun rechazaUnCicloYDevuelveElCaminoQueLoCierra() {
        val rejected = assertIs<GraphBuild.Rejected>(
            buildGraphBuild(
                ALGEBRAIC_EXPRESSIONS to BALANCE_PRINCIPLE,
                BALANCE_PRINCIPLE to DISTRIBUTIVE_PROPERTY,
                DISTRIBUTIVE_PROPERTY to ALGEBRAIC_EXPRESSIONS,
            ),
        )
        val cycle = assertIs<GraphProblem.Cycle>(rejected.problems.single())
        assertEquals(cycle.path.first(), cycle.path.last(), "El ciclo debe cerrarse en el mismo nodo: ${cycle.path}")
        assertEquals(4, cycle.path.size)
    }

    @Test
    fun elOrdenTopologicoEsDeterminista() {
        val otraConstruccion = buildGraph(
            ORDER_OF_OPERATIONS to INTEGER_OPERATIONS,
            FRACTION_ARITHMETIC to INTEGER_OPERATIONS,
            ALGEBRAIC_EXPRESSIONS to ORDER_OF_OPERATIONS,
            DISTRIBUTIVE_PROPERTY to ALGEBRAIC_EXPRESSIONS,
            COMBINING_LIKE_TERMS to ALGEBRAIC_EXPRESSIONS,
            BALANCE_PRINCIPLE to DISTRIBUTIVE_PROPERTY,
            BALANCE_PRINCIPLE to COMBINING_LIKE_TERMS,
            BALANCE_PRINCIPLE to SIGN_HANDLING,
            ISOLATING_VARIABLE to BALANCE_PRINCIPLE,
        )
        assertEquals(miniatura.order(), otraConstruccion.order())
    }

    @Test
    fun todoPrerrequisitoApareceAntesQueSuHabilidad() {
        val order = miniatura.order()
        miniatura.skills.forEach { skill ->
            miniatura.prerequisitesOf(skill).forEach { prerequisite ->
                assertTrue(
                    order.indexOf(prerequisite) < order.indexOf(skill),
                    "$prerequisite debe preceder a $skill en $order",
                )
            }
        }
    }

    @Test
    fun losPrerrequisitosTransitivosCierranLaCadena() {
        assertEquals(
            setOf(
                BALANCE_PRINCIPLE,
                DISTRIBUTIVE_PROPERTY,
                COMBINING_LIKE_TERMS,
                SIGN_HANDLING,
                ALGEBRAIC_EXPRESSIONS,
                ORDER_OF_OPERATIONS,
                INTEGER_OPERATIONS,
            ),
            miniatura.transitivePrerequisites(ISOLATING_VARIABLE),
        )
        assertEquals(emptySet(), miniatura.transitivePrerequisites(INTEGER_OPERATIONS))
    }

    @Test
    fun losDependientesDirectosSeConocen() {
        assertEquals(setOf(BALANCE_PRINCIPLE), miniatura.dependentsOf(DISTRIBUTIVE_PROPERTY))
        assertEquals(emptySet(), miniatura.dependentsOf(ISOLATING_VARIABLE))
    }

    @Test
    fun laFronteraSoloOfreceHabilidadesConBaseDominada() {
        assertEquals(
            listOf(INTEGER_OPERATIONS, SIGN_HANDLING),
            miniatura.availableSkills(emptySet()),
            "Con cero dominio solo salen las raíces del grafo.",
        )

        val conBase = miniatura.availableSkills(
            setOf(INTEGER_OPERATIONS, ORDER_OF_OPERATIONS, ALGEBRAIC_EXPRESSIONS, SIGN_HANDLING),
        )
        assertContains(conBase, DISTRIBUTIVE_PROPERTY)
        assertContains(conBase, COMBINING_LIKE_TERMS)
        assertFalse(ISOLATING_VARIABLE in conBase, "No se despeja sin equilibrio.")
        assertFalse(BALANCE_PRINCIPLE in conBase, "Al equilibrio todavía le falta un prerrequisito.")
    }

    @Test
    fun elCaminoALaMetaNombraLoQueFalta() {
        val camino = miniatura.pathTo(ISOLATING_VARIABLE, setOf(INTEGER_OPERATIONS))
        assertContains(camino.orderedSkills, BALANCE_PRINCIPLE)
        assertEquals(ISOLATING_VARIABLE, camino.orderedSkills.last())
        assertTrue("faltan 6 prerrequisitos:" in camino.reason, camino.reason)
        assertEquals(
            miniatura.order().filter { it in camino.orderedSkills.toSet() },
            camino.orderedSkills,
            "El camino se recorre en el orden topológico, no en el orden de la enumeración.",
        )

        val listo = miniatura.pathTo(ISOLATING_VARIABLE, miniatura.transitivePrerequisites(ISOLATING_VARIABLE))
        assertEquals(listOf(ISOLATING_VARIABLE), listo.orderedSkills)
        assertTrue("está disponible" in listo.reason, listo.reason)
    }

    /**
     * El texto que ve un estudiante con un solo pendiente. Antes de `pluralEs` la frase salía
     * «faltan 1 prerrequisito(s)»: verbo en plural, sustantivo con la terminación del programador.
     */
    @Test
    fun unPendienteSeNombraEnSingular() {
        val dominaLoDemas = setOf(
            DISTRIBUTIVE_PROPERTY, COMBINING_LIKE_TERMS, ALGEBRAIC_EXPRESSIONS, ORDER_OF_OPERATIONS, INTEGER_OPERATIONS,
        )
        val camino = miniatura.pathTo(BALANCE_PRINCIPLE, dominaLoDemas)

        assertEquals(listOf(SIGN_HANDLING, BALANCE_PRINCIPLE), camino.orderedSkills)
        assertTrue("falta 1 prerrequisito:" in camino.reason, camino.reason)
        assertTrue("(s)" !in camino.reason, "El texto muestra la terminación del programador: ${camino.reason}")
    }

    @Test
    fun unaHabilidadSinAristasEsRaiz() {
        val grafo = buildGraph(SIGN_HANDLING to INTEGER_OPERATIONS)
        assertTrue(grafo.isRoot(INTEGER_OPERATIONS))
        assertFalse(grafo.isRoot(SIGN_HANDLING))
        assertEquals(listOf(INTEGER_OPERATIONS, SIGN_HANDLING), grafo.order())
        assertEquals(emptySet(), grafo.prerequisitesOf(INTEGER_OPERATIONS))
    }

    @Test
    fun losNodosQueSoloSonPrerrequisitoTambienVivenEnElGrafo() {
        val grafo = buildGraph(DISTRIBUTIVE_PROPERTY to INTEGER_OPERATIONS)
        assertEquals(setOf(INTEGER_OPERATIONS, DISTRIBUTIVE_PROPERTY), grafo.skills.toSet())
    }

    private fun buildGraph(vararg edges: Pair<SkillId, SkillId>): SkillGraph =
        (buildGraphBuild(*edges) as GraphBuild.Ready).graph

    private fun buildGraphBuild(vararg edges: Pair<SkillId, SkillId>): GraphBuild {
        var builder = SkillGraph.builder()
        edges.forEach { (skill, prerequisite) -> builder = builder.require(skill, prerequisite) }
        return builder.build()
    }
}
