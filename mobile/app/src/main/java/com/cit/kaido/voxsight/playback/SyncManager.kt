package com.cit.kaido.voxsight.playback

import android.util.Log
import com.cit.kaido.voxsight.model.EventStream
import com.cit.kaido.voxsight.model.MusicalEvent
import java.security.MessageDigest
import kotlin.math.abs

/**
 * VoxSight Sync Manager v1.2
 * ==========================================
 * Bridges the native playback engine to the WebView OSMD renderer.
 *
 * Hybrid Two-Tier Mapping Contract:
 *   - Tier 1 (Exact Stable Note Identity): Matches `event.sourceNoteId` (`data-vx-id`)
 *     directly to OSMD graphical note elements. Supports shared unison noteheads
 *     (multiple simultaneous vocal events sharing one OSMD notehead) and multi-head
 *     tied notes split across system breaks.
 *   - Tier 2 (Deterministic Structural Fallback): For legacy/cloud/saved events where
 *     `event.sourceNoteId == null`, maps within aligned `(measure, staffIdx, pitchMidi, tick)`
 *     without cross-measure wild pitch guessing.
 *   - Graceful degradation: audio ALWAYS plays uninterrupted; visuals degrade cleanly.
 */
class SyncManager {

    companion object {
        private const val TAG = "SyncManager"
        private const val OSMD_TPQ = 480
    }

    // ─── Highlight Mode ────────────────────────────────────────────────
    enum class HighlightMode { NOTE_LEVEL, MEASURE_LEVEL, DISABLED }

    // ─── Layout Version Lock ───────────────────────────────────────────
    private var currentLayoutHash: String? = null
    private var coordinateMap = HashMap<String, NoteCoordinate>()  // event_id → screen position
    private var syncConfidence = 1.0f
    private var highlightMode = HighlightMode.DISABLED
    private var totalMappings = 0
    private var successfulMappings = 0

    // ─── Listener for WebView highlight commands ───────────────────────
    interface SyncListener {
        /** Highlight multiple notes at the given screen coordinates */
        fun onHighlightNotes(highlights: List<NoteHighlightData>)
        /** Clear all highlights */
        fun onClearHighlights()
        /** Sync confidence changed (for debug display) */
        fun onSyncConfidenceChanged(confidence: Float)
        /** Highlight mode changed (e.g. badge/banner updates) */
        fun onHighlightModeChanged(mode: HighlightMode)
    }

    private var listener: SyncListener? = null

    fun setListener(l: SyncListener) { listener = l }

    fun getHighlightMode(): HighlightMode = highlightMode
    fun getSyncConfidence(): Float = syncConfidence

    // ─── Coordinate Registration ───────────────────────────────────────
    fun registerCoordinates(
        layoutHash: String,
        coordinates: Map<String, NoteCoordinate>
    ) {
        currentLayoutHash = layoutHash
        coordinateMap.clear()
        coordinateMap.putAll(coordinates)
        totalMappings = coordinates.size
        syncConfidence = 1.0f
        successfulMappings = totalMappings
        highlightMode = HighlightMode.NOTE_LEVEL

        Log.i(TAG, "Registered ${coordinates.size} coordinates for layout $layoutHash")
        listener?.onSyncConfidenceChanged(syncConfidence)
        listener?.onHighlightModeChanged(highlightMode)
    }

    fun computeLayoutHash(width: Int, height: Int, zoom: Float, pageCount: Int): String {
        val raw = "osmd_render_w${width}_h${height}_z${zoom}_p${pageCount}"
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    fun isLayoutValid(layoutHash: String): Boolean {
        return currentLayoutHash == layoutHash
    }

    fun getCoordinateForEvent(eventId: String): NoteCoordinate? {
        return coordinateMap[eventId]
    }

    fun invalidateLayout() {
        currentLayoutHash = null
        coordinateMap.clear()
        syncConfidence = 0f
        highlightMode = HighlightMode.DISABLED
        Log.w(TAG, "Layout invalidated — coordinates cleared")
        listener?.onSyncConfidenceChanged(syncConfidence)
        listener?.onHighlightModeChanged(highlightMode)
    }

    val coordinateMapSize: Int get() = coordinateMap.size

    // ─── Playback Sync ─────────────────────────────────────────────────
    fun onPlaybackProgress(activeEventIds: List<String>) {
        if (activeEventIds.isEmpty()) return
        if (currentLayoutHash == null) return

        val highlights = mutableListOf<NoteHighlightData>()
        for (eventId in activeEventIds) {
            val coord = coordinateMap[eventId]
            if (coord != null) {
                highlights.add(NoteHighlightData(eventId, coord.x, coord.y, coord.width, coord.height))
            }
        }

        if (highlights.isNotEmpty()) {
            listener?.onHighlightNotes(highlights)
        }
    }

    fun onPlaybackStopped() {
        listener?.onClearHighlights()
    }

    // ─── Hybrid Two-Tier Bipartite Matching ────────────────────────────
    data class MatchResult(
        val eventId: String,
        val matched: Boolean,
        val duplicateMatch: Boolean
    )

    private fun scaleEventTickToOsmd(event: MusicalEvent): Int {
        val tpq = event.ticksPerQuarter.coerceAtLeast(1)
        return ((event.tickPosition.toLong() * OSMD_TPQ.toLong()) / tpq.toLong()).toInt()
    }

    private fun resolveExpectedStaffIdx(event: MusicalEvent): Int {
        return when {
            event.partId > 1 && event.staffId <= 1 -> event.partId - 1
            event.staffId > 0 -> event.staffId - 1
            else -> (event.partId - 1).coerceAtLeast(0)
        }
    }

    private fun isVoiceCompatible(eventVoice: String, elemPart: String): Boolean {
        val ev = eventVoice.uppercase().trim()
        val ep = elemPart.uppercase().trim()
        if (ev == ep) return true
        if (ep == "TUTTI" || ep == "ALL" || ep == "UNISON_ALL") {
            return ev != "OTHER" && ev != "OTHERS" && ev != "PIANO"
        }
        if (ep in setOf("UNISON_TB", "TB") && (ev.startsWith("T") || ev.startsWith("B") || ev.contains("TENOR") || ev.contains("BASS"))) return true
        if (ep in setOf("UNISON_SA", "SA") && (ev.startsWith("S") || ev.startsWith("A") || ev.contains("SOPRANO") || ev.contains("ALTO"))) return true
        if (ep in setOf("UNISON_S1S2", "S1S2") && (ev.startsWith("S") || ev.contains("SOPRANO"))) return true
        if (ep in setOf("UNISON_A1A2", "A1A2") && (ev.startsWith("A") || ev.contains("ALTO"))) return true
        if (ep in setOf("UNISON_T1T2", "T1T2") && (ev.startsWith("T") || ev.contains("TENOR"))) return true
        if (ep in setOf("UNISON_B1B2", "B1B2") && (ev.startsWith("B") || ev.contains("BASS"))) return true
        if ((ev.contains("SOLO") || ev.contains("BARITONE")) && (ep == "SOLO" || ep.contains("SOLO"))) return true
        val evInitial = ev.firstOrNull()?.toString() ?: ""
        val epInitial = ep.firstOrNull()?.toString() ?: ""
        return evInitial.isNotEmpty() && evInitial == epInitial
    }

    fun buildMapping(
        events: EventStream,
        osmdElements: List<OsmdNoteElement>,
        layoutHash: String
    ) {
        currentLayoutHash = layoutHash
        coordinateMap.clear()

        val activeEvents = events.filter { !it.isRest }
        val coords = HashMap<String, NoteCoordinate>(activeEvents.size)

        // Track claimed OSMD elements by unique list index so shared IDs across tied heads remain addressable
        val claimedByElementIdx = HashMap<Int, MusicalEvent>()
        val osmdIndexedById = HashMap<Int, MutableList<IndexedValue<OsmdNoteElement>>>()
        osmdElements.withIndex().forEach { indexed ->
            osmdIndexedById.getOrPut(indexed.value.id) { mutableListOf() }.add(indexed)
        }

        val hasReliableMeasureIndex = activeEvents.any { it.measureIndex > 0 } ||
            activeEvents.all { it.measureNumber <= 1 }

        fun canShareUnisonNoteHead(assigned: MusicalEvent, incoming: MusicalEvent): Boolean {
            val sameMeasure = if (hasReliableMeasureIndex) {
                assigned.measureIndex == incoming.measureIndex
            } else {
                assigned.measureNumber == incoming.measureNumber
            }
            val assignedTick480 = scaleEventTickToOsmd(assigned)
            val incomingTick480 = scaleEventTickToOsmd(incoming)
            return sameMeasure &&
                assigned.pitchMidi == incoming.pitchMidi &&
                abs(assignedTick480 - incomingTick480) <= (OSMD_TPQ / 2)
        }

        fun isElementAvailableFor(elemIdx: Int, event: MusicalEvent): Boolean {
            val assigned = claimedByElementIdx[elemIdx] ?: return true
            return canShareUnisonNoteHead(assigned, event)
        }

        var tier1Matched = 0
        var tier2Matched = 0

        // ─── TIER 1: Exact Stable Note Identity (`data-vx-id` / `sourceNoteId`) ───
        for (event in activeEvents) {
            val sourceId = event.sourceNoteId ?: continue
            val indexedCandidates = osmdIndexedById[sourceId] ?: continue
            val eventTick480 = scaleEventTickToOsmd(event)
            val expectedStaff = resolveExpectedStaffIdx(event)

            val bestIndexed = indexedCandidates
                .filter { isElementAvailableFor(it.index, event) }
                .minByOrNull { (_, elem) ->
                    val pitchPenalty = if (elem.midiNote == event.pitchMidi) 0 else 100_000
                    val staffPenalty = if (elem.staffIdx == expectedStaff) 0 else 5_000
                    val voicePenalty = if (isVoiceCompatible(event.satbVoice, elem.part)) 0 else 1_000
                    val measurePenalty = if (hasReliableMeasureIndex) {
                        abs(elem.measureIndex - event.measureIndex) * 2_000
                    } else {
                        abs(elem.measureNumber - event.measureNumber) * 2_000
                    }
                    pitchPenalty + measurePenalty + staffPenalty + voicePenalty + abs(elem.tick - eventTick480)
                } ?: continue

            val elem = bestIndexed.value
            claimedByElementIdx[bestIndexed.index] = event
            coords[event.eventId] = NoteCoordinate(elem.x, elem.y, elem.width, elem.height, elem.id)
            tier1Matched++
        }

        // ─── TIER 2: Deterministic Structural Fallback for Unstamped Events (`sourceNoteId == null`) ───
        // Protects cloud (Railway), previously saved local scores, and unstamped MusicXML imports.
        // Note: Events that carried an explicit non-null `sourceNoteId` that did not exist in OSMD
        // are intentionally NOT guessed here to prevent stale IDs from cross-matching wrong notes.
        val unstampedEvents = activeEvents.filter { it.sourceNoteId == null && !coords.containsKey(it.eventId) }
        if (unstampedEvents.isNotEmpty() && osmdElements.isNotEmpty()) {
            val eventsByMeasureKey = unstampedEvents.groupBy {
                if (hasReliableMeasureIndex) it.measureIndex else it.measureNumber
            }
            val osmdByMeasureKey = osmdElements.withIndex().groupBy { (_, elem) ->
                if (hasReliableMeasureIndex) elem.measureIndex else elem.measureNumber
            }

            val allMeasureKeys = (eventsByMeasureKey.keys + osmdByMeasureKey.keys).distinct().sorted()

            for (mKey in allMeasureKeys) {
                val mEvents = eventsByMeasureKey[mKey] ?: continue
                val mOsmd = osmdByMeasureKey[mKey] ?: continue

                val staffEvents = mEvents.groupBy { resolveExpectedStaffIdx(it) }
                val staffOsmd = mOsmd.groupBy { it.value.staffIdx }
                val allStaves = (staffEvents.keys + staffOsmd.keys).distinct().sorted()

                for (staffIdx in allStaves) {
                    val sEvents = (staffEvents[staffIdx] ?: emptyList())
                        .sortedWith(compareBy<MusicalEvent>({ scaleEventTickToOsmd(it) }, { -it.pitchMidi }))
                    val sOsmd = (staffOsmd[staffIdx] ?: emptyList())
                        .sortedWith(compareBy<IndexedValue<OsmdNoteElement>>({ it.value.tick }, { it.value.x }, { -it.value.midiNote }))

                    // Pass 2A: Same Measure + Same Staff + Compatible Voice + Exact Pitch + Nearest Tick
                    for (event in sEvents) {
                        if (coords.containsKey(event.eventId)) continue
                        val eventTick480 = scaleEventTickToOsmd(event)
                        val best = sOsmd
                            .filter { (idx, elem) ->
                                isElementAvailableFor(idx, event) &&
                                    elem.midiNote == event.pitchMidi &&
                                    isVoiceCompatible(event.satbVoice, elem.part)
                            }
                            .minByOrNull { (_, elem) -> abs(elem.tick - eventTick480) }
                        if (best != null) {
                            val elem = best.value
                            claimedByElementIdx[best.index] = event
                            coords[event.eventId] = NoteCoordinate(elem.x, elem.y, elem.width, elem.height, elem.id)
                            tier2Matched++
                        }
                    }

                    // Pass 2B: Same Measure + Same Staff + Exact Pitch + Nearest Tick
                    for (event in sEvents) {
                        if (coords.containsKey(event.eventId)) continue
                        val eventTick480 = scaleEventTickToOsmd(event)
                        val best = sOsmd
                            .filter { (idx, elem) ->
                                isElementAvailableFor(idx, event) &&
                                    elem.midiNote == event.pitchMidi
                            }
                            .minByOrNull { (_, elem) -> abs(elem.tick - eventTick480) }
                        if (best != null) {
                            val elem = best.value
                            claimedByElementIdx[best.index] = event
                            coords[event.eventId] = NoteCoordinate(elem.x, elem.y, elem.width, elem.height, elem.id)
                            tier2Matched++
                        }
                    }
                }

                // Pass 2C: Same Measure + Cross-Staff Fallback (e.g., condensed grand staff index shift) + Exact Pitch
                val remainingInMeasure = mEvents
                    .filter { !coords.containsKey(it.eventId) }
                    .sortedWith(compareBy<MusicalEvent>({ scaleEventTickToOsmd(it) }, { -it.pitchMidi }))
                for (event in remainingInMeasure) {
                    val eventTick480 = scaleEventTickToOsmd(event)
                    val best = mOsmd
                        .filter { (idx, elem) ->
                            isElementAvailableFor(idx, event) &&
                                elem.midiNote == event.pitchMidi
                        }
                        .minByOrNull { (_, elem) ->
                            val voicePenalty = if (isVoiceCompatible(event.satbVoice, elem.part)) 0 else 480
                            voicePenalty + abs(elem.tick - eventTick480)
                        }
                    if (best != null) {
                        val elem = best.value
                        claimedByElementIdx[best.index] = event
                        coords[event.eventId] = NoteCoordinate(elem.x, elem.y, elem.width, elem.height, elem.id)
                        tier2Matched++
                    }
                }
            }

            // Pass 2D: Adjacent Measure (+/- 1) + Same Staff + Exact Pitch (page-break split measure guard for legacy measure_number streams)
            val remainingUnstamped = unstampedEvents
                .filter { !coords.containsKey(it.eventId) }
                .sortedWith(compareBy<MusicalEvent>({ it.measureNumber }, { scaleEventTickToOsmd(it) }, { -it.pitchMidi }))
            for (event in remainingUnstamped) {
                val expectedStaff = resolveExpectedStaffIdx(event)
                val eventTick480 = scaleEventTickToOsmd(event)
                val best = osmdElements.withIndex()
                    .filter { (idx, elem) ->
                        val mDist = if (hasReliableMeasureIndex) {
                            abs(elem.measureIndex - event.measureIndex)
                        } else {
                            abs(elem.measureNumber - event.measureNumber)
                        }
                        isElementAvailableFor(idx, event) &&
                            mDist <= 1 &&
                            elem.staffIdx == expectedStaff &&
                            elem.midiNote == event.pitchMidi
                    }
                    .minByOrNull { (_, elem) -> abs(elem.tick - eventTick480) }
                if (best != null) {
                    val elem = best.value
                    claimedByElementIdx[best.index] = event
                    coords[event.eventId] = NoteCoordinate(elem.x, elem.y, elem.width, elem.height, elem.id)
                    tier2Matched++
                }
            }
        }

        coordinateMap.putAll(coords)

        val matched = coords.size
        val totalEvents = activeEvents.size
        syncConfidence = if (totalEvents > 0) (matched.toFloat() / totalEvents.toFloat()).coerceIn(0f, 1f) else 0f
        totalMappings = totalEvents
        successfulMappings = matched
        highlightMode = when {
            syncConfidence >= 0.70f -> HighlightMode.NOTE_LEVEL
            syncConfidence >= 0.30f -> HighlightMode.MEASURE_LEVEL
            else -> HighlightMode.DISABLED
        }

        Log.i(
            TAG,
            "Hybrid mapping completed: layout=$layoutHash, events=$totalEvents, osmd=${osmdElements.size}, " +
                "tier1Exact=$tier1Matched, tier2Structural=$tier2Matched, matched=$matched, " +
                "unmatched=${totalEvents - matched}, conf=$syncConfidence, mode=$highlightMode"
        )
        listener?.onSyncConfidenceChanged(syncConfidence)
        listener?.onHighlightModeChanged(highlightMode)
    }

    data class NoteCoordinate(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val id: Int = 0
    )

    data class OsmdNoteElement(
        val id: Int = 0,
        val eventId: String? = null,
        val tick: Int = 0,
        val midiNote: Int = 60,
        val measureNumber: Int = 1,
        val measureIndex: Int = 0,
        val voice: Int = 1,
        val staffIdx: Int = 0,
        val part: String = "S",
        val color: String = "#E11D48",
        val x: Float = 0f,
        val y: Float = 0f,
        val width: Float = 0f,
        val height: Float = 0f
    )

    data class NoteHighlightData(
        val eventId: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )
}
