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

        // Group active events and OSMD elements by pitch (highly reliable)
        val activeEvents = events.filter { !it.isRest }
        val eventsByPitch = activeEvents.groupBy { it.pitchMidi }
        val osmdByPitch = osmdElements.groupBy { it.midiNote }

        val allPitches = (eventsByPitch.keys + osmdByPitch.keys).toSet()

        for (pitch in allPitches) {
            // Sort both lists chronologically.
            // For unison notes at the EXACT SAME TICK across different staves (e.g. Alto C4 vs Tenor C4),
            // we use staffId (1=Treble, 2=Bass) for events, and Y-coordinate (smaller=higher) for visual notes.
            // This completely eliminates any Alto/Tenor color swaps!
            val groupEvents = (eventsByPitch[pitch] ?: emptyList())
                .sortedWith(compareBy({ it.tickPosition }, { it.staffId }))
            val groupOsmd = (osmdByPitch[pitch] ?: emptyList())
                .sortedWith(compareBy({ it.id }, { it.y }))
            
            // Sequential pitch matching: Nth event matches Nth OSMD note.
            // This is 100% immune to OMR timing drift, missing beats, and tick resolution differences.
            val limit = minOf(groupEvents.size, groupOsmd.size)
            for (i in 0 until limit) {
                val event = groupEvents[i]
                val elem = groupOsmd[i]
                coords[event.eventId] = NoteCoordinate(elem.x, elem.y, elem.width, elem.height, elem.id)
            }
        }

        coordinateMap.putAll(coords)

        // Compile match results to assess bipartite matching confidence
        val totalEvents = activeEvents.size
        val totalOsmdNodes = osmdElements.size

        val osmdByTickPitch = osmdElements.groupBy { "t${it.tick}-m${it.midiNote}" }
        
        for (event in activeEvents) {
            val isMatched = coords.containsKey(event.eventId)

            // A duplicate match occurs if multiple OSMD nodes were found for this tick+pitch
            val groupOsmd = osmdByTickPitch["t${event.tickPosition}-m${event.pitchMidi}"] ?: emptyList()
            val isDuplicate = groupOsmd.size > 1

            matchResults.add(MatchResult(event.eventId, isMatched, isDuplicate))
        }

        val matchedPairs = matchResults.count { it.matched }
        val duplicateMatches = matchResults.count { it.duplicateMatch }

        // Bipartite matching confidence:
        // confidence = matched_pairs / max(total_events, total_nodes)
        // Penalize duplicates (one event matched to multiple nodes)
        val denominator = maxOf(totalEvents, totalOsmdNodes)
        val rawConfidence = if (denominator > 0) {
            (matchedPairs.toFloat() - duplicateMatches.toFloat() * 0.5f) / denominator.toFloat()
        } else 0f

        // Structure penalty factor (Fix #32):
        // Prevents confidence inflation in dense engraving.
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
        val eventId: String? = null,  // May not be available
        val tick: Int,
        val midiNote: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    data class NoteHighlightData(
        val eventId: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )
}
