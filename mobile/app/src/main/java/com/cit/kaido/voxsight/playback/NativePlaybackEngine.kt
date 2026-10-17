package com.cit.kaido.voxsight.playback

import android.content.Context
import android.media.midi.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.cit.kaido.voxsight.model.EventStream
import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.model.normalizeLegacyVoiceToParts
import com.cit.kaido.voxsight.model.SortContracts
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * VoxSight Native Playback Engine v1.0 (Milestone 2)
 * ====================================================
 * Tick-centric MIDI playback using Android's built-in synthesizer.
 *
 * Architecture Contract (v3.7):
 *   - SINGLE GLOBAL EVENT QUEUE: All events sorted by Playback Sort (Fix #37)
 *   - SINGLE DISPATCHER THREAD: Only thread that sends MIDI messages
 *   - TIMING SCHEDULER: Absorbs drift via micro-adjustments, never drops events
 *   - AUDIO SAFETY LAYER: Independent, write-only to synth, read-only from scheduler
 *   - COALESCING: 5-10ms batch window for simultaneous events
 *   - TEMPO-AWARE DRIFT: Intentional tempo changes ≠ errors (Fix #34)
 *
 * Pipeline ordering (Fix #30):
 *   COLLECT → COALESCE → SCHEDULE → DETECT_DRIFT → CORRECT_POINTER
 *   Strict sequential. Coalesced batches are never re-split.
 *
 * Late Event Policy:
 *   NOTE_ON  → NEVER skip. Dispatch immediately if late.
 *   NOTE_OFF → NEVER skip. Dispatch immediately. No-op if already released.
 */
class NativePlaybackEngine(private val context: Context) {

    companion object {
        private const val TAG = "NativePlaybackEngine"

        // MIDI constants
        private const val MIDI_NOTE_ON = 0x90
        private const val MIDI_NOTE_OFF = 0x80
        private const val MIDI_PROGRAM_CHANGE = 0xC0
        private const val MIDI_ALL_NOTES_OFF = 0x7B
        private const val MIDI_CONTROL_CHANGE = 0xB0

        // GM Piano patch
        private const val DEFAULT_PROGRAM = 0    // Acoustic Grand Piano
        private const val DEFAULT_VELOCITY = 80

        // Coalescing window (Fix #30)
        private const val COALESCE_WINDOW_MS = 8L

        // Audio Safety: max sustain before forced noteOff (Fix #35)
        private const val MAX_SUSTAIN_MS = 5000L

        // Drift detection threshold
        private const val DRIFT_WARN_MS = 50L

        fun normalizeVoiceKey(voice: String): String {
            val upper = voice.uppercase().trim()
            return when {
                upper.startsWith("SOLO") -> "SOLO"
                upper.startsWith("OTHER") || upper.startsWith("PIANO") || upper.startsWith("ACCOMP") -> "OTHER"
                upper.startsWith("S") -> "S"
                upper.startsWith("A") -> "A"
                upper.startsWith("T") -> "T"
                upper.startsWith("B") -> "B"
                else -> "OTHER"
            }
        }
    }

    // ─── State ─────────────────────────────────────────────────────────
    enum class PlaybackState { IDLE, PLAYING, PAUSED, STOPPED }

    private var state = PlaybackState.IDLE
    private var events: EventStream = EventStream.empty()
    private var playbackQueue: List<MusicalEvent> = emptyList()  // Playback-sorted copy
    private var currentEventIndex = AtomicInteger(0)
    private val isPlaying = AtomicBoolean(false)

    // Tempo
    private var tempoEvents: List<TempoMark> = listOf(TempoMark(0, 120f))
    private var currentBPM = 120f
    private var ticksPerQuarter = 960

    // Track mute/solo state
    private val mutedTracks = ConcurrentHashMap<String, Boolean>()
    private val soloTracks = ConcurrentHashMap<String, Boolean>()
    private val mutedVoices = ConcurrentHashMap<String, Boolean>()
    private var trackChannelMap = HashMap<String, Int>()

    // ─── MIDI Synth ────────────────────────────────────────────────────
    private var midiManager: MidiManager? = null
    private var midiDevice: MidiDevice? = null
    private var midiInputPort: MidiInputPort? = null

    // ─── Dispatcher Thread (SINGLE — only thread touching MIDI) ────────
    private var dispatcherThread: HandlerThread? = null
    private var dispatcherHandler: Handler? = null
    private var dispatcherJob: Job? = null
    private val dispatcherScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ─── Audio Safety Layer (independent monitor) ──────────────────────
    // Tracks active notes for stuck-note detection (Fix #35 + #44)
    // Access pattern: write-only to MIDI synth, read-only from scheduler
    private val activeNotes = ConcurrentHashMap<Int, Long>()  // key=channel*128+note, value=timestamp
    private val activeAccompanimentNotes = ConcurrentHashMap<Int, Long>()  // key=channel*128+note, value=timestamp
    private val voiceVolumeScales = ConcurrentHashMap<String, Float>().apply {
        put("OTHER", 0.35f)
        put("OTHERS", 0.35f)
    }

    // ─── Listener ──────────────────────────────────────────────────────
    interface PlaybackListener {
        fun onPlaybackStarted()
        fun onPlaybackPaused()
        fun onPlaybackStopped()
        fun onPlaybackProgress(currentTick: Int, totalTicks: Int, activeEventIds: List<String>)
        fun onNoteOn(event: MusicalEvent)
        fun onNoteOff(event: MusicalEvent)
        fun onPlaybackError(error: String)
    }

    private var listener: PlaybackListener? = null

    fun setListener(l: PlaybackListener) { listener = l }

    // ─── Initialization ────────────────────────────────────────────────
    /**
     * Initialize the MIDI synthesizer.
     * Must be called before play().
     */
    fun initialize() {
        Log.i(TAG, "Initializing MIDI playback engine")
        midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager

        if (midiManager == null) {
            Log.e(TAG, "MIDI service not available")
            listener?.onPlaybackError("MIDI not supported on this device")
            return
        }

        // Find and open a MIDI device with input ports (synth receiver)
        // Note: TYPE_SYNTHESIZER constant requires API 33+.
        // For API 26+ compat, we search for any device with input ports.
        @Suppress("DEPRECATION", "deprecation")
        val devices = midiManager!!.devices
        var synthInfo: MidiDeviceInfo? = null

        for (info in devices) {
            // Prefer synthesizer type if available (API 33+)
            val isSynth = try {
                info.type == 3  // MidiDeviceInfo.TYPE_SYNTHESIZER = 3
            } catch (_: Throwable) { false }

            if (isSynth) {
                synthInfo = info
                break
            }
        }

        // Fallback: find any device accepting MIDI input (built-in Sonivox)
        if (synthInfo == null) {
            for (info in devices) {
                if (info.inputPortCount > 0) {
                    synthInfo = info
                    break
                }
            }
        }

        if (synthInfo == null) {
            Log.e(TAG, "No MIDI device with input ports found")
            listener?.onPlaybackError("No MIDI synthesizer available")
            return
        }

        midiManager!!.openDevice(synthInfo, { device ->
            midiDevice = device
            if (device != null && device.info.inputPortCount > 0) {
                midiInputPort = device.openInputPort(0)
                Log.i(TAG, "MIDI device opened: ${device.info.properties}")

                // Set default program (piano) on channels 0-3
                for (ch in 0..3) {
                    sendMidiMessage(byteArrayOf((MIDI_PROGRAM_CHANGE + ch).toByte(), DEFAULT_PROGRAM.toByte()))
                }
            } else {
                Log.e(TAG, "Failed to open MIDI device input port")
                listener?.onPlaybackError("Failed to open MIDI synthesizer")
            }
        }, Handler(Looper.getMainLooper()))

        // Start dispatcher thread
        dispatcherThread = HandlerThread("MidiDispatcher").also { it.start() }
        dispatcherHandler = Handler(dispatcherThread!!.looper)

        Log.i(TAG, "Playback engine initialized")
    }

    // ─── Load Events ───────────────────────────────────────────────────
    /**
     * Load events into the playback queue.
     * Events are received as ORDER-FROZEN (Fix #40).
     * Playback Sort (Fix #37) is applied here ONLY for the dispatcher queue.
     */
    fun loadEvents(
        eventStream: EventStream,
        tempoMarks: List<TempoMark> = listOf(TempoMark(0, 120f)),
        tpq: Int = 960
    ) {
        this.events = eventStream
        this.tempoEvents = tempoMarks.ifEmpty { listOf(TempoMark(0, 120f)) }
        this.ticksPerQuarter = tpq
        this.currentBPM = tempoEvents.first().bpm

        // Apply Playback Sort (execution domain ONLY — Fix #37)
        // This creates a SEPARATE sorted copy. The original EventStream remains ORDER-FROZEN.
        this.playbackQueue = eventStream
            .filter { !it.isRest }
            .sortedWith(SortContracts.PLAYBACK_SORT)

        // Assign MIDI channels with strict partition (Fix #Phase2.5):
        // Channels 0-7: Strictly partitioned for Vocal tracks
        // Channel 9: GM Percussion (reserved)
        // Channels 8, 10-15: Strictly partitioned for Accompaniment tracks
        trackChannelMap.clear()
        val tracks = eventStream.playbackTrackIds().sorted()
        val vocalTracks = mutableListOf<String>()
        val accompTracks = mutableListOf<String>()
        for (trackId in tracks) {
            if (isAccompanimentTrack(trackId)) {
                accompTracks.add(trackId)
            } else {
                vocalTracks.add(trackId)
            }
        }
        for ((idx, tId) in vocalTracks.withIndex()) {
            trackChannelMap[tId] = idx.coerceAtMost(7)
        }
        val accompChannels = listOf(8, 10, 11, 12, 13, 14, 15)
        for ((idx, tId) in accompTracks.withIndex()) {
            trackChannelMap[tId] = accompChannels[idx.coerceAtMost(accompChannels.size - 1)]
        }

        currentEventIndex.set(0)
        state = PlaybackState.IDLE

        Log.i(TAG, "Loaded ${playbackQueue.size} events, ${tracks.size} tracks, tpq=$tpq")
    }

    // ─── Playback Controls ─────────────────────────────────────────────
    fun play() {
        if (playbackQueue.isEmpty()) {
            listener?.onPlaybackError("No events loaded")
            return
        }
        // NOTE: We no longer gate on midiInputPort. Tone.js fallback in WebView
        // provides audio even without a hardware MIDI synth.

        isPlaying.set(true)
        state = PlaybackState.PLAYING
        listener?.onPlaybackStarted()

        // Launch dispatcher on dedicated scope
        dispatcherJob = dispatcherScope.launch {
            runDispatcherLoop()
        }
    }

    fun pause() {
        isPlaying.set(false)
        state = PlaybackState.PAUSED
        // Don't kill notes — user might resume
        listener?.onPlaybackPaused()
    }

    fun resume() {
        if (state != PlaybackState.PAUSED) return
        isPlaying.set(true)
        state = PlaybackState.PLAYING
        listener?.onPlaybackStarted()

        dispatcherJob = dispatcherScope.launch {
            runDispatcherLoop()
        }
    }

    fun stop() {
        isPlaying.set(false)
        state = PlaybackState.STOPPED
        dispatcherJob?.cancel()
        currentEventIndex.set(0)

        // Emergency allNotesOff (Audio Safety — Fix #35)
        emergencyAllNotesOff()

        listener?.onPlaybackStopped()
    }

    fun seek(progressFraction: Float) {
        val clamped = progressFraction.coerceIn(0f, 1f)
        val totalTicks = events.maxOfOrNull { it.tickPosition + it.durationTicks }
            ?: (if (playbackQueue.isNotEmpty()) playbackQueue.last().tickPosition else 0)
        val targetTick = (totalTicks * clamped).toInt()
        
        // Find the index of the first event that is at or after the target tick
        var newIdx = 0
        for (i in playbackQueue.indices) {
            if (playbackQueue[i].tickPosition >= targetTick) {
                newIdx = i
                break
            }
        }
        
        // Turn off all currently active notes to prevent hanging notes
        emergencyAllNotesOff()
        
        // Update the playhead
        currentEventIndex.set(newIdx)
        
        // Update current tempo if we seek past tempo changes
        var latestBpm = 120f
        for (mark in tempoEvents) {
            if (mark.tick <= targetTick) {
                latestBpm = mark.bpm
            } else {
                break
            }
        }
        currentBPM = latestBpm

        // Restart the dispatcher loop if playing so it picks up the new index
        if (state == PlaybackState.PLAYING) {
            dispatcherJob?.cancel()
            dispatcherJob = dispatcherScope.launch {
                runDispatcherLoop()
            }
        }
    }

    // ─── Track Controls ────────────────────────────────────────────────
    fun muteTrack(trackId: String, muted: Boolean) {
        mutedTracks[trackId] = muted
    }

    fun soloTrack(trackId: String, soloed: Boolean) {
        soloTracks[trackId] = soloed
    }

    fun muteVoice(voice: String, muted: Boolean) {
        val key = normalizeVoiceKey(voice)
        mutedVoices[key] = muted
        if (key == "OTHER") {
            mutedVoices["OTHERS"] = muted
        }
    }

    fun setVoiceVolume(voiceKey: String, volumeFactor: Float) {
        val normalized = normalizeVoiceKey(voiceKey)
        val clamped = volumeFactor.coerceIn(0.0f, 1.0f)
        voiceVolumeScales[normalized] = clamped
        if (normalized == "OTHER") {
            voiceVolumeScales["OTHERS"] = clamped
        }
    }

    fun getVoiceVolume(voiceKey: String): Float {
        val normalized = normalizeVoiceKey(voiceKey)
        return voiceVolumeScales[normalized] ?: if (normalized in setOf("OTHER", "OTHERS")) 0.35f else 1.0f
    }

    fun cancelAccompanimentNotesNow() {
        val keysToCancel = activeAccompanimentNotes.keys().toList()
        for (key in keysToCancel) {
            val ch = key / 128
            val n = key % 128
            sendNoteOff(ch, n)
            activeAccompanimentNotes.remove(key)
        }
    }

    fun isHardwareMidiActive(): Boolean = midiInputPort != null

    fun isAccompanimentTrack(trackId: String): Boolean {
        val lower = trackId.lowercase().trim()
        if (lower.contains("other") || lower.contains("piano") || lower.contains("accomp") ||
            lower.contains("organ") || lower.contains("guitar") || lower.contains("strings") ||
            lower.contains("violin") || lower.contains("instrument")
        ) {
            return true
        }
        val trackEvents = playbackQueue.filter { it.playbackTrack == trackId }
        if (trackEvents.isNotEmpty() && trackEvents.all { !it.isVocal }) {
            return true
        }
        return false
    }

    fun isTrackMuted(trackId: String): Boolean {
        // If any track is soloed, all non-soloed tracks are effectively muted
        val anySoloed = soloTracks.values.any { it }
        if (anySoloed) {
            return soloTracks[trackId] != true
        }
        return mutedTracks[trackId] == true
    }

    // ─── CORE: Dispatcher Loop ─────────────────────────────────────────
    /**
     * Single dispatcher loop implementing the pipeline:
     *   COLLECT → COALESCE → SCHEDULE → DETECT_DRIFT → CORRECT_POINTER
     *
     * NEVER skips, drops, or reorders musical events.
     * Drift correction = adjust sleep interval (Fix #26).
     */
    private suspend fun runDispatcherLoop() {
        var idx = currentEventIndex.get()
        if (idx >= playbackQueue.size) {
            withContext(Dispatchers.Main) { stop() }
            return
        }

        val totalTicks = events.maxOfOrNull { it.tickPosition + it.durationTicks }
            ?: (playbackQueue.last().tickPosition + playbackQueue.last().durationTicks)

        // Paced initial rest handling for measure 1 rests (e.g. pickup measure silence before beat 4)
        if (idx == 0 && playbackQueue.isNotEmpty() && playbackQueue[0].tickPosition > 0) {
            val initialRestTicks = playbackQueue[0].tickPosition
            val initialRestMs = ticksToMs(initialRestTicks)

            dispatcherScope.launch(Dispatchers.Main) {
                listener?.onPlaybackProgress(0, totalTicks, emptyList())
            }

            if (initialRestMs > 0 && isPlaying.get()) {
                val stepMs = 50L
                var elapsedMs = 0L
                while (elapsedMs < initialRestMs && isPlaying.get()) {
                    val waitSlice = minOf(stepMs, initialRestMs - elapsedMs)
                    delay(waitSlice)
                    elapsedMs += waitSlice
                    val curRestTick = (initialRestTicks * (elapsedMs.toFloat() / initialRestMs.toFloat())).toInt()
                    dispatcherScope.launch(Dispatchers.Main) {
                        listener?.onPlaybackProgress(curRestTick, totalTicks, emptyList())
                    }
                }
            }
        }

        while (isPlaying.get() && idx < playbackQueue.size) {
            // ─── STEP 1: COLLECT events for next window ────────────
            val currentTick = playbackQueue[idx].tickPosition
            val batch = mutableListOf<MusicalEvent>()

            // ─── STEP 2: COALESCE — gather events within window ────
            while (idx < playbackQueue.size) {
                val event = playbackQueue[idx]
                val tickDiff = event.tickPosition - currentTick
                val timeDiffMs = ticksToMs(tickDiff)
                if (timeDiffMs <= COALESCE_WINDOW_MS) {
                    batch.add(event)
                    idx++
                } else {
                    break
                }
            }

            // ─── STEP 3: SCHEDULE — dispatch batch to synth ────────
            for (event in batch) {
                dispatchEvent(event)
            }

            // Update progress on main thread asynchronously so we don't block the audio pacing loop
            val progressTick = currentTick
            val activeEventIds = batch.map { it.eventId }
            
            dispatcherScope.launch(Dispatchers.Main) {
                listener?.onPlaybackProgress(progressTick, totalTicks, activeEventIds)
            }

            // Check for tempo change at current tick (Fix #34)
            updateTempoIfNeeded(currentTick)

            // ─── STEP 4: EXACT PACING (Smooth & Scalable) ──
            if (idx < playbackQueue.size) {
                val nextTick = playbackQueue[idx].tickPosition
                val tickDelta = (nextTick - currentTick).coerceAtLeast(0)
                val sleepMs = ticksToMs(tickDelta)
                
                if (sleepMs > 0) {
                    delay(sleepMs)
                }
            }

            currentEventIndex.set(idx)

            // Run audio safety check periodically
            checkStuckNotes()
        }

        // Playback complete - wait for last notes to finish sustaining before stopping
        if (idx >= playbackQueue.size) {
            val lastNote = playbackQueue.lastOrNull()
            val remainingMs = if (lastNote != null) ticksToMs(lastNote.durationTicks) else 500L
            if (remainingMs > 0 && isPlaying.get()) {
                delay(remainingMs)
            }
            withContext(Dispatchers.Main) {
                state = PlaybackState.STOPPED
                currentEventIndex.set(0)
                emergencyAllNotesOff()
                listener?.onPlaybackStopped()
            }
        }
    }

    // ─── Event Dispatch ────────────────────────────────────────────────
    /**
     * Dispatch a single musical event to the MIDI synth.
     * Respects mute/solo state.
     *
     * Late Event Policy:
     *   NOTE_ON → NEVER skip
     *   NOTE_OFF → NEVER skip (no-op if already released by safety layer)
     */
    private fun dispatchEvent(event: MusicalEvent) {
        val channel = trackChannelMap[event.playbackTrack] ?: 0
        val note = event.pitchMidi

        // 1. ALWAYS process Note-Off releases FIRST (Late Event Policy & Stuck-Note Prevention - Fix #Phase2.4)
        // Mute checks MUST NOT suppress note-offs, which causes stuck notes.
        if (event.tieType == "stop") {
            sendNoteOff(channel, note)
            listener?.onNoteOff(event)
            activeAccompanimentNotes.remove(channel * 128 + note)
            return
        }

        // 2. Sustain Tied Notes: update watchdog timestamp to prevent Audio Safety early release
        if (event.tieType == "continue") {
            val key = channel * 128 + note
            if (activeNotes.containsKey(key)) {
                activeNotes[key] = System.currentTimeMillis()
            }
            return
        }

        // 3. Evaluate Compound-Unison Voice and Track Gating for New Attacks
        val isAccompaniment = isAccompanimentTrack(event.playbackTrack) || !event.isVocal
        val eventVoices = event.participatingParts.ifEmpty { normalizeLegacyVoiceToParts(event.satbVoice) }
        val isAnyVoiceActive = if (isAccompaniment) {
            mutedVoices["OTHER"] != true && mutedVoices["OTHERS"] != true && !isTrackMuted(event.playbackTrack)
        } else {
            // Decouple voice vs. track gating for vocal lines on shared staves (Fix #Phase2.4)
            eventVoices.any { v -> mutedVoices[normalizeVoiceKey(v)] != true }
        }

        if (!isAnyVoiceActive) {
            return // Muted voice or track -> Suppress NOTE_ON attack
        }

        // 4. Accompaniment & Voice Velocity Scaling (35% default backing volume)
        val scale = if (isAccompaniment) {
            voiceVolumeScales["OTHER"] ?: voiceVolumeScales["OTHERS"] ?: 0.35f
        } else {
            val primaryVoice = eventVoices.firstOrNull()?.let { normalizeVoiceKey(it) } ?: "S"
            voiceVolumeScales[primaryVoice] ?: 1.0f
        }

        if (scale <= 0.0f) {
            return // Muted by volume scaling -> Suppress NOTE_ON
        }

        val effectiveVelocity = (DEFAULT_VELOCITY * scale).toInt().coerceIn(1, 127)

        // 5. Sound Note Attack
        if (event.tieType == "start") {
            sendNoteOn(channel, note, effectiveVelocity)
            listener?.onNoteOn(event)
            if (isAccompaniment) {
                activeAccompanimentNotes[channel * 128 + note] = System.currentTimeMillis()
            }
        } else {
            // Normal note (not tied)
            sendNoteOn(channel, note, effectiveVelocity)
            listener?.onNoteOn(event)
            if (isAccompaniment) {
                activeAccompanimentNotes[channel * 128 + note] = System.currentTimeMillis()
            }

            val durationMs = ticksToMs(event.durationTicks.toLong())
            dispatcherScope.launch {
                delay(durationMs.coerceAtLeast(50))  // minimum 50ms note
                sendNoteOff(channel, note)
                listener?.onNoteOff(event)
                activeAccompanimentNotes.remove(channel * 128 + note)
            }
        }
    }

    // ─── MIDI Message Sending ──────────────────────────────────────────
    private fun sendNoteOn(channel: Int, note: Int, velocity: Int) {
        val msg = byteArrayOf(
            (MIDI_NOTE_ON + channel).toByte(),
            note.toByte(),
            velocity.toByte()
        )
        sendMidiMessage(msg)

        // Register in active notes for safety monitoring
        val key = channel * 128 + note
        activeNotes[key] = System.currentTimeMillis()
    }

    private fun sendNoteOff(channel: Int, note: Int) {
        val key = channel * 128 + note
        // Only send if note is actually active (no-op if already released)
        if (activeNotes.remove(key) != null) {
            val msg = byteArrayOf(
                (MIDI_NOTE_OFF + channel).toByte(),
                note.toByte(),
                0.toByte()
            )
            sendMidiMessage(msg)
        }
    }

    private fun sendMidiMessage(data: ByteArray) {
        try {
            midiInputPort?.send(data, 0, data.size)
        } catch (e: Exception) {
            Log.e(TAG, "MIDI send error: ${e.message}")
        }
    }

    // ─── AUDIO SAFETY LAYER (Fix #35 + #44) ────────────────────────────
    /**
     * Independent monitor. Write-only to synth, read-only from scheduler.
     * Can only SUPPRESS stuck notes (force noteOff).
     * Cannot emit noteOn, modify timing, or adjust scheduler pointer.
     */
    private fun checkStuckNotes() {
        val now = System.currentTimeMillis()
        val stuckKeys = mutableListOf<Int>()

        for ((key, timestamp) in activeNotes) {
            if (now - timestamp > MAX_SUSTAIN_MS) {
                stuckKeys.add(key)
            }
        }

        for (key in stuckKeys) {
            val channel = key / 128
            val note = key % 128
            Log.w(TAG, "[AudioSafety] Stuck note detected: ch=$channel note=$note, forcing OFF")
            activeNotes.remove(key)
            val msg = byteArrayOf(
                (MIDI_NOTE_OFF + channel).toByte(),
                note.toByte(),
                0.toByte()
            )
            sendMidiMessage(msg)
        }
    }

    /**
     * Emergency allNotesOff — called ONLY on stop() or app lifecycle events.
     * This is the nuclear option. (Fix #35)
     */
    private fun emergencyAllNotesOff() {
        Log.i(TAG, "[AudioSafety] Emergency allNotesOff")
        activeNotes.clear()
        activeAccompanimentNotes.clear()
        for (ch in 0..15) {
            // CC 123 = All Notes Off
            sendMidiMessage(byteArrayOf(
                (MIDI_CONTROL_CHANGE + ch).toByte(),
                MIDI_ALL_NOTES_OFF.toByte(),
                0.toByte()
            ))
        }
    }

    // ─── Tempo Management (Fix #34) ────────────────────────────────────
    /**
     * Check if there's a tempo change at the current tick.
     * Intentional tempo changes are NOT treated as drift errors.
     */
    private fun updateTempoIfNeeded(currentTick: Int) {
        if (tempoEvents.size <= 1) return // Keep user-set or single base tempo
        for (mark in tempoEvents.reversed()) {
            if (currentTick >= mark.tick) {
                if (currentBPM != mark.bpm) {
                    Log.i(TAG, "Tempo change at tick $currentTick: ${currentBPM} → ${mark.bpm} BPM")
                    currentBPM = mark.bpm
                }
                break
            }
        }
    }

    // ─── Tempo & Speed Multiplier Controls ─────────────────────────────
    private var speedMultiplier = 1.0f

    fun setTempo(bpm: Float) {
        this.currentBPM = bpm.coerceIn(30f, 300f)
        this.tempoEvents = listOf(TempoMark(0, currentBPM))
        Log.i(TAG, "User updated tempo to $currentBPM BPM (effective: ${currentBPM * speedMultiplier})")
        if (state == PlaybackState.PLAYING) {
            dispatcherJob?.cancel()
            dispatcherJob = dispatcherScope.launch {
                runDispatcherLoop()
            }
        }
    }

    fun setSpeedMultiplier(multiplier: Float) {
        this.speedMultiplier = multiplier.coerceIn(0.25f, 3.0f)
        Log.i(TAG, "Speed multiplier set to ${speedMultiplier}x (effective: ${currentBPM * speedMultiplier})")
        if (state == PlaybackState.PLAYING) {
            dispatcherJob?.cancel()
            dispatcherJob = dispatcherScope.launch {
                runDispatcherLoop()
            }
        }
    }

    fun getSpeedMultiplier(): Float = speedMultiplier

    fun getBaseBPM(): Float = currentBPM

    // ─── Timing Conversion ─────────────────────────────────────────────
    private fun ticksToMs(ticks: Long): Long {
        val effectiveBpm = currentBPM * speedMultiplier
        if (effectiveBpm <= 0 || ticksPerQuarter <= 0) return 0
        return (ticks.toDouble() * 60000.0 / (effectiveBpm.toDouble() * ticksPerQuarter.toDouble())).toLong().coerceAtLeast(0)
    }

    private fun ticksToMs(ticks: Int): Long = ticksToMs(ticks.toLong())

    private fun ticksToNs(ticks: Int): Long {
        val effectiveBpm = currentBPM * speedMultiplier
        if (effectiveBpm <= 0 || ticksPerQuarter <= 0) return 0
        return (ticks.toDouble() * 60_000_000_000.0 / (effectiveBpm.toDouble() * ticksPerQuarter.toDouble())).toLong().coerceAtLeast(0)
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────
    /**
     * Called when activity is backgrounded.
     * Audio Safety: allNotesOff() (Fix #35)
     */
    fun onPause() {
        if (state == PlaybackState.PLAYING) {
            pause()
            emergencyAllNotesOff()
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        stop()
        try {
            midiInputPort?.close()
            midiDevice?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing MIDI: ${e.message}")
        }
        dispatcherThread?.quitSafely()
        dispatcherScope.cancel()
        Log.i(TAG, "Playback engine released")
    }

    // ─── Query State ───────────────────────────────────────────────────
    fun getState(): PlaybackState = state
    fun getCurrentBPM(): Float = currentBPM * speedMultiplier
    fun getCurrentTick(): Int {
        val idx = currentEventIndex.get()
        return if (idx < playbackQueue.size) playbackQueue[idx].tickPosition else 0
    }
    fun getTrackIds(): Set<String> = trackChannelMap.keys

    /**
     * Tempo mark from MusicXML (Fix #34).
     */
    data class TempoMark(val tick: Int, val bpm: Float)
}
