package mx.matica.presentation.math

import mx.matica.learning.mastery.MasteryLevel
import mx.matica.math.skills.SkillId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteMapWorldViewTest {

    @Test
    fun testSCurveNormalizedOscillation() {
        val x0 = sCurveNormalizedX(0)
        val x1 = sCurveNormalizedX(1)
        val x2 = sCurveNormalizedX(2)
        val x3 = sCurveNormalizedX(3)

        // Indice 0: sin(0) = 0 -> 0.50
        assertEquals(0.50f, x0, 0.01f)
        // Indice 1: sin(pi/2) = 1 -> 0.50 + 0.28 = 0.78 (hacia la derecha)
        assertEquals(0.78f, x1, 0.01f)
        // Indice 2: sin(pi) = 0 -> 0.50 (centro)
        assertEquals(0.50f, x2, 0.01f)
        // Indice 3: sin(3pi/2) = -1 -> 0.50 - 0.28 = 0.22 (hacia la izquierda)
        assertEquals(0.22f, x3, 0.01f)
    }

    @Test
    fun testBuildWorldStationsInjectsChestsEveryInterval() {
        val skills = SkillId.entries.take(8)
        val nodes = skills.mapIndexed { i, skill ->
            RouteNode(
                skill = skill,
                label = skill.labelEs,
                level = MasteryLevel.DEVELOPING,
                suggested = i == 0,
                unlocked = i <= 1,
                row = i,
                column = i % 3,
            )
        }

        val stations = buildWorldStations(nodes, chestInterval = 4)
        // 8 nodos de habilidad + 2 cofres (uno tras el nodo 4 y otro tras el nodo 8) = 10 estaciones
        assertEquals(10, stations.size)

        // La estación en el índice 4 debe ser un ChestStation
        assertTrue(stations[4] is WorldStation.ChestStation)
        // La estación en el índice 9 debe ser el segundo ChestStation
        assertTrue(stations[9] is WorldStation.ChestStation)
    }
}
