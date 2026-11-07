package mx.matica.design.manipulatives

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManipulativesTest {

    @Test
    fun balanzaDetectaEquilibrioYCalculaInclinacion() {
        // Balanza equilibrada: 1x + 2 = 5 con x=3
        val balanced = BalanceScaleState(
            unknownsLeft = 1,
            constantsLeft = 2,
            unknownsRight = 0,
            constantsRight = 5,
            unknownValue = 3f,
        )
        assertTrue(balanced.isBalanced)
        assertEquals(0f, balanced.tiltDegrees, 0.001f)

        // Balanza desbalanceada: lado izquierdo más pesado (2x + 2 = 8 > 5)
        val leftHeavy = BalanceScaleState(
            unknownsLeft = 2,
            constantsLeft = 2,
            unknownsRight = 0,
            constantsRight = 5,
            unknownValue = 3f,
        )
        assertFalse(leftHeavy.isBalanced)
        assertTrue(leftHeavy.tiltDegrees < 0f, "Debe inclinarse a la izquierda (ángulo negativo)")

        // Balanza desbalanceada: lado derecho más pesado (1x + 1 = 4 < 7)
        val rightHeavy = BalanceScaleState(
            unknownsLeft = 1,
            constantsLeft = 1,
            unknownsRight = 0,
            constantsRight = 7,
            unknownValue = 3f,
        )
        assertFalse(rightHeavy.isBalanced)
        assertTrue(rightHeavy.tiltDegrees > 0f, "Debe inclinarse a la derecha (ángulo positivo)")
    }

    @Test
    fun azulejosAlgebraicosDiferencianSignoYVariable() {
        val posVar = TileKind.POS_VAR
        assertTrue(posVar.isPositive)
        assertTrue(posVar.isVariable)
        assertEquals("+x", posVar.symbol)

        val negUnit = TileKind.NEG_UNIT
        assertFalse(negUnit.isPositive)
        assertFalse(negUnit.isVariable)
        assertEquals("-1", negUnit.symbol)
    }
}
