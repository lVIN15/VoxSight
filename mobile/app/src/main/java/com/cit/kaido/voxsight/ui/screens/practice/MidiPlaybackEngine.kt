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

    // Refresh highlights dynamically when pitchAttempts change
    LaunchedEffect(pitchAttempts) {
        controller.pitchAttempts = pitchAttempts
        controller.refreshHighlights()
    }

    // Lifecycle cleanup
    DisposableEffect(Unit) {
        onDispose {
            controller.release()
        }
    }

    AndroidView(
        factory = {
            controller.webView
        },
        update = { view ->
            if (score != null) {
                controller.loadScore(score)
            }
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
    
    // ─── Diagnostics State (Compose Reactive) ─────────────────────────
    var eventsCount by mutableStateOf(0)
    var osmdNotesCount by mutableStateOf(0)
    var mappedNotesCount by mutableStateOf(0)
    var syncConfidence by mutableStateOf(1.0f)
    var playbackState by mutableStateOf("IDLE")
    var lastMidiEvent by mutableStateOf<String?>(null)
    var mutedVoicesList by mutableStateOf(listOf<String>())
    var pitchAttempts by mutableStateOf(listOf<com.cit.kaido.voxsight.pitch.PitchAttempt>())
    
    private var eventStream: EventStream = EventStream.empty()
    private var isRendered = false
    private var currentScoreXml: String? = null
    
    open val totalSeconds: Int get() = calculatedTotalSeconds
    
    private var visualFocusPart: String? = null
    private var currentTpq = 960
    private var currentBpm = 120f
    private var calculatedTotalSeconds = 0

    init {
        playbackEngine.setListener(this)
        playbackEngine.suspendUntilPitchMatched = { events ->
            onWaitPitchCallback(events)
        }
        playbackEngine.initialize()
        
        syncManager.setListener(this)
        
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
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
        currentScoreXml = score.rawXml
        
        // Parse events
        if (score.eventsJson != null) {
            val eventType = object : TypeToken<List<MusicalEvent>>() {}.type
            val events: List<MusicalEvent> = gson.fromJson(score.eventsJson, eventType)
            eventStream = EventStream.freeze(events)
            eventsCount = events.size
            
            // We'll calculate actual total seconds after extracting tempo metadata
            
            // Parse metadata
            val tempoMarks = mutableListOf<NativePlaybackEngine.TempoMark>()
            var calculatedTpq = 960
            if (score.metadataJson != null) {
                try {
                    val metadata = gson.fromJson(score.metadataJson, ScoreMetadata::class.java)
                    calculatedTpq = metadata.ticksPerQuarter
                    metadata.tempoEvents.forEach { t ->
                        tempoMarks.add(NativePlaybackEngine.TempoMark(t.tick, t.bpm))
                    }
                } catch (e: Exception) {
                    try {
                        val rawMap = gson.fromJson(score.metadataJson, Map::class.java) as Map<*, *>
                        calculatedTpq = (rawMap["ticks_per_quarter"] as? Number)?.toInt() ?: 960
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
        }
        
        if (webView.url != null && webView.progress == 100) {
            renderScore(currentScoreXml!!)
        }
        
        onScoreLoadedCallback(calculatedTotalSeconds)
    }
    
    private fun renderScore(xml: String) {
        val escaped = xml
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("</", "<\\/")

        webView.post {
            webView.evaluateJavascript("loadScore('$escaped', $currentTpq);", null)
        }
        isRendered = true
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
        // Seek is not natively supported yet in NativePlaybackEngine.
    }

    /**
     * Mute the selected SATB part.
     * This mutes ONLY that single voice so the user can sing along without hearing it.
     */
    fun mutePart(part: String) {
        val targetLabel = part.firstOrNull()?.toString()?.uppercase() ?: "S"
        mutedVoicesList = listOf(targetLabel)
        
        // Mute all parts EXCEPT the selected target part (so the user can hear their part clearly)
        listOf("S", "A", "T", "B").forEach { v ->
            playbackEngine.muteVoice(v, v != targetLabel)
        }
    }

    fun unmuteAllParts() {
        mutedVoicesList = emptyList()
        listOf("S", "A", "T", "B").forEach { v ->
            playbackEngine.muteVoice(v, false)
        }
    }

    fun setVisualFocus(part: String) {
        val targetLabel = part.firstOrNull()?.toString()?.uppercase() ?: "S"
        visualFocusPart = targetLabel
        if (isRendered) {
            webView.post { webView.evaluateJavascript("setVisualFocus('$targetLabel');", null) }
        }
    }

    fun clearVisualFocus() {
        visualFocusPart = null
        if (isRendered) {
            webView.post { webView.evaluateJavascript("clearVisualFocus();", null) }
        }
    }

    fun release() {
        playbackEngine.release()
    }

    // ─── PlaybackListener Implementation ───────────────────────────────
    override fun onPlaybackStarted() {
        playbackState = "PLAYING"
    }
    override fun onPlaybackPaused() {
        playbackState = "PAUSED"
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

    override fun onNoteOn(event: MusicalEvent) {
        lastMidiEvent = "ON: pitch=${event.pitchMidi} (${event.pitchName}) voice=${event.satbVoice}"
        onNoteOnCallback(event)
        // Compute proper duration using current tempo and ticksPerQuarter
        val bpm = playbackEngine.getCurrentBPM().coerceAtLeast(1f)
        val tpq = currentTpq.coerceAtLeast(1)
        val durationMs = (event.durationTicks.toDouble() * 60000.0 / (bpm.toDouble() * tpq.toDouble())).toLong().coerceAtLeast(100)
        webView.post {
            webView.evaluateJavascript("playNote(${event.pitchMidi}, $durationMs, 80);", null)
        }
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
        // Inject colors based on SATB part mapping
        val coloredNotes = lastHighlights.mapNotNull { highlight ->
            val event = eventStream.find { it.eventId == highlight.eventId }
            val part = event?.satbVoice?.firstOrNull()?.toString()?.uppercase() ?: "S"
            
            // Visual Focus Filtering: skip non-focused parts if focus is enabled
            if (visualFocusPart != null && part != visualFocusPart) {
                return@mapNotNull null // Skip drawing this highlight
            }
            
            var hexColor = when (part) {
                "S" -> "#E91E63" // Pink
                "A" -> "#9C27B0" // Purple
                "T" -> "#2196F3" // Blue
                "B" -> "#4CAF50" // Green
                else -> "#6366f1" // Indigo fallback
            }

            // Apply Pitch Tracking feedback colors
            val attempt = pitchAttempts.findLast { it.eventId == event?.eventId }
            if (attempt != null) {
                hexColor = if (attempt.isMatch) "#00E676" else "#E53935" // Bright Green for Match, Red for Miss
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
        @JavascriptInterface
        @Suppress("UNCHECKED_CAST")
        fun onRenderComplete(layoutHash: String, coordinatesJson: String) {
            try {
                val coordType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawCoords: List<Map<String, Any>> = gson.fromJson(coordinatesJson, coordType)

                osmdNotesCount = rawCoords.size

                val osmdElements = rawCoords.map { raw ->
                    SyncManager.OsmdNoteElement(
                        id = (raw["id"] as? Number)?.toInt() ?: 0,
                        tick = (raw["tick"] as? Number)?.toInt() ?: 0,
                        midiNote = (raw["midiNote"] as? Number)?.toInt() ?: 0,
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
                        val part = event.satbVoice.firstOrNull()?.toString()?.uppercase() ?: "S"
                        val hexColor = when (part) {
                            "S" -> "#E91E63" // Pink
                            "A" -> "#9C27B0" // Purple
                            "T" -> "#2196F3" // Blue
                            "B" -> "#4CAF50" // Green
                            else -> "#E91E63"
                        }
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
            } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun onLayoutChanged(layoutHash: String, coordinatesJson: String) {
            onRenderComplete(layoutHash, coordinatesJson)
        }

        @JavascriptInterface
        fun onRenderError(error: String) {}
    }
}
