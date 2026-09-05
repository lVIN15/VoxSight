package com.cit.kaido.voxsight.playback

import android.util.Log
import com.cit.kaido.voxsight.model.EventStream
import com.cit.kaido.voxsight.model.MusicalEvent
import java.security.MessageDigest

/**
 * VoxSight Sync Manager v1.1 (Milestone 3)
 * ==========================================
 * Bridges the native playback engine to the WebView renderer.
 *
 * Architecture Contract (v3.7):
 *   - Uses event_id for correlation between playback events and OSMD elements
 *   - layout_hash gating: sync coordinates are only valid for a specific render
 *   - Bipartite matching: maps events to OSMD note elements by tick + pitch
 *   - Confidence is monotonic (never increases after mismatch)
 *   - Graceful degradation: audio ALWAYS works, visuals are best-effort
 *
 * Sync Pipeline:
 *   PLAYBACK_TICK → FIND_EVENT → LOOKUP_COORDINATE → EMIT_HIGHLIGHT
 *   If any step fails: audio continues, highlight is suppressed.
 */
class SyncManager {

    companion object {
        private const val TAG = "SyncManager"
    }

    // ─── Highlight Mode ────────────────────────────────────────────────
    enum class HighlightMode { NOTE_LEVEL, MEASURE_LEVEL, DISABLED }

    // ─── Layout Version Lock ───────────────────────────────────────────
    // Coordinates are ONLY valid for a specific OSMD render.
    // If layout_hash changes (zoom, resize, reflow), coordinates must be re-extracted.
    private var currentLayoutHash: String? = null
    private var coordinateMap = HashMap<String, NoteCoordinate>()  // event_id → screen position
    private var syncConfidence = 1.0f  // Monotonic — only decreases
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
    /**
     * Register note coordinates extracted from OSMD render.
     * Called from JavaScript via WebView bridge.
     *
     * @param layoutHash Hash of the current OSMD render state
     * @param coordinates Map of event_id → screen position
     */
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

    /**
     * Compute a layout hash for the current render parameters.
     * If this changes, existing coordinates are invalidated.
     */
    fun computeLayoutHash(width: Int, height: Int, zoom: Float, pageCount: Int): String {
        val raw = "osmd_render_w${width}_h${height}_z${zoom}_p${pageCount}"
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    /**
     * Check if the current coordinate map is still valid.
     */
    fun isLayoutValid(layoutHash: String): Boolean {
        return currentLayoutHash == layoutHash
    }

    // ─── Event Coordination ────────────────────────────────────────────
    /**
     * Retrieve the mapped screen coordinate for a specific event ID.
     */
    fun getCoordinateForEvent(eventId: String): NoteCoordinate? {
        return coordinateMap[eventId]
    }

    /**
     * Invalidate coordinates (e.g., after zoom/resize/reflow).
     */
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
    /**
     * Called by the playback engine on each progress update.
     * Finds the matching note coordinate and emits a highlight command.
     *
     * Graceful degradation: if mapping fails, audio continues, highlight is suppressed.
     */
    fun onPlaybackProgress(activeEventIds: List<String>) {
        if (activeEventIds.isEmpty()) return
        if (currentLayoutHash == null) return  // No valid layout

        val highlights = mutableListOf<NoteHighlightData>()
        var foundMatches = 0
        for (eventId in activeEventIds) {
            val coord = coordinateMap[eventId]
            if (coord != null && highlightMode != HighlightMode.DISABLED) {
                highlights.add(NoteHighlightData(eventId, coord.x, coord.y, coord.width, coord.height))
                foundMatches++
            }
        }

        if (highlights.isNotEmpty()) {
            listener?.onHighlightNotes(highlights)
        }
        
        if (foundMatches < activeEventIds.size) {
            // Mapping miss — degrade confidence monotonically
            successfulMappings = (successfulMappings - (activeEventIds.size - foundMatches)).coerceAtLeast(0)
            if (totalMappings > 0) {
                val rawConfidence = successfulMappings.toFloat() / totalMappings.toFloat()
                syncConfidence = rawConfidence.coerceIn(0f, 1f)
            }

            Log.d(TAG, "Missing coordinates for some events (confidence: $syncConfidence)")
            listener?.onSyncConfidenceChanged(syncConfidence)

            // Re-evaluate highlight mode based on degraded confidence
            val oldMode = highlightMode
            highlightMode = when {
                syncConfidence >= 0.70f -> HighlightMode.NOTE_LEVEL
                syncConfidence >= 0.30f -> HighlightMode.MEASURE_LEVEL
                else -> HighlightMode.DISABLED
            }
            if (oldMode != highlightMode) {
                listener?.onHighlightModeChanged(highlightMode)
            }
        }
    }

    /**
     * Called when playback stops.
     */
    fun onPlaybackStopped() {
        listener?.onClearHighlights()
    }

    // ─── Bipartite Matching ────────────────────────────────────────────
    data class MatchResult(
        val eventId: String,
        val matched: Boolean,
        val duplicateMatch: Boolean
    )

    /**
     * Bulk-match events to OSMD elements using bipartite matching.
     * This is called once after OSMD renders and JavaScript extracts note positions.
     *
     * @param events The ORDER-FROZEN event stream
     * @param osmdElements Raw note elements extracted from OSMD
     * @param layoutHash The layout hash for this render
     */
    fun buildMapping(
        events: EventStream,
        osmdElements: List<OsmdNoteElement>,
        layoutHash: String
    ) {
        currentLayoutHash = layoutHash
        coordinateMap.clear()

        val coords = HashMap<String, NoteCoordinate>()
        val matchResults = ArrayList<MatchResult>()

        val activeEvents = events.filter { !it.isRest }
        val usedOsmdIds = HashSet<Int>()

        // Sort events strictly chronologically (Measure, Beat/Tick, Staff, Voice, Pitch descending)
        val sortedEvents = activeEvents.sortedWith(
            compareBy<MusicalEvent>({ it.measureNumber }, { it.tickPosition }, { it.staffId }, { it.voiceSource }, { -it.pitchMidi })
        )

        // 1. Group events and OSMD elements by 0-based measure index (mIdx)
        val eventsByMeasure = activeEvents.groupBy { it.measureIndex }
        val osmdByMeasure = osmdElements.groupBy { it.measureIndex }

        val allMeasureIndices = (eventsByMeasure.keys + osmdByMeasure.keys).distinct().sorted()

        for (mIdx in allMeasureIndices) {
            val measEvents = eventsByMeasure[mIdx] ?: emptyList()
            val measOsmd = osmdByMeasure[mIdx] ?: emptyList()

            // Group by staff (0-indexed matching OSMD staffIdx)
            val staffEvents = measEvents.groupBy { event ->
                if (event.partId > 1 && event.staffId <= 1) {
                    event.partId - 1
                } else if (event.staffId > 0) {
                    event.staffId - 1
                } else {
                    (event.partId - 1).coerceAtLeast(0)
                }
            }
            val staffOsmd = measOsmd.groupBy { it.staffIdx }

            val allStaves = (staffEvents.keys + staffOsmd.keys).distinct().sorted()

            for (staffIdx in allStaves) {
                val sEvents = (staffEvents[staffIdx] ?: emptyList())
                    .sortedWith(compareBy<MusicalEvent>({ it.tickPosition }, { -it.pitchMidi }))
                val sOsmd = (staffOsmd[staffIdx] ?: emptyList())
                    .sortedWith(compareBy<OsmdNoteElement>({ it.x }, { -it.midiNote }))

                // PASS 1: Exact SATB Voice + Exact Pitch in chronological visual order on this staff
                for (event in sEvents) {
                    if (coords.containsKey(event.eventId)) continue
                    val eventPart = event.satbVoice.firstOrNull()?.toString()?.uppercase() ?: "S"
                    val candidate = sOsmd.firstOrNull { elem ->
                        !usedOsmdIds.contains(elem.id) &&
                        elem.part.uppercase() == eventPart &&
                        elem.midiNote == event.pitchMidi
                    }
                    if (candidate != null) {
                        usedOsmdIds.add(candidate.id)
                        coords[event.eventId] = NoteCoordinate(candidate.x, candidate.y, candidate.width, candidate.height, candidate.id)
                    }
                }

                // PASS 2: Exact Pitch on this staff (if SATB label had slight variant)
                for (event in sEvents) {
                    if (coords.containsKey(event.eventId)) continue
                    val candidate = sOsmd.firstOrNull { elem ->
                        !usedOsmdIds.contains(elem.id) &&
                        elem.midiNote == event.pitchMidi
                    }
                    if (candidate != null) {
                        usedOsmdIds.add(candidate.id)
                        coords[event.eventId] = NoteCoordinate(candidate.x, candidate.y, candidate.width, candidate.height, candidate.id)
                    }
                }

                // PASS 3: Exact SATB Voice positional 1-to-1 match on this staff
                for (event in sEvents) {
                    if (coords.containsKey(event.eventId)) continue
                    val eventPart = event.satbVoice.firstOrNull()?.toString()?.uppercase() ?: "S"
                    val candidate = sOsmd.firstOrNull { elem ->
                        !usedOsmdIds.contains(elem.id) &&
                        elem.part.uppercase() == eventPart
                    }
                    if (candidate != null) {
                        usedOsmdIds.add(candidate.id)
                        coords[event.eventId] = NoteCoordinate(candidate.x, candidate.y, candidate.width, candidate.height, candidate.id)
                    }
                }

                // PASS 4: Positional 1-to-1 match on this staff
                for (event in sEvents) {
                    if (coords.containsKey(event.eventId)) continue
                    val candidate = sOsmd.firstOrNull { elem -> !usedOsmdIds.contains(elem.id) }
                    if (candidate != null) {
                        usedOsmdIds.add(candidate.id)
                        coords[event.eventId] = NoteCoordinate(candidate.x, candidate.y, candidate.width, candidate.height, candidate.id)
                    }
                }
            }
        }

        // PASS 3: Global Nearby Measure Index (+/- 1) + Same Staff + Same Pitch
        for (event in sortedEvents) {
            if (coords.containsKey(event.eventId)) continue
            val eventStaffIdx = if (event.partId > 1 && event.staffId <= 1) event.partId - 1 else if (event.staffId > 0) event.staffId - 1 else (event.partId - 1).coerceAtLeast(0)
            val candidate = osmdElements.firstOrNull { elem ->
                !usedOsmdIds.contains(elem.id) &&
                kotlin.math.abs(elem.measureIndex - event.measureIndex) <= 1 &&
                elem.staffIdx == eventStaffIdx &&
                elem.midiNote == event.pitchMidi
            }
            if (candidate != null) {
                usedOsmdIds.add(candidate.id)
                coords[event.eventId] = NoteCoordinate(candidate.x, candidate.y, candidate.width, candidate.height, candidate.id)
            }
        }

        // PASS 4: Same Measure Index + Same Pitch (Staff Fallback)
        for (event in sortedEvents) {
            if (coords.containsKey(event.eventId)) continue
            val candidate = osmdElements.firstOrNull { elem ->
                !usedOsmdIds.contains(elem.id) &&
                elem.measureIndex == event.measureIndex &&
                elem.midiNote == event.pitchMidi
            }
            if (candidate != null) {
                usedOsmdIds.add(candidate.id)
                coords[event.eventId] = NoteCoordinate(candidate.x, candidate.y, candidate.width, candidate.height, candidate.id)
            }
        }

        // PASS 5: Global Pitch Match (Chronological order)
        for (event in sortedEvents) {
            if (coords.containsKey(event.eventId)) continue
            val candidate = osmdElements.firstOrNull { elem ->
                !usedOsmdIds.contains(elem.id) &&
                elem.midiNote == event.pitchMidi
            }
            if (candidate != null) {
                usedOsmdIds.add(candidate.id)
                coords[event.eventId] = NoteCoordinate(candidate.x, candidate.y, candidate.width, candidate.height, candidate.id)
            }
        }

        coordinateMap.putAll(coords)

        // Compile match results to assess bipartite matching confidence
        val totalEvents = activeEvents.size
        val totalOsmdNodes = osmdElements.size

        val osmdByTickPitchGroup = osmdElements.groupBy { "t${it.tick}-m${it.midiNote}" }
        
        for (event in activeEvents) {
            val isMatched = coords.containsKey(event.eventId)
            val groupOsmd = osmdByTickPitchGroup["t${event.tickPosition}-m${event.pitchMidi}"]
                ?: osmdElements.filter { it.measureIndex == event.measureIndex && it.midiNote == event.pitchMidi }
            val isDuplicate = groupOsmd.size > 1
            matchResults.add(MatchResult(event.eventId, isMatched, isDuplicate))
        }

        val matchedPairs = matchResults.count { it.matched }
        val duplicateMatches = matchResults.count { it.duplicateMatch }

        val denominator = maxOf(totalEvents, totalOsmdNodes)
        val rawConfidence = if (denominator > 0) {
            (matchedPairs.toFloat() - duplicateMatches.toFloat() * 0.5f) / denominator.toFloat()
        } else 0f

        val nodesPerEvent = matchResults.groupBy { it.eventId }
            .map { it.value.size.toFloat() }
        val npeVariance = if (nodesPerEvent.size > 1) {
            val mean = nodesPerEvent.average().toFloat()
            nodesPerEvent.map { (it - mean) * (it - mean) }.average().toFloat()
        } else 0f
        val structurePenalty = (npeVariance * 0.1f).coerceAtMost(0.15f)

        syncConfidence = (rawConfidence - structurePenalty).coerceIn(0f, 1f)
        totalMappings = totalEvents
        successfulMappings = matchedPairs

        // Gate highlight mode by confidence thresholds
        highlightMode = when {
            syncConfidence >= 0.70f -> HighlightMode.NOTE_LEVEL
            syncConfidence >= 0.30f -> HighlightMode.MEASURE_LEVEL
            else -> HighlightMode.DISABLED
        }

        Log.i(TAG, "BuildMapping completed: layout=$layoutHash, events=$totalEvents, osmd=$totalOsmdNodes, " +
                "matched=$matchedPairs, duplicates=$duplicateMatches, rawConf=$rawConfidence, " +
                "penalty=$structurePenalty, finalConf=$syncConfidence, mode=$highlightMode")

        listener?.onSyncConfidenceChanged(syncConfidence)
        listener?.onHighlightModeChanged(highlightMode)
    }

    // ─── Data Classes ──────────────────────────────────────────────────
    /**
     * Screen coordinate of a rendered note in the WebView.
     */
    data class NoteCoordinate(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val id: Int = 0
    )

    /**
     * Raw note element extracted from OSMD via JavaScript.
     */
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
        val color: String = "#E91E63",
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
