package com.cit.kaido.voxsight.ui.screens.practice

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.cit.kaido.voxsight.model.EventStream
import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.network.ScoreMetadata
import com.cit.kaido.voxsight.playback.NativePlaybackEngine
import com.cit.kaido.voxsight.playback.SyncManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MidiPlaybackEngine(
    score: MusicXmlScore?,
    tempo: Int = 120,
    isMicEnabled: Boolean = false,
    selectedVoicePart: String? = null,
    pitchAttempts: List<com.cit.kaido.voxsight.pitch.PitchAttempt> = emptyList(),
    onReady: (MidiPlayerController) -> Unit,
    onScoreLoaded: (Int) -> Unit = {},
    onProgress: (Float) -> Unit = {},
    onPlaybackComplete: () -> Unit = {},
    onNoteOn: (MusicalEvent) -> Unit = {},
    onWaitPitch: suspend (List<MusicalEvent>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Create the controller once
    val controller = remember {
        MidiPlayerController(context)
    }

    controller.isMicEnabled = isMicEnabled
    controller.selectedVoicePart = selectedVoicePart
    controller.pitchAttempts = pitchAttempts

    // Update callbacks when they change
    DisposableEffect(onProgress, onPlaybackComplete, onScoreLoaded, onNoteOn, onWaitPitch) {
        controller.onProgressCallback = onProgress
        controller.onCompleteCallback = onPlaybackComplete
        controller.onScoreLoadedCallback = onScoreLoaded
        controller.onNoteOnCallback = onNoteOn
        controller.onWaitPitchCallback = onWaitPitch
        onDispose { }
    }

    // Refresh highlights dynamically when isMicEnabled, selectedVoicePart, or pitchAttempts change
    LaunchedEffect(isMicEnabled, selectedVoicePart, pitchAttempts) {
        controller.isMicEnabled = isMicEnabled
        controller.selectedVoicePart = selectedVoicePart
        controller.pitchAttempts = pitchAttempts
        controller.refreshHighlights()
    }

    // Lifecycle cleanup
    DisposableEffect(Unit) {
        onDispose {
            controller.release()
        }
    }

    // Load score only when the score identity/content changes
    LaunchedEffect(score?.title, score?.rawXml) {
        if (score != null) {
            controller.loadScore(score)
        }
    }

    AndroidView(
        factory = {
            controller.webView
        },
        update = {
            // Score loading and lifecycle are handled reactively by LaunchedEffect
        },
        modifier = modifier
    )

    LaunchedEffect(Unit) {
        onReady(controller)
    }
}

open class MidiPlayerController(
    private val context: Context
) : NativePlaybackEngine.PlaybackListener, SyncManager.SyncListener {
    
    val webView = WebView(context)
    private val playbackEngine = NativePlaybackEngine(context)
    private val syncManager = SyncManager()
    private val gson = Gson()
    
    var onProgressCallback: (Float) -> Unit = {}
    var onCompleteCallback: () -> Unit = {}
    var onScoreLoadedCallback: (Int) -> Unit = {}
    var onNoteOnCallback: (MusicalEvent) -> Unit = {}
    var onWaitPitchCallback: suspend (List<MusicalEvent>) -> Unit = {}
    var onPlaybackStartedCallback: () -> Unit = {}
    var onPlaybackPausedCallback: () -> Unit = {}
    
    // ─── Diagnostics State (Compose Reactive) ─────────────────────────
    var eventsCount by mutableStateOf(0)
    var osmdNotesCount by mutableStateOf(0)
    var mappedNotesCount by mutableStateOf(0)
    var syncConfidence by mutableStateOf(1.0f)
    var playbackState by mutableStateOf("IDLE")
    var lastMidiEvent by mutableStateOf<String?>(null)
    var mutedVoicesList by mutableStateOf(listOf<String>())
    var isMicEnabled by mutableStateOf(false)
    var selectedVoicePart by mutableStateOf<String?>(null)
    var pitchAttempts by mutableStateOf(listOf<com.cit.kaido.voxsight.pitch.PitchAttempt>())
    
    private var eventStream: EventStream = EventStream.empty()
    private var isPageFinished = false
    private var isRendered = false
    private var currentScoreXml: String? = null
    private var loadedScoreKey: String? = null
    
    open val totalSeconds: Int get() = calculatedTotalSeconds
    
    private var visualFocusPart: String? = null
    private var currentTpq = 960
    private var currentBpm = 120f
    private var calculatedTotalSeconds = 0

    init {
        playbackEngine.setListener(this)

        playbackEngine.initialize()
        
        syncManager.setListener(this)
        
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = false
            
            // Enable pinch-to-zoom
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    android.util.Log.d("VoxSightWebView", "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                }
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                isPageFinished = true
                if (currentScoreXml != null && !isRendered) {
                    renderScore(currentScoreXml!!)
                }
            }
        }

        webView.addJavascriptInterface(VoxSightJsBridge(), "VoxSightBridge")
        // Load the new native renderer using OSMD + Canvas highlights
        webView.loadUrl("file:///android_asset/renderer.html")
        // Transparent background to let Compose Surface show through
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    
    @Suppress("UNCHECKED_CAST")
    fun loadScore(score: MusicXmlScore) {
        val scoreKey = "${score.title}_${score.rawXml.hashCode()}"
        if (scoreKey == loadedScoreKey && isRendered) {
            return
        }
        loadedScoreKey = scoreKey
        currentScoreXml = score.rawXml
        isRendered = false
        
        // Parse events with automatic fallback generation
        val resolvedEventsJson = score.eventsJson?.takeIf { it.isNotBlank() && it != "[]" }
            ?: generateEventsJsonFromScoreParts(score.parts, score.divisions)

        val eventType = object : TypeToken<List<MusicalEvent>>() {}.type
        val events: List<MusicalEvent> = try {
            gson.fromJson(resolvedEventsJson, eventType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        eventStream = EventStream.freeze(events)
        eventsCount = events.size
        
        // We'll calculate actual total seconds after extracting tempo metadata
        
        // Parse metadata & determine true ticks-per-quarter (divisions)
        val tempoMarks = mutableListOf<NativePlaybackEngine.TempoMark>()
        val defaultEventTpq = events.firstOrNull()?.ticksPerQuarter ?: score.divisions.takeIf { it > 0 } ?: 960
        var calculatedTpq = defaultEventTpq

        if (score.metadataJson != null && score.metadataJson != "{}") {
            try {
                val metadata = gson.fromJson(score.metadataJson, ScoreMetadata::class.java)
                if (metadata.ticksPerQuarter > 0) {
                    calculatedTpq = metadata.ticksPerQuarter
                }
                metadata.tempoEvents.forEach { t ->
                    tempoMarks.add(NativePlaybackEngine.TempoMark(t.tick, t.bpm))
                }
            } catch (e: Exception) {
                try {
                    val rawMap = gson.fromJson(score.metadataJson, Map::class.java) as Map<*, *>
                    val tpqVal = (rawMap["ticks_per_quarter"] as? Number)?.toInt() ?: 0
                    if (tpqVal > 0) calculatedTpq = tpqVal
                    val tempoList = rawMap["tempo_events"] as? List<*>
                    tempoList?.forEach { item ->
                        val t = item as? Map<*, *>
                        if (t != null) {
                            val tick = (t["tick"] as? Number)?.toInt() ?: 0
                            val bpm = (t["bpm"] as? Number)?.toFloat() ?: 120f
                            tempoMarks.add(NativePlaybackEngine.TempoMark(tick, bpm))
                        }
                    }
                } catch (err: Exception) {}
            }
        }
        if (tempoMarks.isEmpty()) {
            tempoMarks.add(NativePlaybackEngine.TempoMark(0, 120f))
        }

        currentTpq = calculatedTpq
        currentBpm = tempoMarks.first().bpm
        
        // Calculate actual total seconds based on events and resolved tempo
        val maxTick = eventStream.maxOfOrNull { it.tickPosition + it.durationTicks } ?: 0
        if (maxTick > 0 && currentTpq > 0 && currentBpm > 0) {
            calculatedTotalSeconds = (maxTick * 60f / (currentBpm * currentTpq)).roundToInt()
        } else {
            calculatedTotalSeconds = score.totalSeconds // Fallback
        }
        
        playbackEngine.loadEvents(eventStream, tempoMarks, calculatedTpq)
        
        if (isPageFinished && currentScoreXml != null && !isRendered) {
            renderScore(currentScoreXml!!)
        }
        
        onScoreLoadedCallback(calculatedTotalSeconds)
    }
    
    private fun renderScore(xml: String) {
        if (isRendered) return
        isRendered = true
        val encoded = android.util.Base64.encodeToString(xml.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        webView.post {
            webView.evaluateJavascript("loadScoreBase64('$encoded', $currentTpq);", null)
        }
    }

    // ─── Control API ───────────────────────────────────────────────
    fun play() {
        webView.post { webView.evaluateJavascript("initSynth();", null) }
        playbackState = "PLAYING"
        when (playbackEngine.getState()) {
            NativePlaybackEngine.PlaybackState.IDLE,
            NativePlaybackEngine.PlaybackState.STOPPED -> playbackEngine.play()
            NativePlaybackEngine.PlaybackState.PLAYING -> {
                // Ignore, already playing. Or treat as toggle. UI toggles state.
            }
            NativePlaybackEngine.PlaybackState.PAUSED -> playbackEngine.resume()
        }
    }

    fun pause() {
        playbackState = "PAUSED"
        playbackEngine.pause()
    }

    fun stop() {
        playbackState = "STOPPED"
        playbackEngine.stop()
    }

    fun seek(progressFraction: Float) {
        playbackEngine.seek(progressFraction)
    }

    fun setTempo(bpm: Float) {
        currentBpm = bpm
        playbackEngine.setTempo(bpm)
        recalculateTotalSeconds()
    }

    fun setSpeedMultiplier(multiplier: Float) {
        playbackEngine.setSpeedMultiplier(multiplier)
        recalculateTotalSeconds()
    }

    fun getBaseBPM(): Float = currentBpm
    fun getSpeedMultiplier(): Float = playbackEngine.getSpeedMultiplier()

    fun zoomIn() {
        webView.post { webView.evaluateJavascript("if (window.zoomIn) window.zoomIn();", null) }
    }

    fun zoomOut() {
        webView.post { webView.evaluateJavascript("if (window.zoomOut) window.zoomOut();", null) }
    }

    fun zoomFit() {
        webView.post { webView.evaluateJavascript("if (window.zoomFit) window.zoomFit();", null) }
    }

    fun setZoom(zoom: Float) {
        webView.post {
            webView.evaluateJavascript("if (window.setZoom) window.setZoom($zoom); else if (window.osmd) { osmd.zoom = $zoom; osmd.render(); }", null)
        }
    }

    private fun recalculateTotalSeconds() {
        val maxTick = eventStream.maxOfOrNull { it.tickPosition + it.durationTicks } ?: 0
        val effectiveBpm = playbackEngine.getCurrentBPM()
        if (maxTick > 0 && currentTpq > 0 && effectiveBpm > 0) {
            calculatedTotalSeconds = (maxTick * 60f / (effectiveBpm * currentTpq)).roundToInt()
        }
        onScoreLoadedCallback(calculatedTotalSeconds)
    }

    // Accompaniment State Machine (Fix #Phase2 / Section 6.3)
    var isAccompanimentEnabled: Boolean = true
        set(value) {
            field = value
            updateAccompanimentState()
        }

    fun updateAccompanimentState() {
        val currentFocus = mutedVoicesList.firstOrNull() ?: "S"
        if (currentFocus in setOf("OTHER", "OTHERS")) {
            // State 3: Others is primary focus at 100% volume
            playbackEngine.muteVoice("OTHER", false)
            playbackEngine.muteVoice("OTHERS", false)
            playbackEngine.setVoiceVolume("OTHER", 1.0f)
            playbackEngine.setVoiceVolume("OTHERS", 1.0f)
        } else {
            // States 1, 2, 4: Vocal focus
            if (isAccompanimentEnabled) {
                // State 1 & 4 (ON): Soft 35% backing track
                playbackEngine.muteVoice("OTHER", false)
                playbackEngine.muteVoice("OTHERS", false)
                playbackEngine.setVoiceVolume("OTHER", 0.35f)
                playbackEngine.setVoiceVolume("OTHERS", 0.35f)
            } else {
                // State 2 & 4 (OFF): Muted backing track & mid-stream note cancellation
                playbackEngine.muteVoice("OTHER", true)
                playbackEngine.muteVoice("OTHERS", true)
                playbackEngine.cancelAccompanimentNotesNow()
            }
        }
    }

    /**
     * Focus audio on the selected part (solos this part, muting non-focused vocal parts).
     */
    fun focusPart(part: String) {
        val targetLabel = when {
            part.uppercase().startsWith("SOLO") -> "SOLO"
            part.uppercase().startsWith("OTHER") -> "OTHER"
            else -> part.trim().uppercase()
        }
        mutedVoicesList = listOf(targetLabel)
        playbackEngine.setFocusedVoicePart(targetLabel)
        playbackEngine.silenceAllActiveNotes()

        val baseVoice = when {
            targetLabel.startsWith("SOLO") -> "SOLO"
            targetLabel.startsWith("OTHER") -> "OTHER"
            targetLabel.startsWith("S") -> "S"
            targetLabel.startsWith("A") -> "A"
            targetLabel.startsWith("T") -> "T"
            targetLabel.startsWith("B") -> "B"
            else -> "S"
        }

        // Mute all vocal parts EXCEPT the focused part
        listOf("S", "A", "T", "B", "SOLO").forEach { v ->
            val isMuted = (v != baseVoice)
            playbackEngine.muteVoice(v, isMuted)
            if (!isMuted) {
                playbackEngine.setVoiceVolume(v, 1.0f)
            }
        }

        // Apply accompaniment state according to Section 6.3 State Machine
        updateAccompanimentState()
    }

    fun mutePart(part: String) = focusPart(part)

    fun unmuteAllParts() {
        mutedVoicesList = emptyList()
        playbackEngine.setFocusedVoicePart(null)
        playbackEngine.silenceAllActiveNotes()
        listOf("S", "A", "T", "B", "SOLO").forEach { v ->
            playbackEngine.muteVoice(v, false)
            playbackEngine.setVoiceVolume(v, 1.0f)
        }
        updateAccompanimentState()
    }

    fun setVisualFocus(part: String) {
        val targetLabel = when {
            part.uppercase().startsWith("SOLO") -> "SOLO"
            part.uppercase().startsWith("OTHER") -> "OTHER"
            else -> part.trim().uppercase()
        }
        visualFocusPart = targetLabel
        if (isRendered) {
            webView.post { webView.evaluateJavascript("setVisualFocus('$targetLabel');", null) }
        }
        refreshHighlights()
    }

    fun clearVisualFocus() {
        visualFocusPart = null
        if (isRendered) {
            webView.post { webView.evaluateJavascript("clearVisualFocus();", null) }
        }
        refreshHighlights()
    }

    fun release() {
        playbackEngine.release()
    }

    // ─── PlaybackListener Implementation ───────────────────────────────
    override fun onPlaybackStarted() {
        playbackState = "PLAYING"
        Handler(Looper.getMainLooper()).post {
            onPlaybackStartedCallback()
        }
    }
    override fun onPlaybackPaused() {
        playbackState = "PAUSED"
        Handler(Looper.getMainLooper()).post {
            onPlaybackPausedCallback()
        }
    }
    
    override fun onPlaybackStopped() {
        playbackState = "STOPPED"
        syncManager.onPlaybackStopped()
        Handler(Looper.getMainLooper()).post {
            onProgressCallback(1f)
            onCompleteCallback()
        }
        webView.post {
            webView.evaluateJavascript("clearHighlights();", null)
        }
    }

    override fun onPlaybackProgress(currentTick: Int, totalTicks: Int, activeEventIds: List<String>) {
        val progress = if (totalTicks > 0) currentTick.toFloat() / totalTicks else 0f
        Handler(Looper.getMainLooper()).post {
            onProgressCallback(progress)
        }
        syncManager.onPlaybackProgress(activeEventIds)
    }

    override fun onNoteOn(event: MusicalEvent, velocity: Int) {
        lastMidiEvent = "ON: pitch=${event.pitchMidi} (${event.pitchName}) voice=${event.satbVoice} vel=$velocity"
        onNoteOnCallback(event)

        // Tone.js Display-Only Mode (Fix #Phase2.6):
        // If hardware MIDI is active, mute WebView Web Audio synthesis to avoid duplicated audio and echo
        if (playbackEngine.isHardwareMidiActive()) {
            return
        }

        // Tone.js Fallback Mode: forward scaled effective velocity (e.g. 28 for 35% backing track, 80 for 100% vocal)
        val bpm = playbackEngine.getCurrentBPM().coerceAtLeast(1f)
        val tpq = currentTpq.coerceAtLeast(1)
        val durationMs = (event.durationTicks.toDouble() * 60000.0 / (bpm.toDouble() * tpq.toDouble())).toLong().coerceAtLeast(100)
        webView.post {
            webView.evaluateJavascript("playNote(${event.pitchMidi}, $durationMs, $velocity);", null)
        }
    }

    override fun onNoteOn(event: MusicalEvent) {
        onNoteOn(event, 80)
    }

    override fun onNoteOff(event: MusicalEvent) {
        // Handled by Tone.js triggerAttackRelease duration
    }

    override fun onPlaybackError(error: String) {}

    private var lastHighlights: List<SyncManager.NoteHighlightData> = emptyList()
    private var lastRenderedHighlightsJson: String? = null
    private var overlayVersion = 0

    override fun onHighlightNotes(highlights: List<SyncManager.NoteHighlightData>) {
        lastHighlights = highlights
        renderHighlights()
    }

    fun refreshHighlights() {
        if (lastHighlights.isNotEmpty()) {
            renderHighlights()
        }
    }

    private fun renderHighlights() {
        val targetFilterPart = if (isMicEnabled) {
            when {
                selectedVoicePart?.uppercase()?.startsWith("SOLO") == true -> "SOLO"
                selectedVoicePart?.uppercase()?.startsWith("OTHER") == true -> "OTHERS"
                else -> selectedVoicePart?.trim()?.uppercase() ?: visualFocusPart
            }
        } else {
            visualFocusPart
        }

        // Inject colors based on SATB / Divisi / Solo / Others part mapping
        val coloredNotes = lastHighlights.mapNotNull { highlight ->
            val event = eventStream.find { it.eventId == highlight.eventId }
            if (event == null) return@mapNotNull null

            // Visual Focus / Active singing voice filtering
            if (!isEventMatchingFocusOrPart(targetFilterPart, event)) {
                return@mapNotNull null // Skip drawing this highlight
            }

            var hexColor = getHighlightColorForEvent(event, targetFilterPart)

            // Apply Pitch Tracking feedback colors ONLY when mic / pitch test mode is active
            if (isMicEnabled) {
                val attempt = pitchAttempts.findLast { it.eventId == event.eventId }
                if (attempt != null) {
                    hexColor = if (attempt.isMatch) "#00E676" else "#E53935" // Bright Green for Match, Red for Miss
                }
            }

            mapOf(
                "eventId" to highlight.eventId,
                "x" to highlight.x,
                "y" to highlight.y,
                "width" to highlight.width,
                "height" to highlight.height,
                "color" to hexColor
            )
        }
        
        val notesJson = gson.toJson(coloredNotes)
        
        // Prevent redundant JS bridge calls if colors haven't actually changed
        if (notesJson != lastRenderedHighlightsJson) {
            lastRenderedHighlightsJson = notesJson
            
            overlayVersion++
            val payload = mapOf(
                "version" to overlayVersion,
                "notes" to coloredNotes
            )
            val json = gson.toJson(payload)
            
            webView.post {
                webView.evaluateJavascript("window.updatePitchOverlay($json);", null)
            }
        }
    }

    override fun onClearHighlights() {
        webView.post {
            webView.evaluateJavascript("clearHighlights();", null)
        }
    }

    override fun onSyncConfidenceChanged(confidence: Float) {
        syncConfidence = confidence
        webView.post {
            webView.evaluateJavascript("updateSyncConfidence($confidence);", null)
        }
    }

    override fun onHighlightModeChanged(mode: SyncManager.HighlightMode) {}

    // ─── JavaScript Bridge ─────────────────────────────────────────────
    inner class VoxSightJsBridge {
        private var lastProcessedLayoutHash: String? = null

        @JavascriptInterface
        @Suppress("UNCHECKED_CAST")
        fun onRenderComplete(layoutHash: String, coordinatesJson: String) {
            try {
                if (layoutHash == lastProcessedLayoutHash && mappedNotesCount > 0) {
                    return
                }
                lastProcessedLayoutHash = layoutHash

                val coordType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawCoords: List<Map<String, Any>> = gson.fromJson(coordinatesJson, coordType)

                osmdNotesCount = rawCoords.size

                val osmdElements = rawCoords.map { raw ->
                    SyncManager.OsmdNoteElement(
                        id = (raw["id"] as? Number)?.toInt() ?: 0,
                        tick = (raw["tick"] as? Number)?.toInt() ?: 0,
                        midiNote = (raw["midiNote"] as? Number)?.toInt() ?: 0,
                        measureNumber = (raw["measureNumber"] as? Number)?.toInt() ?: 1,
                        measureIndex = (raw["measureIndex"] as? Number)?.toInt() ?: (((raw["measureNumber"] as? Number)?.toInt() ?: 1) - 1),
                        voice = (raw["voice"] as? Number)?.toInt() ?: 1,
                        staffIdx = (raw["staffIdx"] as? Number)?.toInt() ?: 0,
                        part = (raw["part"] as? String) ?: "S",
                        color = (raw["color"] as? String) ?: "#E11D48",
                        x = (raw["x"] as? Number)?.toFloat() ?: 0f,
                        y = (raw["y"] as? Number)?.toFloat() ?: 0f,
                        width = (raw["width"] as? Number)?.toFloat() ?: 0f,
                        height = (raw["height"] as? Number)?.toFloat() ?: 0f
                    )
                }

                syncManager.buildMapping(eventStream, osmdElements, layoutHash)
                
                syncConfidence = syncManager.getSyncConfidence()
                mappedNotesCount = syncManager.coordinateMapSize
                
                // Synchronize source coloring directly from backend mapping
                val initialColors = eventStream.mapNotNull { event ->
                    val match = syncManager.getCoordinateForEvent(event.eventId)
                    if (match != null) {
                        val hexColor = getHighlightColorForEvent(event, null)
                        val part = event.participatingParts.firstOrNull() ?: event.satbVoice
                        mapOf(
                            "x" to match.x,
                            "y" to match.y,
                            "id" to match.id,
                            "color" to hexColor,
                            "part" to part
                        )
                    } else null
                }
                
                val initColorsJson = gson.toJson(initialColors)
                webView.post {
                    webView.evaluateJavascript("applySyncColors('$initColorsJson');", null)
                }
            } catch (e: Exception) {
                android.util.Log.e("SyncDiag", "onRenderComplete EXCEPTION: ${e.message}", e)
            }
        }

        @JavascriptInterface
        fun onLayoutChanged(layoutHash: String, coordinatesJson: String) {
            onRenderComplete(layoutHash, coordinatesJson)
        }

        @JavascriptInterface
        fun onRenderError(error: String) {}
    }
}
