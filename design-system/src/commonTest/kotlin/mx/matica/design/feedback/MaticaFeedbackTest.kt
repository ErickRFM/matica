package mx.matica.design.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaticaFeedbackTest {

    @Test
    fun feedbackSoundCatalogContainsAllRequiredAudioTriggers() {
        val names = FeedbackSound.values().map { it.name }.toSet()
        val expected = setOf("CLICK", "STEP_SELECT", "CORRECT", "INCORRECT", "VICTORY", "NODE_CLICK")
        assertEquals(expected, names)
    }

    @Test
    fun feedbackHapticCatalogContainsAllRequiredVibrationTypes() {
        val names = FeedbackHaptic.values().map { it.name }.toSet()
        val expected = setOf("LIGHT_TICK", "CONFIRMATION", "ERROR", "CELEBRATION", "SELECTION")
        assertEquals(expected, names)
    }

    @Test
    fun noOpFeedbackExecutesGracefullyWithoutExceptions() {
        val feedback = NoOpMaticaFeedback
        feedback.keyTap()
        feedback.stepSelect()
        feedback.correct()
        feedback.incorrect()
        feedback.victory()
        feedback.nodeClick()
    }

    @Test
    fun recordingFeedbackTracksAudioAndHapticDispatches() {
        val feedback = RecordingMaticaFeedback()

        feedback.keyTap()
        assertEquals(listOf(FeedbackSound.CLICK), feedback.playedSounds)
        assertEquals(listOf(FeedbackHaptic.LIGHT_TICK), feedback.performedHaptics)

        feedback.clear()
        feedback.stepSelect()
        assertEquals(listOf(FeedbackSound.STEP_SELECT), feedback.playedSounds)
        assertEquals(listOf(FeedbackHaptic.SELECTION), feedback.performedHaptics)

        feedback.clear()
        feedback.correct()
        assertEquals(listOf(FeedbackSound.CORRECT), feedback.playedSounds)
        assertEquals(listOf(FeedbackHaptic.CONFIRMATION), feedback.performedHaptics)

        feedback.clear()
        feedback.incorrect()
        assertEquals(listOf(FeedbackSound.INCORRECT), feedback.playedSounds)
        assertEquals(listOf(FeedbackHaptic.ERROR), feedback.performedHaptics)

        feedback.clear()
        feedback.victory()
        assertEquals(listOf(FeedbackSound.VICTORY), feedback.playedSounds)
        assertEquals(listOf(FeedbackHaptic.CELEBRATION), feedback.performedHaptics)

        feedback.clear()
        feedback.nodeClick()
        assertEquals(listOf(FeedbackSound.NODE_CLICK), feedback.playedSounds)
        assertEquals(listOf(FeedbackHaptic.SELECTION), feedback.performedHaptics)
    }
}
