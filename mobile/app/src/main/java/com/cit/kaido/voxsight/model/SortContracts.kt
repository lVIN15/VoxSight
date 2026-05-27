package com.cit.kaido.voxsight.model

/**
 * Sort Contracts (Fix #37 — Dual Canonical Sort Contract)
 *
 * Two sorts exist. They are NEVER interchangeable.
 *
 * ┌─────────────────┬──────────────┬───────────────────────────────────────────┐
 * │ Sort            │ Domain       │ Used For                                  │
 * ├─────────────────┼──────────────┼───────────────────────────────────────────┤
 * │ Identity Sort   │ Analysis     │ chord_index assignment (Python only)      │
 * │ Playback Sort   │ Execution    │ Dispatcher queue ordering (Kotlin only)   │
 * └─────────────────┴──────────────┴───────────────────────────────────────────┘
 *
 * CRITICAL RULES:
 *   - Identity Sort lives ONLY in satb_analyzer.py — DO NOT reimplement here
 *   - Playback Sort lives ONLY in this file — DO NOT import in analysis code
 *   - event_id is NEVER used for sorting in either domain
 *   - chord_index has NO temporal meaning
 *   - Playback Sort has NO identity meaning
 */
object SortContracts {

    /**
     * Playback Sort Comparator (EXECUTION DOMAIN ONLY)
     *
     * Ordering: tick_position ASC → NOTE_OFF before NOTE_ON → chord grouping
     *
     * Used ONLY by NativePlaybackEngine.kt for dispatcher queue ordering.
     * DO NOT import or use this in SyncManager, renderer, or UI code.
     */
    val PLAYBACK_SORT: Comparator<MusicalEvent> = compareBy<MusicalEvent> { it.tickPosition }
        .thenBy { if (it.tieType == "stop") 0 else 1 }  // NOTE_OFF (tie stops) before NOTE_ON
        .thenBy { it.partId }
        .thenBy { it.pitchMidi }

    /**
     * WARNING: Identity Sort is NOT available in Kotlin.
     * It exists ONLY in satb_analyzer.py (Python backend).
     *
     * The identity sort key is:
     *   (pitch_midi ASC, voice_source ASC, staff_id ASC, tie_type_priority ASC, xml_element_order ASC)
     *
     * chord_index is assigned during analysis and is embedded in event_id.
     * It is consumed here, never recomputed.
     */
}
