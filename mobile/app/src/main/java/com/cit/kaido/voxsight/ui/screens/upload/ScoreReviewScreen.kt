package com.cit.kaido.voxsight.ui.screens.upload

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlNote
import com.cit.kaido.voxsight.ui.theme.VoxBackground
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextPrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextSubtitle
import com.google.gson.Gson

data class SelectedNoteInfo(
    val id: Int,
    val pitchStep: String,
    val alter: Int,
    val octave: Int,
    val durationType: String,
    val voiceId: Int,
    val measureNumber: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScoreReviewScreen(
    musicXml: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var selectedNote by remember { mutableStateOf<SelectedNoteInfo?>(null) }
    var isWebLoaded by remember { mutableStateOf(false) }

    // JS Bridge class to capture note clicks
    class EditorJsBridge {
        @JavascriptInterface
        fun onNoteSelected(
            id: Int,
            pitchStep: String,
            alterVal: Int,
            octaveVal: Int,
            durationType: String,
            voiceId: Int,
            measureNum: Int
        ) {
            // Note selected callback from JS Thread - hop to Main thread
            (context as? android.app.Activity)?.runOnUiThread {
                selectedNote = SelectedNoteInfo(
                    id = id,
                    pitchStep = pitchStep,
                    alter = alterVal,
                    octave = octaveVal,
                    durationType = durationType,
                    voiceId = voiceId,
                    measureNumber = measureNum
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Review Digitized Score",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = VoxTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Cancel",
                            tint = VoxPurplePrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Bold, color = VoxTextSubtitle)
                    }

                    Button(
                        onClick = {
                            // Extract modified XML from WebView and confirm
                            webViewInstance?.let { webView ->
                                webView.evaluateJavascript("getModifiedXml();") { result ->
                                    if (result != null && result != "null" && result.isNotEmpty()) {
                                        val rawXml = try {
                                            Gson().fromJson(result, String::class.java)
                                        } catch (e: Exception) {
                                            result.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"")
                                        }
                                        onConfirm(rawXml)
                                    } else {
                                        onConfirm(musicXml)
                                    }
                                }
                            } ?: onConfirm(musicXml)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VoxPurplePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = "Confirm")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CONFIRM & SAVE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VoxBackground)
                .padding(innerPadding)
        ) {
            // Help Hint Label
            Surface(
                color = VoxPurplePrimary.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verify OMR accuracy. Tap any notehead on the sheet music to correct overlapping notes, pitches, durations, or voice parts before saving.",
                        fontSize = 12.sp,
                        color = VoxPurplePrimary,
                        lineHeight = 16.sp
                    )
                }
            }

            // WebView Sheet Music Render Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isWebLoaded = true
                                    // Inject raw MusicXML into page
                                    val escapedXml = escapeJavascriptString(musicXml)
                                    evaluateJavascript("loadScore('$escapedXml');", null)
                                }
                            }
                            addJavascriptInterface(EditorJsBridge(), "VoxSightBridge")
                            loadUrl("file:///android_asset/interactive_editor.html")
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Show Edit sheet if a note is selected
        selectedNote?.let { note ->
            NoteEditorBottomSheet(
                noteId = note.id,
                pitchStep = note.pitchStep,
                alter = note.alter,
                octave = note.octave,
                durationType = note.durationType,
                voiceId = note.voiceId,
                measureNumber = note.measureNumber,
                onVoiceChanged = { newVoiceId ->
                    webViewInstance?.evaluateJavascript("updateNoteVoice(${note.id}, $newVoiceId);", null)
                    selectedNote = selectedNote?.copy(voiceId = newVoiceId)
                },
                onPitchChanged = { newStep, newAlter, newOctave ->
                    webViewInstance?.evaluateJavascript("updateNotePitch(${note.id}, '$newStep', $newAlter, $newOctave);", null)
                    selectedNote = selectedNote?.copy(pitchStep = newStep, alter = newAlter, octave = newOctave)
                },
                onDurationChanged = { newDuration ->
                    webViewInstance?.evaluateJavascript("updateNoteDuration(${note.id}, '$newDuration');", null)
                    selectedNote = selectedNote?.copy(durationType = newDuration)
                },
                onDeleteClicked = {
                    webViewInstance?.evaluateJavascript("deleteNote(${note.id});", null)
                    selectedNote = null
                    Toast.makeText(context, "Note deleted successfully", Toast.LENGTH_SHORT).show()
                },
                onDismissRequest = {
                    selectedNote = null
                    // Clear graphic highlights
                    webViewInstance?.evaluateJavascript("clearHighlights();", null)
                }
            )
        }
    }
}

private fun escapeJavascriptString(str: String): String {
    return str.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}

fun regenerateEventsJsonFromScore(score: MusicXmlScore): String {
    val eventsList = mutableListOf<MusicalEvent>()
    var eventIndex = 0
    val tpq = score.divisions

    score.parts.forEach { part ->
        val satbVoiceStr = when (part.id) {
            1 -> "SOPRANO"
            2 -> "ALTO"
            3 -> "TENOR"
            4 -> "BASS"
            else -> "UNKNOWN"
        }
        
        part.notes.forEach { note ->
            if (note.isRest) return@forEach
            
            val midi = calculateMidiNote(note.step, note.alter, note.octave)
            val pitchName = formatPitchName(note.step, note.alter, note.octave)
            val eventId = "t${note.startTimeDivisions}-p${part.id}-c${eventIndex++}"
            
            eventsList.add(
                MusicalEvent(
                    eventId = eventId,
                    measureNumber = 1 + (note.startTimeDivisions / (tpq * 4)),
                    tickPosition = note.startTimeDivisions,
                    ticksPerQuarter = tpq,
                    pitchMidi = midi,
                    pitchName = pitchName,
                    durationTicks = note.durationDivisions,
                    durationQuarters = note.durationDivisions.toFloat() / tpq,
                    voiceSource = note.voice,
                    staffId = note.staff,
                    partId = part.id,
                    isRest = false,
                    isChordMember = note.isChord,
                    satbVoice = satbVoiceStr,
                    satbConfidence = 1.0f,
                    playbackTrack = satbVoiceStr
                )
            )
        }
    }
    
    return Gson().toJson(eventsList)
}

private fun calculateMidiNote(step: String, alter: Int, octave: Int): Int {
    val base = when (step.uppercase()) {
        "C" -> 0
        "D" -> 2
        "E" -> 4
        "F" -> 5
        "G" -> 7
        "A" -> 9
        "B" -> 11
        else -> 0
    }
    return base + alter + (octave + 1) * 12
}

private fun formatPitchName(step: String, alter: Int, octave: Int): String {
    val alterStr = when (alter) {
        -1 -> "b"
        1 -> "#"
        else -> ""
    }
    return "$step$alterStr$octave"
}
