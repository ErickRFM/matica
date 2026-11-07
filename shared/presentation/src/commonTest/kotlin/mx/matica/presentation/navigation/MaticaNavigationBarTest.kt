package mx.matica.presentation.navigation

import mx.matica.presentation.components.MaticaIcon
import mx.matica.presentation.route.MaticaRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaticaNavigationBarTest {

    @Test
    fun laBarraDeNavegacionContieneLosCuatroDestinosDeFigma() {
        val tabs = MaticaNavTab.values()
        assertEquals(4, tabs.size, "Figma Frame 2 define exactamente 4 botones de navegación.")

        val labels = tabs.map { it.label }
        assertEquals(listOf("Inicio", "Ruta", "Repaso", "Perfil"), labels)

        val icons = tabs.map { it.icon }
        assertEquals(listOf(MaticaIcon.HOME, MaticaIcon.ROUTE, MaticaIcon.REFRESH, MaticaIcon.PROFILE), icons)

        val routes = tabs.map { it.route }
        assertEquals(listOf(MaticaRoute.Home, MaticaRoute.RouteMap, MaticaRoute.ReviewQueue, MaticaRoute.Profile), routes)
    }

    @Test
    fun cadaDestinoEsUnaRutaValidaDelSistema() {
        MaticaNavTab.values().forEach { tab ->
            assertTrue(MaticaRoute.all.contains(tab.route), "La pestaña ${tab.label} debe apuntar a una ruta registrada.")
        }
    }
}
