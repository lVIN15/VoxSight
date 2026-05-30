package com.cit.kaido.voxsight.playback

import android.content.Context
import android.media.midi.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.cit.kaido.voxsight.model.EventStream
import com.cit.kaido.voxsight.model.MusicalEvent
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

        // Assign MIDI channels to playback tracks
        trackChannelMap.clear()
        val tracks = eventStream.playbackTrackIds().sorted()
        for ((idx, trackId) in tracks.withIndex()) {
            // Use channels 0-8, skip channel 9 (GM percussion)
            val channel = if (idx < 9) idx else idx + 1
            trackChannelMap[trackId] = channel.coerceAtMost(15)
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
        val targetTick = if (playbackQueue.isNotEmpty()) {
            (playbackQueue.last().tickPosition * clamped).toInt()
        } else {
            0
        }
        
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
        mutedVoices[voice.uppercase()] = muted
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

        var expectedWallTimeNs = System.nanoTime()

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
            val totalTicks = if (playbackQueue.isNotEmpty())
                playbackQueue.last().tickPosition else 1
            val activeEventIds = batch.map { it.eventId }
            
            dispatcherScope.launch(Dispatchers.Main) {
                listener?.onPlaybackProgress(progressTick, totalTicks, activeEventIds)
            }

            // Check for tempo change at current tick (Fix #34)
            updateTempoIfNeeded(currentTick)

            // ─── STEP 4: EXACT PACING (Fix for speed oscillation) ──
            if (idx < playbackQueue.size) {
                val nextTick = playbackQueue[idx].tickPosition
                val tickDelta = nextTick - currentTick
                val sleepNs = ticksToNs(tickDelta)
                
                expectedWallTimeNs += sleepNs
                
                var nowNs = System.nanoTime()
                var actualSleepNs = expectedWallTimeNs - nowNs
                
                if (actualSleepNs > 0) {
                    if (actualSleepNs > 10_000_000) {
                        delay((actualSleepNs / 1_000_000) - 5)
                    }
                    while (System.nanoTime() < expectedWallTimeNs && isPlaying.get()) {
                        // Busy wait for precision
                    }
                } else {
                    // Drift compensation: if we are late, do not aggressively try to catch up 
                    // by playing immediately. Reset expected wall time to prevent speed oscillation.
                    if (actualSleepNs < -5_000_000) {
                        expectedWallTimeNs = System.nanoTime()
                    }
                }
            }

            currentEventIndex.set(idx)

            // Run audio safety check periodically
            checkStuckNotes()
        }

        // Playback complete
        if (idx >= playbackQueue.size) {
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
        val velocity = DEFAULT_VELOCITY

        // Check mute/solo at track level and voice level
        if (isTrackMuted(event.playbackTrack)) return
        val voiceUpper = event.satbVoice.uppercase()
        val isVoiceMuted = mutedVoices.keys.any { voiceUpper.startsWith(it) } && mutedVoices.entries.find { voiceUpper.startsWith(it.key) }?.value == true
        if (isVoiceMuted) return

        when (event.tieType) {
            "stop" -> {
                // End of a tied note — trigger NOTE_OFF
                sendNoteOff(channel, note)
                listener?.onNoteOff(event)
            }
            "continue" -> {
                // Middle of a tied note — keep sustaining, do nothing (no new attack)
                // Proactively update active note timestamp to prevent Audio Safety early release
                val key = channel * 128 + note
                if (activeNotes.containsKey(key)) {
                    activeNotes[key] = System.currentTimeMillis()
                }
            }
            "start" -> {
                // Start of a tied note — trigger NOTE_ON, do not schedule NOTE_OFF (stop event will handle it)
                sendNoteOn(channel, note, velocity)
                listener?.onNoteOn(event)
            }
            else -> {
                // Normal note (not tied) — trigger NOTE_ON and schedule NOTE_OFF after duration
                sendNoteOn(channel, note, velocity)
                listener?.onNoteOn(event)

                val durationMs = ticksToMs(event.durationTicks.toLong())
                dispatcherScope.launch {
                    delay(durationMs.coerceAtLeast(50))  // minimum 50ms note
                    // NEVER skip NOTE_OFF (Late Event Policy)
                    // No-op if already released by Audio Safety Layer
                    sendNoteOff(channel, note)
                    listener?.onNoteOff(event)
                }
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

    // ─── Timing Conversion ─────────────────────────────────────────────
    private fun ticksToMs(ticks: Long): Long {
        if (currentBPM <= 0 || ticksPerQuarter <= 0) return 0
        return (ticks.toDouble() * 60000.0 / (currentBPM.toDouble() * ticksPerQuarter.toDouble())).toLong().coerceAtLeast(0)
    }

    private fun ticksToMs(ticks: Int): Long = ticksToMs(ticks.toLong())

    private fun ticksToNs(ticks: Int): Long {
        if (currentBPM <= 0 || ticksPerQuarter <= 0) return 0
        return (ticks.toDouble() * 60_000_000_000.0 / (currentBPM.toDouble() * ticksPerQuarter.toDouble())).toLong().coerceAtLeast(0)
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
    fun getCurrentBPM(): Float = currentBPM
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
