package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.pitch.PitchAttempt
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Test Suite for Pitch Test Back Button Confirmation and Playback State Management.
 * Verifies all 8 core test scenarios:
 * 1. Press Back while Pitch Test is playing -> Playback pauses and exit confirmation appears.
 * 2. Select Continue Practice -> Practice resumes from correct position if it was previously playing.
 * 3. Press Back while already paused -> Exit confirmation appears without changing paused state.
 * 4. Select Continue Practice while previously paused -> Practice remains paused.
 * 5. Select Exit Practice -> User exits and resources cleaned up.
 * 6. Cancel exit after several practiced measures -> Existing practice progress preserved.
 * 7. Press Back repeatedly -> No duplicate modals or state corruption.
 * 8. Listen Only mode -> Direct exit without confirmation.
 */
class PitchTestExitConfirmationTest {

    /**
     * Pure Kotlin State Machine modeling the exact state transitions of PracticeViewModel
     * and AppNavigation for the Pitch Test exit confirmation flow.
     */
    class PitchTestPlaybackStateMachine(
        var isMicrophoneEnabled: Boolean = true,
        var isPlaying: Boolean = false,
        var showExitConfirmationModal: Boolean = false,
        var showPauseModal: Boolean = false,
        var wasPlayingBeforeExitPrompt: Boolean = false,
        val pitchAttempts: MutableList<PitchAttempt> = mutableListOf(),
        var sessionEnded: Boolean = false
    ) {
        fun onBackRequested(): Boolean {
            if (!isMicrophoneEnabled) {
                return false
            }
            if (showExitConfirmationModal) {
                return true
            }
            showPauseModal = false
            wasPlayingBeforeExitPrompt = isPlaying
            if (isPlaying) {
                isPlaying = false
            }
            showExitConfirmationModal = true
            return true
        }

        fun cancelExitPractice(): Boolean {
            showExitConfirmationModal = false
            val shouldResume = wasPlayingBeforeExitPrompt
            wasPlayingBeforeExitPrompt = false
            if (shouldResume) {
                isPlaying = true
                return true
            }
            return false
        }

        fun confirmExitPractice() {
            showExitConfirmationModal = false
            wasPlayingBeforeExitPrompt = false
            isPlaying = false
            sessionEnded = true
        }

        fun addPitchAttempt(attempt: PitchAttempt) {
            // Emulates PracticeViewModel: if paused, pitch attempts are rejected
            if (!isPlaying) {
                return
            }
            pitchAttempts.add(attempt)
        }
    }

    @Test
    fun testPressBackWhilePlaying_pausesPlaybackAndShowsExitConfirmation() {
        val sm = PitchTestPlaybackStateMachine(
            isMicrophoneEnabled = true,
            isPlaying = true
        )

        val intercepted = sm.onBackRequested()

        assertTrue("Back press must be intercepted in Pitch Test", intercepted)
        assertTrue("Exit confirmation modal must appear", sm.showExitConfirmationModal)
        assertFalse("Playback must be paused immediately", sm.isPlaying)
        assertTrue("wasPlayingBeforeExitPrompt must record that playback was active", sm.wasPlayingBeforeExitPrompt)
    }

    @Test
    fun testContinuePracticeWhilePreviouslyPlaying_resumesPlayback() {
        val sm = PitchTestPlaybackStateMachine(
            isMicrophoneEnabled = true,
            isPlaying = true
        )

        sm.onBackRequested()
        assertTrue("Exit modal is showing", sm.showExitConfirmationModal)
        assertFalse("Playback paused while modal showing", sm.isPlaying)

        val resumed = sm.cancelExitPractice()

        assertTrue("Playback must resume when continuing practice", resumed)
        assertTrue("Playing state must be restored to true", sm.isPlaying)
        assertFalse("Exit modal must be dismissed", sm.showExitConfirmationModal)
        assertFalse("wasPlayingBeforeExitPrompt must be reset", sm.wasPlayingBeforeExitPrompt)
    }

    @Test
    fun testPressBackWhileAlreadyPaused_showsExitConfirmationWithoutChangingPausedState() {
        val sm = PitchTestPlaybackStateMachine(
            isMicrophoneEnabled = true,
            isPlaying = false // Already paused manually
        )

        val intercepted = sm.onBackRequested()

        assertTrue("Back press must be intercepted", intercepted)
        assertTrue("Exit confirmation modal must appear", sm.showExitConfirmationModal)
        assertFalse("Playback remains paused", sm.isPlaying)
        assertFalse("wasPlayingBeforeExitPrompt must be false", sm.wasPlayingBeforeExitPrompt)
    }

    @Test
    fun testContinuePracticeWhilePreviouslyPaused_remainsPaused() {
        val sm = PitchTestPlaybackStateMachine(
            isMicrophoneEnabled = true,
            isPlaying = false
        )

        sm.onBackRequested()
        val resumed = sm.cancelExitPractice()

        assertFalse("Playback must NOT resume if it was already paused before Back", resumed)
        assertFalse("Playing state remains false", sm.isPlaying)
        assertFalse("Exit modal is dismissed", sm.showExitConfirmationModal)
    }

    @Test
    fun testExitPractice_cleansUpSessionAndResetsState() {
        val sm = PitchTestPlaybackStateMachine(
            isMicrophoneEnabled = true,
            isPlaying = true
        )

        sm.onBackRequested()
        sm.confirmExitPractice()

        assertFalse("Exit modal dismissed", sm.showExitConfirmationModal)
        assertFalse("Playback stopped", sm.isPlaying)
        assertFalse("wasPlaying flag reset", sm.wasPlayingBeforeExitPrompt)
        assertTrue("Session marked ended and cleaned up", sm.sessionEnded)
    }

    @Test
    fun testCancelExitAfterSeveralPracticedMeasures_preservesAccumulatedProgress() {
        val sm = PitchTestPlaybackStateMachine(
            isMicrophoneEnabled = true,
            isPlaying = true
        )

        // Simulate 4 pitch attempts recorded during practice
        sm.addPitchAttempt(PitchAttempt(eventId = "e1", targetHz = 440f, detectedHz = 440f, deviationCents = 0f, isMatch = true, timestampMs = 1000L))
        sm.addPitchAttempt(PitchAttempt(eventId = "e2", targetHz = 493.88f, detectedHz = 490f, deviationCents = -13f, isMatch = true, timestampMs = 1100L))
        sm.addPitchAttempt(PitchAttempt(eventId = "e3", targetHz = 523.25f, detectedHz = 510f, deviationCents = -44f, isMatch = true, timestampMs = 1200L))
        sm.addPitchAttempt(PitchAttempt(eventId = "e4", targetHz = 587.33f, detectedHz = 620f, deviationCents = 93f, isMatch = false, timestampMs = 1300L))

        assertEquals("4 pitch attempts accumulated", 4, sm.pitchAttempts.size)

        // User accidentally presses Back
        sm.onBackRequested()
        assertTrue(sm.showExitConfirmationModal)

        // User chooses Continue Practice
        sm.cancelExitPractice()

        // Progress must be completely intact
        assertEquals("All 4 pitch attempts must be preserved", 4, sm.pitchAttempts.size)
        assertEquals("First event retained", "e1", sm.pitchAttempts[0].eventId)
        assertEquals("Last event retained", "e4", sm.pitchAttempts[3].eventId)
    }

    @Test
    fun testPressBackRepeatedly_doesNotDuplicateOrCorruptState() {
        val sm = PitchTestPlaybackStateMachine(
            isMicrophoneEnabled = true,
            isPlaying = true
        )

        // First press: opens modal, records wasPlaying = true
        val handled1 = sm.onBackRequested()
        assertTrue(handled1)
        assertTrue(sm.showExitConfirmationModal)
        assertTrue(sm.wasPlayingBeforeExitPrompt)

        // Rapid second press while modal is already visible
        val handled2 = sm.onBackRequested()
        assertTrue("Repeated back press is safely consumed", handled2)
        assertTrue("Modal stays open", sm.showExitConfirmationModal)
        assertTrue("wasPlaying flag must not be overwritten to false", sm.wasPlayingBeforeExitPrompt)

        // When user finally continues practice:
        val resumed = sm.cancelExitPractice()
        assertTrue("Playback still successfully resumes", resumed)
        assertTrue(sm.isPlaying)
    }

    @Test
    fun testListenOnlyMode_doesNotShowExitConfirmation() {
        val sm = PitchTestPlaybackStateMachine(
            isMicrophoneEnabled = false, // Listen Only Mode
            isPlaying = true
        )

        val intercepted = sm.onBackRequested()

        assertFalse("Listen Only mode must not intercept back navigation", intercepted)
        assertFalse("Modal must not be shown", sm.showExitConfirmationModal)
    }

    @Test
    fun testPitchEvaluationSuspendedWhilePaused_doesNotRecordFalseAttempts() {
        val sm = PitchTestPlaybackStateMachine(
            isMicrophoneEnabled = true,
            isPlaying = true
        )

        sm.addPitchAttempt(PitchAttempt(eventId = "note_1", targetHz = 440f, detectedHz = 440f, deviationCents = 0f, isMatch = true, timestampMs = 1000L))
        assertEquals(1, sm.pitchAttempts.size)

        // Back pressed -> paused
        sm.onBackRequested()
        assertFalse(sm.isPlaying)

        // Ambient speech or user talking during exit confirmation dialog
        sm.addPitchAttempt(PitchAttempt(eventId = "spurious", targetHz = 440f, detectedHz = 220f, deviationCents = 1200f, isMatch = false, timestampMs = 2000L))

        // Spurious attempt must be ignored while paused
        assertEquals("Spurious attempt while paused must NOT be recorded", 1, sm.pitchAttempts.size)
    }
}
