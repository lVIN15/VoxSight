package com.cit.kaido.voxsight.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.PracticeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// Premium Sleek Color Palette
val PrimaryColor = Color(0xFF0F172A)    // Slate 900
val AccentColor = Color(0xFF0EA5E9)     // Sky 500
val CardBgColor = Color(0xFF1E293B)     // Slate 800
val TextLightColor = Color(0xFFF8FAFC) // Slate 50
val TextMutedColor = Color(0xFF94A3B8) // Slate 400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmrScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var backendUrl by remember { mutableStateOf("http://192.168.1.4:8080") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var resultFilename by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // v3.7: Store full analysis data for PracticeActivity
    var analysisMusic by remember { mutableStateOf<String?>(null) }
    var analysisEvents by remember { mutableStateOf<String?>(null) }
    var analysisMetadata by remember { mutableStateOf<String?>(null) }
    var satbConfidence by remember { mutableStateOf<String?>(null) }
    var structureType by remember { mutableStateOf<String?>(null) }

    // Launcher for file picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
        if (uri != null) {
            selectedFileName = getFileName(context, uri)
            resultUrl = null
            resultFilename = null
            errorMessage = null
            statusMessage = ""
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "VoxSight OMR Client",
                        fontWeight = FontWeight.Bold,
                        color = TextLightColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextLightColor
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PrimaryColor
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(PrimaryColor, Color(0xFF020617))
                    )
                )
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Server Configuration",
                        color = AccentColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        label = { Text("Backend URL", color = TextMutedColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLightColor,
                            unfocusedTextColor = TextLightColor,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = TextMutedColor
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Use http://192.168.1.4:8080 for physical device on local Wi-Fi.",
                        color = TextMutedColor,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // File Selection & Action Area
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (selectedFileName == null) {
                        Text(
                            "Select Sheet Music",
                            fontWeight = FontWeight.Bold,
                            color = TextLightColor,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Upload an image (PNG/JPG) or PDF to convert it to MusicXML",
                            color = TextMutedColor,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                        ) {
                            Text("Browse Files", color = PrimaryColor, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Selected File Info
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Selected File",
                            tint = AccentColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            selectedFileName ?: "",
                            fontWeight = FontWeight.SemiBold,
                            color = TextLightColor,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
                                enabled = !isLoading
                            ) {
                                Text("Change File")
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        uploadAndAnalyze(
                                            context,
                                            backendUrl,
                                            selectedUri!!,
                                            selectedFileName!!,
                                            onStart = {
                                                isLoading = true
                                                errorMessage = null
                                                resultUrl = null
                                                analysisMusic = null
                                                statusMessage = "Uploading & analyzing (Audiveris + SATB)..."
                                            },
                                            onSuccess = { musicXml, eventsJson, metadataJson, confidence, sType ->
                                                isLoading = false
                                                resultUrl = "analyzed"
                                                resultFilename = selectedFileName
                                                analysisMusic = musicXml
                                                analysisEvents = eventsJson
                                                analysisMetadata = metadataJson
                                                satbConfidence = confidence
                                                structureType = sType
                                                statusMessage = "Analysis complete!"
                                            },
                                            onError = { err ->
                                                isLoading = false
                                                errorMessage = err
                                                statusMessage = ""
                                            }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                                enabled = !isLoading
                            ) {
                                Text("Upload & Analyze", color = PrimaryColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Progress Loader
                    if (isLoading) {
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(color = AccentColor)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            statusMessage,
                            color = TextLightColor,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "This might take up to a minute.",
                            color = TextMutedColor,
                            fontSize = 12.sp
                        )
                    }

                    // Success Result View
                    if (resultUrl != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color.Green,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Analysis Complete!",
                            fontWeight = FontWeight.Bold,
                            color = TextLightColor,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            resultFilename ?: "",
                            color = TextMutedColor,
                            fontSize = 14.sp
                        )
                        // Show SATB metadata
                        if (structureType != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "SATB: $structureType (${satbConfidence ?: "?"})",
                                color = AccentColor,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Play button — launches PracticeActivity with full analysis
                        Button(
                            onClick = {
                                val intent = Intent(context, PracticeActivity::class.java).apply {
                                    putExtra(PracticeActivity.EXTRA_MUSICXML, analysisMusic)
                                    putExtra(PracticeActivity.EXTRA_EVENTS_JSON, analysisEvents)
                                    putExtra(PracticeActivity.EXTRA_METADATA_JSON, analysisMetadata)
                                    putExtra(PracticeActivity.EXTRA_BACKEND_URL, backendUrl)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Play & View SATB",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Error Message View
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extracts the score ID (filename without extension) from the result filename.
 * e.g. "1716234567890-Merry_Go_Round.xml" -> "1716234567890-Merry_Go_Round"
 */
private fun extractScoreId(filename: String?): String {
    if (filename == null) return ""
    val dotIndex = filename.lastIndexOf('.')
    return if (dotIndex > 0) filename.substring(0, dotIndex) else filename
}

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "file"
}

/**
 * Upload file to /api/analyze — runs Audiveris OMR + SATB analysis.
 * Returns raw MusicXML + events[] + score_metadata.
 *
 * Architecture Contract (v3.7):
 *   - musicxml is RAW, UNTOUCHED from Audiveris
 *   - events are ORDER-FROZEN (Fix #40)
 *   - Schema version is always present
 */
private suspend fun uploadAndAnalyze(
    context: Context,
    baseUrl: String,
    uri: Uri,
    filename: String,
    onStart: () -> Unit,
    onSuccess: (musicXml: String, eventsJson: String, metadataJson: String, confidence: String, structureType: String) -> Unit,
    onError: (String) -> Unit
) {
    onStart()

    withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open input stream for file")
            val fileBytes = inputStream.use { it.readBytes() }

            val client = OkHttpClient.Builder()
                .connectTimeout(900, TimeUnit.SECONDS) // Audiveris can take up to 15 minutes for multi-page PDFs
                .readTimeout(900, TimeUnit.SECONDS)
                .writeTimeout(900, TimeUnit.SECONDS)
                .build()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "musicFile",
                    filename,
                    fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/analyze")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (response.isSuccessful && bodyString != null) {
                    val json = JSONObject(bodyString)
                    val success = json.getBoolean("success")
                    if (success) {
                        val musicXml = json.optString("musicxml", "")
                        val events = json.optJSONArray("events")?.toString() ?: "[]"
                        val metadata = json.optJSONObject("score_metadata")?.toString() ?: "{}"

                        // Extract display info
                        val metaObj = json.optJSONObject("score_metadata")
                        val confidence = metaObj?.optDouble("satb_confidence", 0.0)?.let {
                            "%.1f%%".format(it * 100)
                        } ?: "?"
                        val sType = metaObj?.optString("structure_type", "UNCERTAIN") ?: "UNCERTAIN"

                        withContext(Dispatchers.Main) {
                            onSuccess(musicXml, events, metadata, confidence, sType)
                        }
                    } else {
                        val errorMsg = json.optString("error", "Analysis failed.")
                        withContext(Dispatchers.Main) {
                            onError(errorMsg)
                        }
                    }
                } else {
                    val errorDetail = bodyString ?: "Response code ${response.code}"
                    withContext(Dispatchers.Main) {
                        onError("Server Error: $errorDetail")
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Network Error: ${e.localizedMessage ?: e.message}")
            }
        }
    }
}
