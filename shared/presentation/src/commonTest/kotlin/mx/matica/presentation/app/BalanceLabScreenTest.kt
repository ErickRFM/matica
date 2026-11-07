package mx.matica.presentation.app

import mx.matica.presentation.route.MaticaRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BalanceLabScreenTest {

    @Test
    fun rutaBalanceLabSeParseaYReconstruye() {
        val route = MaticaRoute.BalanceLab
        assertEquals("balance-lab", route.path)

        val parsed = MaticaRoute.parse("balance-lab")
        assertNotNull(parsed)
        assertEquals(MaticaRoute.BalanceLab, parsed)
    }

    @Test
    fun rutaBalanceLabEstaIncluidaEnInventario() {
        assertEquals(21, MaticaRoute.all.size)
        assertNotNull(MaticaRoute.all.firstOrNull { it == MaticaRoute.BalanceLab })
    }
}
