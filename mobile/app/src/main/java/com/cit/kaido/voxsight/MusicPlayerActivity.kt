package com.cit.kaido.voxsight

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// Data classes matching backend JSON
data class ProcessedResponse(
    val musicxml: String,
    val satb: Satb,
    val tempo: Int
)

data class Satb(
    val S: List<Note>,
    val A: List<Note>,
    val T: List<Note>,
    val B: List<Note>
)

data class Note(
    val pitch: String,
    val time: Double,
    val duration: String,
    // New field from backend; keep nullable for backward compatibility
    val durationBeats: Double? = null
)

interface MusicService {
    @GET("musicxml/{id}/processed")
    suspend fun getProcessed(@Path("id") id: String): ProcessedResponse
}

class MusicPlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val gson = Gson()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_player)

        // Read score ID and backend URL from intent extras
        val scoreId = intent.getStringExtra("SCORE_ID") ?: "sample"
        val backendUrl = intent.getStringExtra("BACKEND_URL") ?: "http://10.71.181.36:8080"

        val retrofit = Retrofit.Builder()
            .baseUrl("$backendUrl/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(MusicService::class.java)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        // Be permissive for WebAudio/Tone.js; Play is still user-driven in the page UI
        try {
            webView.settings.mediaPlaybackRequiresUserGesture = false
        } catch (_: Throwable) {
            // Older WebView implementations may not support this; ignore.
        }
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // After HTML is loaded, fetch data from backend
                fetchAndLoadScore(service, scoreId)
            }
        }
        // Load local HTML from assets (Phase 1: Simple MIDI-based player)
        webView.loadUrl("file:///android_asset/player_phase1.html")
    }

    private fun fetchAndLoadScore(service: MusicService, scoreId: String) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { service.getProcessed(scoreId) }
                val json = gson.toJson(response)
                // Inject JSON into the WebView JS context
                webView.evaluateJavascript("window.loadScore($json);") { /* result ignored */ }
            } catch (e: Exception) {
                e.printStackTrace()
                // Show error in the WebView
                val errorMsg = e.localizedMessage?.replace("'", "\\'") ?: "Unknown error"
                webView.evaluateJavascript(
                    "document.getElementById('score').innerHTML = '<p style=\"color:red;padding:20px\">Error loading score: $errorMsg</p>';",
                    null
                )
            }
        }
    }
}
