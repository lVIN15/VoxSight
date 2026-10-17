package com.cit.kaido.voxsight

import org.junit.Assert.*
import org.junit.Test

/**
 * State Machine Unit Test for Section 6.3 Accompaniment Toggle Transitions.
 */
class AccompanimentStateMachineTest {

    class MockAccompanimentStateMachine {
        var isAccompanimentEnabled: Boolean = true
        var currentFocus: String = "S"
        var mutedVoices = mutableMapOf<String, Boolean>()
        var voiceVolumes = mutableMapOf<String, Float>()
        var canceledAccompanimentCount = 0

        fun focusPart(part: String) {
            currentFocus = part
            listOf("S", "A", "T", "B", "SOLO").forEach { v ->
                mutedVoices[v] = (v != currentFocus)
                if (v == currentFocus) {
                    voiceVolumes[v] = 1.0f
                }
            }
            updateAccompanimentState()
        }

        fun setAccompanimentToggle(enabled: Boolean) {
            isAccompanimentEnabled = enabled
            updateAccompanimentState()
        }

        fun updateAccompanimentState() {
            if (currentFocus in setOf("OTHER", "OTHERS")) {
                // State 3: Others is primary focus at 100% volume
                mutedVoices["OTHER"] = false
                mutedVoices["OTHERS"] = false
                voiceVolumes["OTHER"] = 1.0f
                voiceVolumes["OTHERS"] = 1.0f
            } else {
                // States 1, 2, 4: Vocal focus
                if (isAccompanimentEnabled) {
                    mutedVoices["OTHER"] = false
                    mutedVoices["OTHERS"] = false
                    voiceVolumes["OTHER"] = 0.35f
                    voiceVolumes["OTHERS"] = 0.35f
                } else {
                    mutedVoices["OTHER"] = true
                    mutedVoices["OTHERS"] = true
                    canceledAccompanimentCount++
                }
            }
        }
    }

    @Test
    fun testState1VocalFocusAccompanimentOn() {
        val sm = MockAccompanimentStateMachine()
        sm.focusPart("S")
        sm.setAccompanimentToggle(true)

        // Soprano 100% unmuted
        assertEquals(false, sm.mutedVoices["S"])
        assertEquals(1.0f, sm.voiceVolumes["S"] ?: 0f, 0.01f)

        // Alto/Tenor/Bass muted
        assertEquals(true, sm.mutedVoices["A"])
        assertEquals(true, sm.mutedVoices["T"])
        assertEquals(true, sm.mutedVoices["B"])

        // Others 35% backing track unmuted
        assertEquals(false, sm.mutedVoices["OTHER"])
        assertEquals(0.35f, sm.voiceVolumes["OTHER"] ?: 0f, 0.01f)
    }

    @Test
    fun testState2VocalFocusAccompanimentOff() {
        val sm = MockAccompanimentStateMachine()
        sm.focusPart("S")
        sm.setAccompanimentToggle(false)

        // Soprano 100% unmuted
        assertEquals(false, sm.mutedVoices["S"])
        assertEquals(1.0f, sm.voiceVolumes["S"] ?: 0f, 0.01f)

        // Others strictly muted & notes cancelled
        assertEquals(true, sm.mutedVoices["OTHER"])
        assertTrue(sm.canceledAccompanimentCount >= 1)
    }

    @Test
    fun testState3SwitchToOthersChipRemembersPreference() {
        val sm = MockAccompanimentStateMachine()
        sm.focusPart("S")
        sm.setAccompanimentToggle(false) // User preferred Accompaniment OFF

        // Now user selects OTHERS chip
        sm.focusPart("OTHER")

        // Others becomes 100% full volume primary focus
        assertEquals(false, sm.mutedVoices["OTHER"])
        assertEquals(1.0f, sm.voiceVolumes["OTHER"] ?: 0f, 0.01f)

        // All vocal parts muted
        assertEquals(true, sm.mutedVoices["S"])
        assertEquals(true, sm.mutedVoices["A"])
        assertEquals(true, sm.mutedVoices["T"])
        assertEquals(true, sm.mutedVoices["B"])

        // Remembered preference remains intact (still false)
        assertEquals(false, sm.isAccompanimentEnabled)
    }

    @Test
    fun testState4SwitchFromOthersBackToVocalRestoresPreference() {
        val sm = MockAccompanimentStateMachine()
        sm.focusPart("S")
        sm.setAccompanimentToggle(false) // OFF preference
        sm.focusPart("OTHER") // temporary switch to Others
        sm.focusPart("S") // switch back to Soprano

        // Soprano unmuted at 100%
        assertEquals(false, sm.mutedVoices["S"])
        assertEquals(1.0f, sm.voiceVolumes["S"] ?: 0f, 0.01f)

        // Others restores to MUTED because remembered preference was false
        assertEquals(true, sm.mutedVoices["OTHER"])

        // Now test with ON preference
        sm.setAccompanimentToggle(true)
        sm.focusPart("OTHER")
        sm.focusPart("S")

        // Others restores to 35% backing volume because remembered preference was true
        assertEquals(false, sm.mutedVoices["OTHER"])
        assertEquals(0.35f, sm.voiceVolumes["OTHER"] ?: 0f, 0.01f)
    }
}
