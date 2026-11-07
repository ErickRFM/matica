package mx.matica.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreakFlameBadgeTest {

    @Test
    fun testReducedMotionEliminatesSway() {
        val sway = streakFlameTipSwayPx(reduced = true, progress = 0.5f)
        assertEquals(0f, sway)
    }

    @Test
    fun testReducedMotionEliminatesPulse() {
        val scale = streakPulseScale(reduced = true, isActive = true, progress = 0.5f)
        assertEquals(1f, scale)
    }

    @Test
    fun testInactiveStreakEliminatesPulse() {
        val scale = streakPulseScale(reduced = false, isActive = false, progress = 0.5f)
        assertEquals(1f, scale)
    }

    @Test
    fun testActiveStreakPulseCalculatesCorrectly() {
        val scaleMin = streakPulseScale(reduced = false, isActive = true, progress = 0f)
        val scaleMax = streakPulseScale(reduced = false, isActive = true, progress = 1f)
        assertEquals(1f, scaleMin)
        assertEquals(1.06f, scaleMax)
    }

    @Test
    fun testAccessibilityDescriptionForActiveAndInactive() {
        val activeDesc1 = streakBadgeAccessibilityDescription(1)
        assertTrue(activeDesc1.contains("1 día") && activeDesc1.contains("Racha activa"))

        val activeDesc5 = streakBadgeAccessibilityDescription(5)
        assertTrue(activeDesc5.contains("5 días") && activeDesc5.contains("Racha activa"))

        val inactiveDesc = streakBadgeAccessibilityDescription(0)
        assertTrue(inactiveDesc.contains("Sin racha activa"))
    }
}
