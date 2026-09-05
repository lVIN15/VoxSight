package com.cit.kaido.voxsight.ui.screens.upload

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
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
import com.cit.kaido.voxsight.ui.theme.VoxCardStroke
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
    val measureNumber: Int,
    val lyricText: String = "",
    val isRest: Boolean = false,
    val isDotted: Boolean = false
)

data class SelectedMultiRestInfo(
    val measureNumber: Int,
    val count: Int
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
    var selectedMultiRest by remember { mutableStateOf<SelectedMultiRestInfo?>(null) }
    var isWebLoaded by remember { mutableStateOf(false) }
    var showMetadataDialog by remember { mutableStateOf(false) }

    // JS Bridge class to capture note/rest clicks
    class EditorJsBridge {
        @JavascriptInterface
        fun onNoteSelected(
            id: Int,
            pitchStep: String,
            alterVal: Double,   // Float in MusicXML (e.g. 1.0, -1.0); Int bridge would silently fail
            octaveVal: Int,
            durationType: String,
            voiceId: Int,
            measureNum: Int,
            lyricText: String? = "",
            isRest: Boolean = false,
            isDotted: Boolean = false
        ) {
            // Note selected callback from JS Thread - hop to Main thread
            (context as? android.app.Activity)?.runOnUiThread {
                selectedMultiRest = null
                selectedNote = SelectedNoteInfo(
                    id = id,
                    pitchStep = pitchStep,
                    alter = alterVal.toInt(),  // Round: 1.0 → 1, -1.0 → -1, 0.5 → 0
                    octave = octaveVal,
                    durationType = durationType,
                    voiceId = voiceId,
                    measureNumber = measureNum,
                    lyricText = lyricText ?: "",
                    isRest = isRest,
                    isDotted = isDotted
                )
            }
        }

        @JavascriptInterface
        fun onMultiRestSelected(measureNum: Int, count: Int) {
            (context as? android.app.Activity)?.runOnUiThread {
                selectedNote = null
                selectedMultiRest = SelectedMultiRestInfo(measureNum, count)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Review & Edit Score",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
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
                actions = {
                    IconButton(onClick = { showMetadataDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Score Info & Words",
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
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = VoxTextSubtitle)
                        Spacer(modifier = Modifier.width(8.dp))
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VoxPurplePrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = "Confirm", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CONFIRM", fontWeight = FontWeight.Bold, color = Color.White)
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verify OMR accuracy. Tap any notehead or rest symbol on the sheet music to edit pitches, durations, dotted values, rests, or add new elements.",
                        fontSize = 12.sp,
                        color = VoxPurplePrimary,
                        lineHeight = 16.sp
                    )
                }
            }

            // SATB Vocal Part Legend
            Surface(
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VoxCardStroke)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vocal Parts:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoxTextSubtitle
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VoiceLegendItem("Soprano", Color(0xFFE91E63))
                        VoiceLegendItem("Alto", Color(0xFF9C27B0))
                        VoiceLegendItem("Tenor", Color(0xFF2196F3))
                        VoiceLegendItem("Bass", Color(0xFF4CAF50))
                    }
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
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isWebLoaded = true
                                    // Inject raw MusicXML into page using Base64 to avoid quoting issues
                                    val encoded = android.util.Base64.encodeToString(musicXml.toByteArray(), android.util.Base64.NO_WRAP)
                                    evaluateJavascript("loadScoreBase64('$encoded');", null)
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

        // Show Edit Info & Text Words Dialog
        if (showMetadataDialog) {
            ScoreInfoDialog(
                onDismissRequest = { showMetadataDialog = false },
                onApply = { newTitle, newComposer, newArranger, oldWord, newWord ->
                    showMetadataDialog = false
                    val escapedTitle = escapeJavascriptString(newTitle)
                    val escapedComp = escapeJavascriptString(newComposer)
                    val escapedArr = escapeJavascriptString(newArranger)
                    webViewInstance?.evaluateJavascript("updateScoreMetadata('$escapedTitle', '$escapedComp', '$escapedArr');", null)
                    if (oldWord.isNotBlank() && newWord.isNotBlank()) {
                        val escapedOld = escapeJavascriptString(oldWord)
                        val escapedNew = escapeJavascriptString(newWord)
                        webViewInstance?.evaluateJavascript("updateDirectionWord('$escapedOld', '$escapedNew');", null)
                    }
                    Toast.makeText(context, "Score info updated", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Show Edit sheet if a note or rest is selected
        selectedNote?.let { note ->
            NoteEditorBottomSheet(
                noteId = note.id,
                pitchStep = note.pitchStep,
                alter = note.alter,
                octave = note.octave,
                durationType = note.durationType,
                voiceId = note.voiceId,
                measureNumber = note.measureNumber,
                lyricText = note.lyricText,
                isRest = note.isRest,
                isDotted = note.isDotted,
                onLyricChanged = { newLyric ->
                    val escapedLyric = escapeJavascriptString(newLyric)
                    webViewInstance?.evaluateJavascript("updateNoteLyric(${note.id}, '$escapedLyric');", null)
                    selectedNote = selectedNote?.copy(lyricText = newLyric)
                },
                onVoiceChanged = { newVoiceId ->
                    webViewInstance?.evaluateJavascript("updateNoteVoice(${note.id}, $newVoiceId);", null)
                    selectedNote = selectedNote?.copy(voiceId = newVoiceId)
                },
                onPitchChanged = { newStep, newAlter, newOctave ->
                    webViewInstance?.evaluateJavascript("updateNotePitch(${note.id}, '$newStep', $newAlter, $newOctave);", null)
                    selectedNote = selectedNote?.copy(pitchStep = newStep, alter = newAlter, octave = newOctave, isRest = false)
                },
                onDurationChanged = { newDuration, dotted ->
                    webViewInstance?.evaluateJavascript("updateNoteDuration(${note.id}, '$newDuration', $dotted);", null)
                    selectedNote = selectedNote?.copy(durationType = newDuration, isDotted = dotted)
                },
                onToggleDot = { dotted ->
                    webViewInstance?.evaluateJavascript("toggleNoteDot(${note.id}, $dotted);", null)
                    selectedNote = selectedNote?.copy(isDotted = dotted)
                },
                onConvertToRest = {
                    webViewInstance?.evaluateJavascript("convertToRest(${note.id});", null)
                    selectedNote = selectedNote?.copy(isRest = true)
                },
                onConvertToNote = { step, alter, octave ->
                    webViewInstance?.evaluateJavascript("convertToNote(${note.id}, '$step', $alter, $octave);", null)
                    selectedNote = selectedNote?.copy(isRest = false, pitchStep = step, alter = alter, octave = octave)
                },
                onAddNote = { pos, step, alter, octave, type, dotted ->
                    webViewInstance?.evaluateJavascript("addNote(${note.id}, '$pos', '$step', $alter, $octave, '$type', $dotted);", null)
                    Toast.makeText(context, "Note added", Toast.LENGTH_SHORT).show()
                },
                onAddRest = { pos, type, dotted ->
                    webViewInstance?.evaluateJavascript("addRest(${note.id}, '$pos', '$type', $dotted);", null)
                    Toast.makeText(context, "Rest added", Toast.LENGTH_SHORT).show()
                },
                onDeleteClicked = {
                    webViewInstance?.evaluateJavascript("deleteNote(${note.id});", null)
                    selectedNote = null
                    Toast.makeText(context, "Element deleted successfully", Toast.LENGTH_SHORT).show()
                },
                onDismissRequest = {
                    selectedNote = null
                    // Clear graphic highlights
                    webViewInstance?.evaluateJavascript("clearHighlights();", null)
                }
            )
        }

        // Show Native Edit Bottom Sheet if a multimeasure rest (H-bar) is selected
        selectedMultiRest?.let { multiRest ->
            MultiRestEditorBottomSheet(
                measureNumber = multiRest.measureNumber,
                count = multiRest.count,
                onCountChanged = { delta ->
                    webViewInstance?.evaluateJavascript("adjustMultiRestCount($delta);", null)
                    val newCount = (multiRest.count + delta).coerceAtLeast(1)
                    selectedMultiRest = selectedMultiRest?.copy(count = newCount)
                },
                onSetCount = { targetCount ->
                    val delta = targetCount - multiRest.count
                    if (delta != 0) {
                        webViewInstance?.evaluateJavascript("adjustMultiRestCount($delta);", null)
                        selectedMultiRest = selectedMultiRest?.copy(count = targetCount)
                    }
                },
                onDismissRequest = {
                    selectedMultiRest = null
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
        part.notes.forEach { note ->
            // Priority 1: Explicit user override from data-vx-voice (customVoice)
            // Priority 2: Voice assigned by MusicXmlParser (1=Soprano, 2=Alto, 3=Tenor, 4=Bass)
            // Priority 3: Fallback based on part structure
            val satbVoiceStr = when {
                note.customVoice != null && note.customVoice in 1..4 -> when (note.customVoice) {
                    1 -> "SOPRANO"
                    2 -> "ALTO"
                    3 -> "TENOR"
                    4 -> "BASS"
                    else -> "SOPRANO"
                }
                score.parts.size >= 4 -> when (part.id) {
                    1 -> "SOPRANO"
                    2 -> "ALTO"
                    3 -> "TENOR"
                    4 -> "BASS"
                    else -> "SOPRANO"
                }
                score.parts.size == 2 -> when (part.id) {
                    1 -> if (note.voice == 2 || note.originalVoice == 2) "ALTO" else "SOPRANO"
                    2 -> if (note.voice == 2 || note.originalVoice == 2 || note.voice == 4) "BASS" else "TENOR"
                    else -> "SOPRANO"
                }
                note.staff == 2 -> if (note.voice == 2 || note.originalVoice == 2 || note.voice == 4) "BASS" else "TENOR"
                note.staff == 1 -> if (note.voice == 2 || note.originalVoice == 2) "ALTO" else "SOPRANO"
                note.voice in 1..4 -> when (note.voice) {
                    1 -> "SOPRANO"
                    2 -> "ALTO"
                    3 -> "TENOR"
                    4 -> "BASS"
                    else -> "SOPRANO"
                }
                else -> "SOPRANO"
            }
            
            val midi = if (note.isRest) 0 else calculateMidiNote(note.step, note.alter, note.octave)
            val pitchName = if (note.isRest) "Rest" else formatPitchName(note.step, note.alter, note.octave)
            val eventId = "t${note.startTimeDivisions}-p${part.id}-c${eventIndex++}"
            
            eventsList.add(
                MusicalEvent(
                    eventId = eventId,
                    measureNumber = note.measureNumber,
                    measureIndex = note.measureIndex,
                    tickPosition = note.startTimeDivisions,
                    ticksPerQuarter = tpq,
                    pitchMidi = midi,
                    pitchName = pitchName,
                    durationTicks = note.durationDivisions,
                    durationQuarters = note.durationDivisions.toFloat() / tpq,
                    voiceSource = note.voice,
                    staffId = note.staff,
                    partId = part.id,
                    isRest = note.isRest,
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
    // MusicXmlParser may embed accidentals in step (e.g. "C#", "Fb").
    // Extract just the base letter for the lookup.
    val baseLetter = step.take(1).uppercase()
    val base = when (baseLetter) {
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

@Composable
fun ScoreInfoDialog(
    onDismissRequest: () -> Unit,
    onApply: (title: String, composer: String, arranger: String, oldWord: String, newWord: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var composer by remember { mutableStateOf("") }
    var arranger by remember { mutableStateOf("") }
    var oldWord by remember { mutableStateOf("") }
    var newWord by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Edit Score Info & Words",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = VoxTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Header Information",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = VoxPurplePrimary
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Score Title") },
                    placeholder = { Text("e.g. Happy Birthday To You") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = composer,
                    onValueChange = { composer = it },
                    label = { Text("Composer") },
                    placeholder = { Text("e.g. Patty and Mildred J. Hill") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = arranger,
                    onValueChange = { arranger = it },
                    label = { Text("Arranger / Lyricist") },
                    placeholder = { Text("e.g. Arranged by Jed Scott") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Edit Floating Words / Annotations",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = VoxPurplePrimary
                )
                Text(
                    text = "Correct OCR typos in performance directions (e.g. optional descant, allegro, solo):",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoxTextSubtitle
                )
                OutlinedTextField(
                    value = oldWord,
                    onValueChange = { oldWord = it },
                    label = { Text("Current Word / Typo") },
                    placeholder = { Text("e.g. optional descanr") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    label = { Text("Corrected Word") },
                    placeholder = { Text("e.g. optional descant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(title, composer, arranger, oldWord, newWord) },
                colors = ButtonDefaults.buttonColors(containerColor = VoxPurplePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("APPLY", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("CANCEL", color = VoxTextSubtitle)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun VoiceLegendItem(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
