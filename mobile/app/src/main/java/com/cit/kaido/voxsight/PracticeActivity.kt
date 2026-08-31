package com.cit.kaido.voxsight

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cit.kaido.voxsight.model.EventStream
import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.model.SATBClassification
import com.cit.kaido.voxsight.model.SATBDisplayPolicy
import com.cit.kaido.voxsight.network.ApiClient
import com.cit.kaido.voxsight.network.OmrResponse
import com.cit.kaido.voxsight.network.PlaybackTrack
import com.cit.kaido.voxsight.network.ScoreMetadata
import com.cit.kaido.voxsight.playback.NativePlaybackEngine
import com.cit.kaido.voxsight.playback.SyncManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * VoxSight Practice Activity (Milestone 5 — Integration)
 * ========================================================
 * Wires together:
 *   - OSMD renderer (WebView — read-only, no MusicXML modification)
 *   - NativePlaybackEngine (Android MIDI — single queue dispatcher)
 *   - SyncManager (event_id + layout_hash correlation)
 *
 * Architecture Contract (v3.7):
 *   - Events received as ORDER-FROZEN (Fix #40)
 *   - Playback is fully native — WebView has ZERO audio responsibility
 *   - Sync is best-effort — audio ALWAYS plays, visuals degrade gracefully
 *   - MusicXML is rendered UNTOUCHED by OSMD
 */
class PracticeActivity : AppCompatActivity(),
    NativePlaybackEngine.PlaybackListener,
    SyncManager.SyncListener {

    companion object {
        private const val TAG = "PracticeActivity"

        // Intent extras
        const val EXTRA_MUSICXML = "MUSICXML_CONTENT"
        const val EXTRA_EVENTS_JSON = "EVENTS_JSON"
        const val EXTRA_METADATA_JSON = "METADATA_JSON"
        const val EXTRA_BACKEND_URL = "BACKEND_URL"
        const val EXTRA_FILE_URI = "FILE_URI"
        const val EXTRA_FILE_NAME = "FILE_NAME"
    }

    // ─── Components ────────────────────────────────────────────────────
    private lateinit var webView: WebView
    private lateinit var playbackEngine: NativePlaybackEngine
    private lateinit var syncManager: SyncManager
    private val gson = Gson()

    // ─── State ─────────────────────────────────────────────────────────
    private var musicXml: String? = null
    private var eventStream: EventStream = EventStream.empty()
    private var isRendered = false
    private var currentTpq = 960
    private var currentBpm = 120f

    // ─── UI Controls ───────────────────────────────────────────────────
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    // Premium UI Elements
    private lateinit var toolbarTitle: TextView
    private lateinit var badgeSyncStatus: TextView
    private lateinit var layoutTrackList: LinearLayout
    private lateinit var btnBack: ImageButton

    // Playback Track state
    private var playbackTracks: List<PlaybackTrack> = emptyList()
    private val trackRowViews = HashMap<String, View>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice)

        // Initialize playback engine
        playbackEngine = NativePlaybackEngine(this)
        playbackEngine.setListener(this)
        playbackEngine.initialize()

        // Initialize sync manager
        syncManager = SyncManager()
        syncManager.setListener(this)

        // Set up WebView
        webView = findViewById(R.id.practiceWebView)
        setupWebView()

        // Set up control bar
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnStop = findViewById(R.id.btnStop)
        progressBar = findViewById(R.id.playbackProgress)
        statusText = findViewById(R.id.statusText)

        btnPlayPause.setOnClickListener { onPlayClicked() }
        btnStop.setOnClickListener { onStopClicked() }

        // Set up premium UI elements
        toolbarTitle = findViewById(R.id.toolbarTitle)
        badgeSyncStatus = findViewById(R.id.badgeSyncStatus)
        layoutTrackList = findViewById(R.id.layoutTrackList)
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        // Check if data was passed via intent
        val passedMusicXml = intent.getStringExtra(EXTRA_MUSICXML)
        val passedEventsJson = intent.getStringExtra(EXTRA_EVENTS_JSON)
        val fileUri = intent.getStringExtra(EXTRA_FILE_URI)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        val backendUrl = intent.getStringExtra(EXTRA_BACKEND_URL) ?: "http://10.202.26.27:8080"

        if (passedMusicXml != null && passedEventsJson != null) {
            // Data already available (passed from OmrScreen after /api/analyze)
            loadFromAnalysisData(passedMusicXml, passedEventsJson,
                intent.getStringExtra(EXTRA_METADATA_JSON))
        } else if (fileUri != null && fileName != null) {
            // Need to upload and analyze
            uploadAndAnalyze(backendUrl, fileUri, fileName)
        } else {
            Toast.makeText(this, "No score data provided", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            // No media playback — audio is native
            mediaPlaybackRequiresUserGesture = true
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Render the score once the HTML is loaded
                if (musicXml != null && !isRendered) {
                    renderScore()
                }
            }
        }

        // Add JavaScript bridge for coordinate extraction
        webView.addJavascriptInterface(VoxSightJsBridge(), "VoxSightBridge")

        // Load the clean OSMD renderer
        webView.loadUrl("file:///android_asset/renderer.html")
    }

    // ─── Data Loading ──────────────────────────────────────────────────
    private fun loadFromAnalysisData(musicXml: String, eventsJson: String, metadataJson: String?) {
        this.musicXml = musicXml

        // Set action bar title
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Practice Score"
        runOnUiThread {
            toolbarTitle.text = fileName.substringBeforeLast(".")
        }

        // Parse events from JSON
        val eventType = object : TypeToken<List<MusicalEvent>>() {}.type
        val events: List<MusicalEvent> = gson.fromJson(eventsJson, eventType)
        eventStream = EventStream.freeze(events)

        // Parse metadata for tempo events and structural tracks
        val tempoMarks = mutableListOf<NativePlaybackEngine.TempoMark>()
        var tpq = 960
        if (metadataJson != null) {
            try {
                val metadata = gson.fromJson(metadataJson, ScoreMetadata::class.java)
                tpq = metadata.ticksPerQuarter
                
                metadata.tempoEvents.forEach { t ->
                    tempoMarks.add(NativePlaybackEngine.TempoMark(t.tick, t.bpm))
                }
                
                playbackTracks = metadata.playbackTracks
                Log.i(TAG, "Parsed ${playbackTracks.size} playback tracks from metadata")
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing metadata via ScoreMetadata, falling back to Map: ${e.message}")
                try {
                    val rawMap = gson.fromJson(metadataJson, Map::class.java) as Map<*, *>
                    tpq = (rawMap["ticks_per_quarter"] as? Number)?.toInt() ?: 960
                    
                    @Suppress("UNCHECKED_CAST")
                    val tempoList = rawMap["tempo_events"] as? List<Map<String, Any>>
                    tempoList?.forEach { t ->
                        val tick = (t["tick"] as? Number)?.toInt() ?: 0
                        val bpm = (t["bpm"] as? Number)?.toFloat() ?: 120f
                        tempoMarks.add(NativePlaybackEngine.TempoMark(tick, bpm))
                    }
                } catch (err: Exception) {
                    Log.e(TAG, "Fallback parsing failed: ${err.message}")
                }
            }
        }
        if (tempoMarks.isEmpty()) {
            tempoMarks.add(NativePlaybackEngine.TempoMark(0, 120f))
            currentBpm = 120f
        } else {
            currentBpm = tempoMarks.first().bpm
        }
        currentTpq = tpq

        // Load events into playback engine
        playbackEngine.loadEvents(eventStream, tempoMarks, tpq)

        Log.i(TAG, "Loaded ${events.size} events, tpq=$tpq")

        // Populate the dynamic track list controls on UI thread
        populateTrackList()

        // Render score if WebView is ready
        if (webView.url != null) {
            renderScore()
        }
    }

    private fun uploadAndAnalyze(backendUrl: String, fileUri: String, fileName: String) {
        lifecycleScope.launch {
            try {
                // Read file bytes
                val uri = android.net.Uri.parse(fileUri)
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot read file")
                val fileBytes = inputStream.use { it.readBytes() }

                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("musicFile", fileName, requestBody)

                // Call /api/analyze
                val response = withContext(Dispatchers.IO) {
                    ApiClient.omrService.analyzeScore(part)
                }

                if (response.success && response.musicXml != null) {
                    val eventsJson = gson.toJson(response.events ?: emptyList<Any>())
                    val metadataJson = gson.toJson(response.scoreMetadata)
                    loadFromAnalysisData(response.musicXml, eventsJson, metadataJson)
                } else {
                    Toast.makeText(this@PracticeActivity,
                        "Analysis failed: ${response.error ?: "Unknown error"}",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload/analyze error: ${e.message}", e)
                Toast.makeText(this@PracticeActivity,
                    "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ─── Rendering ─────────────────────────────────────────────────────
    private fun renderScore() {
        val xml = musicXml ?: return
        val encoded = android.util.Base64.encodeToString(xml.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        webView.evaluateJavascript("loadScoreBase64('$encoded');", null)
        isRendered = true
        Log.i(TAG, "Score sent to OSMD renderer via Base64")
    }

    // ─── Playback Controls (called from UI) ────────────────────────────
    fun onPlayClicked() {
        when (playbackEngine.getState()) {
            NativePlaybackEngine.PlaybackState.IDLE,
            NativePlaybackEngine.PlaybackState.STOPPED -> playbackEngine.play()
            NativePlaybackEngine.PlaybackState.PLAYING -> playbackEngine.pause()
            NativePlaybackEngine.PlaybackState.PAUSED -> playbackEngine.resume()
        }
    }

    fun onStopClicked() {
        playbackEngine.stop()
    }

    // ─── PlaybackListener Implementation ───────────────────────────────
    override fun onPlaybackStarted() {
        Log.i(TAG, "Playback started")
        runOnUiThread {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            statusText.text = "Playing"
        }
    }

    override fun onPlaybackPaused() {
        Log.i(TAG, "Playback paused")
        runOnUiThread {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            statusText.text = "Paused"
        }
    }

    override fun onPlaybackStopped() {
        Log.i(TAG, "Playback stopped")
        syncManager.onPlaybackStopped()
        runOnUiThread {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            progressBar.progress = 0
            statusText.text = "Stopped"
        }
        webView.post {
            webView.evaluateJavascript("clearHighlights();", null)
        }
    }

    override fun onPlaybackProgress(currentTick: Int, totalTicks: Int, activeEventIds: List<String>) {
        // Update progress bar
        val progress = if (totalTicks > 0) (currentTick * 100 / totalTicks) else 0
        runOnUiThread {
            progressBar.progress = progress.coerceIn(0, 100)
        }
        // Sync: translate playback event to visual highlight
        syncManager.onPlaybackProgress(activeEventIds)
    }

    override fun onNoteOn(event: com.cit.kaido.voxsight.model.MusicalEvent) {
        val bpm = currentBpm.coerceAtLeast(1f)
        val tpq = currentTpq.coerceAtLeast(1)
        val durationMs = (event.durationTicks.toLong() * 60000L / (bpm.toLong() * tpq)).coerceAtLeast(100)
        webView.post {
            webView.evaluateJavascript("playNote(${event.pitchMidi}, $durationMs, 80);", null)
        }
    }

    override fun onNoteOff(event: com.cit.kaido.voxsight.model.MusicalEvent) {}

    override fun onPlaybackError(error: String) {
        Log.e(TAG, "Playback error: $error")
        runOnUiThread {
            Toast.makeText(this, "Playback error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── SyncListener Implementation ───────────────────────────────────
    override fun onHighlightNotes(highlights: List<SyncManager.NoteHighlightData>) {
        val coloredNotes = highlights.map { highlight ->
            mapOf(
                "eventId" to highlight.eventId,
                "x" to highlight.x,
                "y" to highlight.y,
                "width" to highlight.width,
                "height" to highlight.height,
                "color" to "#6366f1"
            )
        }
        val json = gson.toJson(coloredNotes)
        webView.post {
            webView.evaluateJavascript(
                "highlightNotes('$json');", null
            )
        }
    }

    override fun onClearHighlights() {
        webView.post {
            webView.evaluateJavascript("clearHighlights();", null)
        }
    }

    override fun onSyncConfidenceChanged(confidence: Float) {
        webView.post {
            webView.evaluateJavascript(
                "updateSyncConfidence($confidence);", null
            )
        }
    }

    override fun onHighlightModeChanged(mode: SyncManager.HighlightMode) {
        Log.i(TAG, "Sync highlight mode changed to: $mode")
        runOnUiThread {
            when (mode) {
                SyncManager.HighlightMode.NOTE_LEVEL -> {
                    badgeSyncStatus.text = "NOTE-LEVEL SYNC"
                    badgeSyncStatus.setBackgroundResource(R.drawable.bg_badge_sync_note)
                }
                SyncManager.HighlightMode.MEASURE_LEVEL -> {
                    badgeSyncStatus.text = "APPROXIMATE SYNC"
                    badgeSyncStatus.setBackgroundResource(R.drawable.bg_badge_sync_measure)
                }
                SyncManager.HighlightMode.DISABLED -> {
                    badgeSyncStatus.text = "SYNC DISABLED"
                    badgeSyncStatus.setBackgroundResource(R.drawable.bg_badge_sync_disabled)
                }
            }
        }
        webView.post {
            webView.evaluateJavascript(
                "console.log('Highlight mode changed to $mode');", null
            )
        }
    }

    // ─── Track List Population & Mute/Solo (Milestone 5) ──────────────────
    private fun populateTrackList() {
        runOnUiThread {
            layoutTrackList.removeAllViews()
            trackRowViews.clear()

            if (playbackTracks.isEmpty()) {
                Log.w(TAG, "No playback tracks found in metadata. Creating mock tracks from eventStream.")
                val trackIds = eventStream.playbackTrackIds().sorted()
                playbackTracks = trackIds.map { id ->
                    val isStaff2 = id.contains("s2")
                    val isVoice2 = id.contains("v2")
                    val satbLabel = when {
                        !isStaff2 && !isVoice2 -> "Soprano"
                        !isStaff2 && isVoice2 -> "Alto"
                        isStaff2 && !isVoice2 -> "Tenor"
                        else -> "Bass"
                    }
                    PlaybackTrack(
                        trackId = id,
                        structuralLabel = if (isVoice2) "Part 1, Staff ${if (isStaff2) 2 else 1}, Voice 2" else "Part 1, Staff ${if (isStaff2) 2 else 1}",
                        satbLabel = satbLabel,
                        satbConfidenceApplied = true
                    )
                }
            }

            val inflater = LayoutInflater.from(this)

            for (track in playbackTracks) {
                val rowView = inflater.inflate(R.layout.item_playback_track, layoutTrackList, false)
                
                val txtStructural = rowView.findViewById<TextView>(R.id.txtStructuralLabel)
                val layoutSatbBadge = rowView.findViewById<View>(R.id.layoutSatbBadge)
                val txtSatbLabel = rowView.findViewById<TextView>(R.id.txtSatbLabel)
                val txtSatbConfidence = rowView.findViewById<TextView>(R.id.txtSatbConfidence)
                val btnMute = rowView.findViewById<ImageButton>(R.id.btnMute)
                val btnSolo = rowView.findViewById<ImageButton>(R.id.btnSolo)

                txtStructural.text = track.structuralLabel

                // ─── Dual-Layer Gating via SATBDisplayPolicy (Fix #39) ───
                val trackEvents = eventStream.filter { it.playbackTrack == track.trackId }
                val avgConfidence = if (trackEvents.isNotEmpty()) {
                    trackEvents.map { it.satbConfidence }.average().toFloat()
                } else 0.8f
                
                val meanConf = avgConfidence
                val variance = if (trackEvents.size > 1) {
                    trackEvents.map { (it.satbConfidence - meanConf) * (it.satbConfidence - meanConf) }.average().toFloat()
                } else 0f
                val stdDev = kotlin.math.sqrt(variance)

                val classification = SATBClassification(
                    satbVoice = track.satbLabel,
                    confidence = avgConfidence,
                    uncertaintyBand = stdDev
                )

                if (track.satbConfidenceApplied && SATBDisplayPolicy.shouldShowSATBLabel(classification)) {
                    layoutSatbBadge.visibility = View.VISIBLE
                    txtSatbLabel.text = track.satbLabel
                    
                    val labelStyle = SATBDisplayPolicy.labelStyle(classification)
                    when (labelStyle) {
                        SATBDisplayPolicy.LabelStyle.CONFIRMED -> {
                            txtSatbConfidence.text = "CONFIRMED (${"%.0f%%".format(avgConfidence * 100)})"
                            txtSatbConfidence.setTextColor(Color.parseColor("#10b981"))
                            txtSatbConfidence.setBackgroundResource(R.drawable.bg_badge_confirmed)
                        }
                        SATBDisplayPolicy.LabelStyle.TENTATIVE -> {
                            txtSatbConfidence.text = "TENTATIVE (${"%.0f%%".format(avgConfidence * 100)})"
                            txtSatbConfidence.setTextColor(Color.parseColor("#f59e0b"))
                            txtSatbConfidence.setBackgroundResource(R.drawable.bg_badge_tentative)
                        }
                        SATBDisplayPolicy.LabelStyle.HIDDEN -> {
                            layoutSatbBadge.visibility = View.GONE
                        }
                    }
                } else {
                    layoutSatbBadge.visibility = View.GONE
                }

                // ─── Click Handlers ───
                btnMute.setOnClickListener {
                    val isMuted = !playbackEngine.isTrackMuted(track.trackId)
                    playbackEngine.muteTrack(track.trackId, isMuted)
                    refreshTrackListUI()
                }

                btnSolo.setOnClickListener {
                    val isSoloed = btnSolo.tag as? Boolean ?: false
                    val newSoloedState = !isSoloed
                    btnSolo.tag = newSoloedState
                    
                    playbackEngine.soloTrack(track.trackId, newSoloedState)
                    refreshTrackListUI()
                }

                layoutTrackList.addView(rowView)
                trackRowViews[track.trackId] = rowView
            }
            refreshTrackListUI()
        }
    }

    private fun refreshTrackListUI() {
        runOnUiThread {
            for (track in playbackTracks) {
                val rowView = trackRowViews[track.trackId] ?: continue
                val btnMute = rowView.findViewById<ImageButton>(R.id.btnMute)
                val btnSolo = rowView.findViewById<ImageButton>(R.id.btnSolo)

                val isMuted = playbackEngine.isTrackMuted(track.trackId)
                val isSoloed = btnSolo.tag as? Boolean ?: false

                if (isMuted) {
                    btnMute.setColorFilter(Color.parseColor("#ef4444"))
                    btnMute.setBackgroundColor(Color.parseColor("#450a0a"))
                } else {
                    btnMute.setColorFilter(Color.parseColor("#94a3b8"))
                    btnMute.setBackgroundResource(R.drawable.bg_control_button)
                }

                if (isSoloed) {
                    btnSolo.setColorFilter(Color.parseColor("#0ea5e9"))
                    btnSolo.setBackgroundColor(Color.parseColor("#0c4a6e"))
                } else {
                    btnSolo.setColorFilter(Color.parseColor("#94a3b8"))
                    btnSolo.setBackgroundResource(R.drawable.bg_control_button)
                }
            }
        }
    }

    // ─── JavaScript Bridge ─────────────────────────────────────────────
    /**
     * Bridge for OSMD renderer to send data back to Android.
     * Handles coordinate extraction and layout change notifications.
     */
    inner class VoxSightJsBridge {

        @JavascriptInterface
        fun onRenderComplete(layoutHash: String, coordinatesJson: String) {
            Log.i(TAG, "OSMD render complete. Layout hash: $layoutHash")

            try {
                val coordType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawCoords: List<Map<String, Any>> = gson.fromJson(coordinatesJson, coordType)

                val osmdElements = rawCoords.map { raw ->
                    SyncManager.OsmdNoteElement(
                        id = (raw["id"] as? Number)?.toInt() ?: 0,
                        tick = (raw["tick"] as? Number)?.toInt() ?: 0,
                        midiNote = (raw["midiNote"] as? Number)?.toInt() ?: 0,
                        measureNumber = (raw["measureNumber"] as? Number)?.toInt() ?: 1,
                        voice = (raw["voice"] as? Number)?.toInt() ?: 1,
                        x = (raw["x"] as? Number)?.toFloat() ?: 0f,
                        y = (raw["y"] as? Number)?.toFloat() ?: 0f,
                        width = (raw["width"] as? Number)?.toFloat() ?: 0f,
                        height = (raw["height"] as? Number)?.toFloat() ?: 0f
                    )
                }

                // Build sync mapping
                syncManager.buildMapping(eventStream, osmdElements, layoutHash)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing OSMD coordinates: ${e.message}", e)
            }
        }

        @JavascriptInterface
        fun onLayoutChanged(layoutHash: String, coordinatesJson: String) {
            Log.w(TAG, "Layout changed — re-building sync mapping")
            onRenderComplete(layoutHash, coordinatesJson)
        }

        @JavascriptInterface
        fun onRenderError(error: String) {
            Log.e(TAG, "OSMD render error: $error")
            runOnUiThread {
                Toast.makeText(this@PracticeActivity,
                    "Render error: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────
    override fun onPause() {
        super.onPause()
        playbackEngine.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackEngine.release()
    }
}
