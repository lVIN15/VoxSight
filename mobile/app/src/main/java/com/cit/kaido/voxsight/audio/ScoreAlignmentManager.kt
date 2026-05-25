package com.cit.kaido.voxsight.audio

import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlNote
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore

/**
 * ScoreAlignmentManager
 *
 * Maps the current playhead position (as a 0.0–1.0 fractional progress value
 * from [Module2PracticeScreen]) to the active [TargetNote] the singer should
 * currently be producing.
 *
 * SDD Class Diagram mapping:
 *   ScoreManager <<External (Module 1/3)>>
 *     +getCurrentTargetNote(playheadPosition : long) : TargetNote
 *
 * Design notes
 * ─────────────
 * • The [MusicXmlScore] only stores note durations in *divisions* (raw
 *   MusicXML units).  This manager converts them to milliseconds using the
 *   same DEFAULT_BPM constant that the parser uses, so the timings are
 *   always self-consistent.
 *
 * • The [voiceFilter] parameter lets the caller restrict alignment to a
 *   specific SATB voice part (voice = 1 by convention for the currently
 *   selected part).  Pass `null` to align across all voices.
 *
 * • The manager pre-computes cumulative onset times once on construction and
 *   reuses them on every [getCurrentTargetNote] call, keeping the hot-path
 *   O(n) at worst but typically O(1) with binary search.
 */
class ScoreAlignmentManager(
    private val score: MusicXmlScore,
    /** MusicXML <divisions> value from the parsed score. Default: 1. */
    private val divisions: Int = 1,
    /** BPM — must match the parser's DEFAULT_BPM (96 f). */
    private val bpm: Float = DEFAULT_BPM,
    /** If non-null, only notes from this MusicXML voice number are considered. */
    private val voiceFilter: Int? = null
) {

    companion object {
        private const val DEFAULT_BPM = 96f
        private const val MS_PER_MINUTE = 60_000f
    }

    // ── Pre-computed timeline ──────────────────────────────────────────────

    /**
     * Internal representation of each note's time window.
     * Built once on construction.
     */
    private data class NoteWindow(
        val note: MusicXmlNote,
        val startMs: Long,
        val endMs: Long,
        val targetHz: Float
    )

    private val timeline: List<NoteWindow> = buildTimeline()

    /** Total score duration in ms (mirrors [MusicXmlScore.totalSeconds] × 1000). */
    val totalDurationMs: Long = timeline.lastOrNull()?.endMs ?: 0L

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Returns the [TargetNote] active at [playheadProgress] (0.0–1.0).
     *
     * This overload accepts the same fractional progress that
     * [Module2PracticeScreen] tracks internally, converting it to an
     * absolute millisecond position using [totalDurationMs].
     *
     * Returns `null` if:
     *  • The score has no notes.
     *  • The note at the current position has an unrecognised step name.
     *  • [playheadProgress] is out of range.
     */
    fun getCurrentTargetNote(playheadProgress: Float): TargetNote? {
        if (timeline.isEmpty()) return null
        val positionMs = (playheadProgress.coerceIn(0f, 1f) * totalDurationMs).toLong()
        return getCurrentTargetNote(positionMs)
    }

    /**
     * Returns the [TargetNote] active at an absolute [positionMs] offset from
     * the beginning of the score.
     *
     * Uses a simple linear scan; for typical choir-piece lengths (< 1 000
     * notes) this is fast enough to remain under the ≤ 0.5 s latency target
     * even without a binary search.
     */
    fun getCurrentTargetNote(positionMs: Long): TargetNote? {
        val window = timeline.firstOrNull { positionMs in it.startMs until it.endMs }
            ?: timeline.lastOrNull { positionMs >= it.startMs }   // hold last note
            ?: return null

        return TargetNote(
            pitchFrequencyHz = window.targetHz,
            durationMs = window.endMs - window.startMs,
            noteName = PitchComparator.noteLabel(window.note.step, window.note.octave)
        )
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun buildTimeline(): List<NoteWindow> {
        val msPerDivision = MS_PER_MINUTE / (bpm * divisions)

        val filtered = if (voiceFilter != null) {
            score.notes.filter { it.voice == voiceFilter }
        } else {
            score.notes
        }

        val windows = mutableListOf<NoteWindow>()
        var cursorMs = 0L

        for (note in filtered) {
            val durationMs = (note.durationDivisions * msPerDivision).toLong()
                .coerceAtLeast(1L)

            val hz = PitchComparator.noteToHz(note.step, note.octave)
            if (hz != null) {
                windows.add(
                    NoteWindow(
                        note = note,
                        startMs = cursorMs,
                        endMs = cursorMs + durationMs,
                        targetHz = hz
                    )
                )
            }
            cursorMs += durationMs
        }

        return windows
    }
}
