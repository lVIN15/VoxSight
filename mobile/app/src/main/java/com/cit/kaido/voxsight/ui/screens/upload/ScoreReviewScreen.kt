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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.filled.DragHandle
import kotlin.math.roundToInt
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlNote
import com.cit.kaido.voxsight.ui.theme.VoxBackground
import com.cit.kaido.voxsight.ui.theme.VoxCardStroke
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextPrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextSubtitle
import com.cit.kaido.voxsight.ui.screens.practice.parseMusicXmlFromString
import com.cit.kaido.voxsight.ui.screens.practice.detectAvailableParts
import com.cit.kaido.voxsight.ui.screens.practice.VoicePart
import com.google.gson.Gson

data class SelectedNoteInfo(
    val id: Int,
    val pitchStep: String,
    val alter: Int,
    val octave: Int,
    val durationType: String,
    val voiceId: Int,
    val voiceCode: String = "",
    val measureNumber: Int,
    val lyricText: String = "",
    val isRest: Boolean = false,
    val isDotted: Boolean = false
)

data class SelectedMultiRestInfo(
    val measureNumber: Int,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScoreReviewScreen(
    musicXml: String,
    originalScoreUri: String? = null,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var selectedNote by remember { mutableStateOf<SelectedNoteInfo?>(null) }
    var showNoteBottomSheet by remember { mutableStateOf(false) }
    var showOriginalScoreSheet by remember { mutableStateOf(false) }
    var selectedMultiRest by remember { mutableStateOf<SelectedMultiRestInfo?>(null) }
    var isWebLoaded by remember { mutableStateOf(false) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var zoomOffsetX by remember { mutableStateOf(0f) }
    var zoomOffsetY by remember { mutableStateOf(0f) }
    var editorCurrentPage by remember { mutableIntStateOf(1) }
    var editorTotalPages by remember { mutableIntStateOf(1) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    val parsedScore = remember(musicXml) { parseMusicXmlFromString(musicXml) }
    var dynamicVoiceCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    val legendItems = remember(parsedScore, dynamicVoiceCodes) {
        extractScorePartLegendItems(parsedScore, dynamicVoiceCodes)
    }

    LaunchedEffect(musicXml, isWebLoaded) {
        if (isWebLoaded && musicXml.isNotBlank()) {
            val encoded = android.util.Base64.encodeToString(musicXml.toByteArray(), android.util.Base64.NO_WRAP)
            val palette = com.cit.kaido.voxsight.ui.screens.practice.VoicePalette.asJson()
            webViewInstance?.evaluateJavascript("setVoicePalette($palette);loadScoreBase64('$encoded');", null)
        }
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        webViewInstance?.evaluateJavascript("zoomFit();", null)
    }

    BackHandler(enabled = !isFullscreen) {
        onCancel()
    }

    // JS Bridge class to capture note/rest clicks
    class EditorJsBridge {
        @JavascriptInterface
        fun onHistoryChanged(canUndoVal: Boolean, canRedoVal: Boolean) {
            (context as? android.app.Activity)?.runOnUiThread {
                canUndo = canUndoVal
                canRedo = canRedoVal
            }
        }
        @JavascriptInterface
        fun onPageChanged(currentPage: Int, totalPages: Int) {
            (context as? android.app.Activity)?.runOnUiThread {
                editorCurrentPage = currentPage
                editorTotalPages = totalPages
            }
        }
        @JavascriptInterface
        fun onNoteSelected(
            id: Int,
            pitchStep: String,
            alterVal: Double,
            octaveVal: Int,
            durationType: String,
            voiceId: Int,
            measureNum: Int,
            lyricText: String? = "",
            isRest: Boolean = false,
            isDotted: Boolean = false,
            voiceCode: String? = null
        ) {
            (context as? android.app.Activity)?.runOnUiThread {
                selectedMultiRest = null
                selectedNote = SelectedNoteInfo(
                    id = id,
                    pitchStep = pitchStep,
                    alter = alterVal.toInt(),
                    octave = octaveVal,
                    durationType = durationType,
                    voiceId = voiceId,
                    voiceCode = voiceCode ?: "",
                    measureNumber = measureNum,
                    lyricText = lyricText ?: "",
                    isRest = isRest,
                    isDotted = isDotted
                )
                // In-situ note selection: bottom sheet is NOT shown automatically on selection,
                // keeping the sheet music clear for direct drag-and-drop & micro-HUD interaction.
            }
        }

        @JavascriptInterface
        fun onNoteDeselected() {
            (context as? android.app.Activity)?.runOnUiThread {
                selectedNote = null
                showNoteBottomSheet = false
            }
        }

        @JavascriptInterface
        fun onOpenNoteDetails() {
            (context as? android.app.Activity)?.runOnUiThread {
                if (selectedNote != null) {
                    showNoteBottomSheet = true
                }
            }
        }

        @JavascriptInterface
        fun onMultiRestSelected(measureNum: Int, count: Int) {
            (context as? android.app.Activity)?.runOnUiThread {
                selectedNote = null
                showNoteBottomSheet = false
                selectedMultiRest = SelectedMultiRestInfo(measureNum, count)
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
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
                        IconButton(
                            onClick = { webViewInstance?.evaluateJavascript("undo();", null) },
                            enabled = canUndo
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Undo,
                                contentDescription = "Undo",
                                tint = if (canUndo) VoxPurplePrimary else Color.LightGray
                            )
                        }
                        IconButton(
                            onClick = { webViewInstance?.evaluateJavascript("redo();", null) },
                            enabled = canRedo
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Redo,
                                contentDescription = "Redo",
                                tint = if (canRedo) VoxPurplePrimary else Color.LightGray
                            )
                        }
                        if (!originalScoreUri.isNullOrBlank()) {
                            IconButton(onClick = { showOriginalScoreSheet = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Visibility,
                                    contentDescription = "Compare with Original Scan",
                                    tint = VoxPurplePrimary
                                )
                            }
                        }
                        if (selectedNote != null) {
                            IconButton(onClick = { showNoteBottomSheet = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit Details & Lyrics",
                                    tint = VoxPurplePrimary
                                )
                            }
                        }
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
            }
        },
        bottomBar = {
            if (!isFullscreen) {
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
                                        if (result != null && result != "null" && result.isNotEmpty() && result != "\"\"") {
                                            val rawXml = try {
                                                Gson().fromJson(result, String::class.java)
                                            } catch (e: Exception) {
                                                result.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"")
                                            }
                                            if (!rawXml.isNullOrBlank()) {
                                                onConfirm(rawXml)
                                            } else {
                                                onConfirm(musicXml)
                                            }
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isFullscreen) Color.White else VoxBackground)
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
        ) {
            if (!isFullscreen) {
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

                // Score Parts Legend
                Surface(
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, VoxCardStroke)
                ) {
                    var isExpanded by remember { mutableStateOf(false) }
                    val showExpandToggle = legendItems.size > 4

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Score Parts",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VoxTextSubtitle
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(VoxPurplePrimary.copy(alpha = 0.08f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${legendItems.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VoxPurplePrimary
                                    )
                                }
                            }

                            if (!isExpanded) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    legendItems.forEach { item ->
                                        ScorePartLegendChip(item)
                                    }
                                }
                            }

                            if (showExpandToggle) {
                                IconButton(
                                    onClick = { isExpanded = !isExpanded },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand All Score Parts",
                                        tint = VoxTextSubtitle,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (isExpanded) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                legendItems.forEach { item ->
                                    ScorePartLegendChip(item)
                                }
                            }
                        }
                    }
                }
            }

            // WebView Sheet Music Render Area
            Box(
                modifier = if (isFullscreen) {
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .background(Color.White)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                }
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.WHITE)
                            val activityManager = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                            val isLowEnd = activityManager?.isLowRamDevice == true || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q
                            if (isLowEnd) {
                                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                            } else {
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                            settings.allowFileAccess = true
                            settings.setSupportZoom(false)
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            settings.useWideViewPort = false
                            settings.loadWithOverviewMode = false
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isWebLoaded = true
                                }
                            }
                            addJavascriptInterface(EditorJsBridge(), "VoxSightBridge")
                            loadUrl("file:///android_asset/interactive_editor.html")
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Original Score Button
                if (!originalScoreUri.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(if (isFullscreen) 16.dp else 10.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                showOriginalScoreSheet = true
                            },
                        color = Color.White.copy(alpha = 0.94f),
                        shadowElevation = 6.dp,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, VoxPurplePrimary.copy(alpha = 0.35f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Visibility,
                                contentDescription = "Compare with Original Scan",
                                tint = VoxPurplePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Floating Fullscreen / Exit Fullscreen Button
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(if (isFullscreen) 16.dp else 10.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable {
                            isFullscreen = !isFullscreen
                            webViewInstance?.evaluateJavascript("zoomFit();", null)
                        },
                    color = Color.White.copy(alpha = 0.94f),
                    shadowElevation = 6.dp,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, VoxPurplePrimary.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen View",
                            tint = VoxPurplePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Floating Page Navigation Pill (Visible for multi-page scores, docked at top center)
                if (editorTotalPages > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp),
                        color = Color.White.copy(alpha = 0.95f),
                        shadowElevation = 6.dp,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, VoxPurplePrimary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { webViewInstance?.evaluateJavascript("prevPage();", null) },
                                enabled = editorCurrentPage > 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Page",
                                    tint = if (editorCurrentPage > 1) VoxPurplePrimary else Color.LightGray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Text(
                                text = "Page $editorCurrentPage of $editorTotalPages",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = VoxPurplePrimary,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            IconButton(
                                onClick = { webViewInstance?.evaluateJavascript("nextPage();", null) },
                                enabled = editorCurrentPage < editorTotalPages,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Page",
                                    tint = if (editorCurrentPage < editorTotalPages) VoxPurplePrimary else Color.LightGray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Floating Zoom Controls Pill (Positioned in bottom-right margin, draggable if moved)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = if (isFullscreen) 12.dp else 16.dp)
                        .offset { IntOffset(zoomOffsetX.roundToInt(), zoomOffsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                zoomOffsetX += dragAmount.x
                                zoomOffsetY += dragAmount.y
                            }
                        },
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, VoxPurplePrimary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to move",
                            tint = VoxPurplePrimary.copy(alpha = 0.45f),
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 2.dp)
                        )
                        IconButton(
                            onClick = { webViewInstance?.evaluateJavascript("zoomOut();", null) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomOut,
                                contentDescription = "Zoom Out",
                                tint = VoxPurplePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        TextButton(
                            onClick = { webViewInstance?.evaluateJavascript("zoomFit();", null) },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "FIT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = VoxPurplePrimary
                            )
                        }

                        IconButton(
                            onClick = { webViewInstance?.evaluateJavascript("zoomIn();", null) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomIn,
                                contentDescription = "Zoom In",
                                tint = VoxPurplePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
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

        // Show Edit sheet only if user explicitly requested note details & lyrics
        if (showOriginalScoreSheet && !originalScoreUri.isNullOrBlank()) {
        ModalBottomSheet(
            onDismissRequest = { showOriginalScoreSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            OriginalScoreViewer(
                uriString = originalScoreUri,
                onClose = { showOriginalScoreSheet = false }
            )
        }
    }

    if (showNoteBottomSheet) {
            selectedNote?.let { note ->
                NoteEditorBottomSheet(
                    noteId = note.id,
                    pitchStep = note.pitchStep,
                    alter = note.alter,
                    octave = note.octave,
                    durationType = note.durationType,
                    voiceId = note.voiceId,
                    voiceCode = note.voiceCode,
                    measureNumber = note.measureNumber,
                    lyricText = note.lyricText,
                    isRest = note.isRest,
                    isDotted = note.isDotted,
                    onLyricChanged = { newLyric ->
                        val escapedLyric = escapeJavascriptString(newLyric)
                        webViewInstance?.evaluateJavascript("updateNoteLyric(${note.id}, '$escapedLyric');", null)
                        selectedNote = selectedNote?.copy(lyricText = newLyric)
                    },
                    onVoiceChanged = { newVoiceCode ->
                        val escapedCode = escapeJavascriptString(newVoiceCode)
                        webViewInstance?.evaluateJavascript("updateNoteVoice(${note.id}, '$escapedCode');", null)
                        selectedNote = selectedNote?.copy(voiceCode = newVoiceCode)
                        dynamicVoiceCodes = dynamicVoiceCodes + newVoiceCode
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
                        showNoteBottomSheet = false
                        Toast.makeText(context, "Element deleted successfully", Toast.LENGTH_SHORT).show()
                    },
                    onDismissRequest = {
                        showNoteBottomSheet = false
                    }
                )
            }
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
        val nameLower = part.name.lowercase()
        val nonRestNotes = part.notes.filter { !it.isRest }
        val avgMidi = if (nonRestNotes.isNotEmpty()) {
            nonRestNotes.map { calculateMidiNote(it.step, it.alter, it.octave) }.average()
        } else {
            0.0
        }
        val isBaritoneSoloPart = nameLower.contains("baritone") || nameLower.contains("solo") ||
            nameLower.contains("cantor") || nameLower.contains("leader") || nameLower.contains("descant") ||
            nameLower.contains("sol.") || nameLower.trim() == "sol" || Regex("(?i)\\bsol\\b").containsMatchIn(nameLower)

        val isCondensedSA = (nameLower.contains("soprano") && nameLower.contains("alto")) ||
            nameLower.contains("s/a") || nameLower.contains("s & a") || nameLower.contains("s.a.") || nameLower.contains("s. a.") || nameLower == "sa"
        val isCondensedTB = (nameLower.contains("tenor") && nameLower.contains("bass")) ||
            nameLower.contains("t/b") || nameLower.contains("t & b") || nameLower.contains("t.b.") || nameLower.contains("t. b.") || nameLower == "tb"

        part.notes.forEach { note ->
            // Priority 1: Explicit user override from data-vx-voice (customVoiceStr or customVoice)
            // Priority 2: Part name keywords (Solo, Others, Condensed SA/TB, Soprano, Alto, Tenor, Bass)
            // Priority 3: Fallback based on part structure and voice numbers
            val satbVoiceStr = when {
                !note.customVoiceStr.isNullOrBlank() -> when (note.customVoiceStr.uppercase()) {
                    "1", "S", "SOPRANO" -> "SOPRANO"
                    "S1", "SOPRANO1", "SOPRANO 1" -> "SOPRANO 1"
                    "S2", "SOPRANO2", "SOPRANO 2" -> "SOPRANO 2"
                    "2", "A", "ALTO" -> "ALTO"
                    "A1", "ALTO1", "ALTO 1" -> "ALTO 1"
                    "A2", "ALTO2", "ALTO 2" -> "ALTO 2"
                    "3", "T", "TENOR" -> "TENOR"
                    "T1", "TENOR1", "TENOR 1" -> "TENOR 1"
                    "T2", "TENOR2", "TENOR 2" -> "TENOR 2"
                    "4", "B", "BASS" -> "BASS"
                    "B1", "BASS1", "BASS 1" -> "BASS 1"
                    "B2", "BASS2", "BASS 2" -> "BASS 2"
                    "UNISON_TB", "TB" -> "TENOR / BASS"
                    "UNISON_SA", "SA" -> "SOPRANO / ALTO"
                    "UNISON_S1S2", "S1S2" -> "SOPRANO 1 / 2"
                    "UNISON_A1A2", "A1A2" -> "ALTO 1 / 2"
                    "UNISON_T1T2", "T1T2" -> "TENOR 1 / 2"
                    "UNISON_B1B2", "B1B2" -> "BASS 1 / 2"
                    "TUTTI", "UNISON_ALL", "ALL" -> "TUTTI"
                    "BARITONE SOLO", "BARITONE_SOLO", "BARITONE", "5", "SOLO" -> "SOLO"
                    "6", "OTHERS", "OTHER" -> "OTHERS"
                    else -> note.customVoiceStr
                }
                note.customVoice != null && note.customVoice in 1..6 -> when (note.customVoice) {
                    1 -> "SOPRANO"
                    2 -> "ALTO"
                    3 -> "TENOR"
                    4 -> "BASS"
                    5 -> "SOLO"
                    6 -> "OTHERS"
                    else -> "SOPRANO"
                }
                // Guard: If this is the only part in the score and its name contains "solo",
                // only intro measures before m6 are true solo; subsequent measures are choral SATB.
                score.parts.size == 1 && (nameLower.contains("solo") || nameLower.contains("voice")) -> {
                    if (note.measureNumber < 6) {
                        "SOLO"
                    } else {
                        val midi = calculateMidiNote(note.step, note.alter, note.octave)
                        when {
                            note.staff == 2 || midi < 58 -> "BASS"
                            midi in 58..64 -> "TENOR"
                            midi in 65..71 -> "ALTO"
                            else -> "SOPRANO"
                        }
                    }
                }
                isBaritoneSoloPart -> "BARITONE SOLO"
                nameLower.contains("solo") || nameLower.contains("cantor") || nameLower.contains("leader") || nameLower.contains("descant") -> "SOLO"
                nameLower.contains("piano") || nameLower.contains("organ") || nameLower.contains("keyboard") || nameLower.contains("guitar") || nameLower.contains("accomp") || nameLower.contains("orch") || nameLower.contains("strings") || nameLower.contains("other") -> "OTHERS"
                isCondensedSA -> if (note.voice == 2 || note.originalVoice == 2) "ALTO" else "SOPRANO"
                isCondensedTB -> if (note.voice == 4 || note.voice == 2 || note.originalVoice == 2 || note.originalVoice == 4) "BASS" else "TENOR"
                nameLower.contains("soprano 1") || nameLower == "s1" || nameLower == "s 1" -> "SOPRANO 1"
                nameLower.contains("soprano 2") || nameLower == "s2" || nameLower == "s 2" -> "SOPRANO 2"
                nameLower.contains("alto 1") || nameLower == "a1" || nameLower == "a 1" -> "ALTO 1"
                nameLower.contains("alto 2") || nameLower == "a2" || nameLower == "a 2" -> "ALTO 2"
                nameLower.contains("tenor 1") || nameLower == "t1" || nameLower == "t 1" -> "TENOR 1"
                nameLower.contains("tenor 2") || nameLower == "t2" || nameLower == "t 2" -> "TENOR 2"
                nameLower.contains("bass 1") || nameLower == "b1" || nameLower == "b 1" -> "BASS 1"
                nameLower.contains("bass 2") || nameLower == "b2" || nameLower == "b 2" -> "BASS 2"
                nameLower.contains("soprano") || nameLower == "s" || nameLower.startsWith("s.") || Regex("(?i)\\bs\\b").containsMatchIn(nameLower) -> "SOPRANO"
                nameLower.contains("alto") || nameLower == "a" || nameLower.startsWith("a.") || Regex("(?i)\\ba\\b").containsMatchIn(nameLower) -> "ALTO"
                nameLower.contains("tenor") || nameLower == "t" || nameLower.startsWith("t.") || Regex("(?i)\\bt\\b").containsMatchIn(nameLower) -> "TENOR"
                nameLower.contains("bass") || nameLower == "b" || nameLower.startsWith("b.") || Regex("(?i)\\bb\\b").containsMatchIn(nameLower) -> "BASS"
                score.parts.size >= 4 -> {
                    val nonSoloParts = score.parts.filter { p ->
                        val pLower = p.name.lowercase()
                        !listOf("solo", "cantor", "leader", "descant", "piano", "organ", "keyboard", "guitar", "accomp", "orch", "strings", "other", "instrument").any { pLower.contains(it) }
                    }
                    val sortedByPitch = nonSoloParts.sortedByDescending { p ->
                        val pNotes = p.notes.filter { !it.isRest }
                        if (pNotes.isNotEmpty()) pNotes.map { calculateMidiNote(it.step, it.alter, it.octave) }.average() else 0.0
                    }
                    val rank = sortedByPitch.indexOf(part)
                    when (rank) {
                        0 -> "SOPRANO"
                        1 -> "ALTO"
                        2 -> "TENOR"
                        3 -> "BASS"
                        else -> "OTHERS"
                    }
                }
                note.voice == 5 -> "SOLO"
                note.voice == 6 -> "OTHERS"
                note.voice in 1..4 -> when (note.voice) {
                    1 -> "SOPRANO"
                    2 -> "ALTO"
                    3 -> "TENOR"
                    4 -> "BASS"
                    else -> "SOPRANO"
                }
                score.parts.size == 2 -> when (part.id) {
                    1 -> if (note.voice == 2 || note.originalVoice == 2) "ALTO" else "SOPRANO"
                    2 -> if (note.voice == 4 || note.voice == 2 || note.originalVoice == 2 || note.originalVoice == 4) "BASS" else "TENOR"
                    else -> "SOPRANO"
                }
                note.staff == 2 -> if (note.voice == 4 || note.voice == 2 || note.originalVoice == 2 || note.originalVoice == 4) "BASS" else "TENOR"
                note.staff == 1 -> if (note.voice == 2 || note.originalVoice == 2) "ALTO" else "SOPRANO"
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
private fun ScorePartLegendChip(item: ScorePartLegendItem) {
    Surface(
        color = item.color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, item.color.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(item.color)
            )
            Text(
                text = item.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = item.color
            )
        }
    }
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

data class ScorePartLegendItem(
    val code: String,
    val name: String,
    val color: Color
)

fun extractScorePartLegendItems(
    score: MusicXmlScore?,
    dynamicVoiceCodes: Set<String> = emptySet()
): List<ScorePartLegendItem> {
    if (score == null) {
        return listOf(
            ScorePartLegendItem("S", "Soprano", Color(0xFFE11D48)),
            ScorePartLegendItem("A", "Alto", Color(0xFF8E24AA)),
            ScorePartLegendItem("T", "Tenor", Color(0xFF2563EB)),
            ScorePartLegendItem("B", "Bass", Color(0xFF16A34A))
        )
    }

    val availableVoiceParts = detectAvailableParts(score)
    val result = mutableListOf<ScorePartLegendItem>()
    val addedCodes = mutableSetOf<String>()

    fun addPartIfNew(code: String, name: String, color: Color) {
        if (addedCodes.add(code.uppercase())) {
            result.add(ScorePartLegendItem(code, name, color))
        }
    }

    // 1. Add detected vocal parts from score (core sections and divisi)
    availableVoiceParts.forEach { vp ->
        val code = vp.apiCode
        val label = vp.label
        val color = vp.color
        addPartIfNew(code, label, color)
    }

    // 2. Check for any merged or unison notes in the score
    val scoreCustomVoices = mutableSetOf<String>()
    score.notes.forEach { note ->
        if (!note.customVoiceStr.isNullOrBlank()) {
            scoreCustomVoices.add(note.customVoiceStr.uppercase().trim())
        }
    }
    score.parts.forEach { part ->
        part.notes.forEach { note ->
            if (!note.customVoiceStr.isNullOrBlank()) {
                scoreCustomVoices.add(note.customVoiceStr.uppercase().trim())
            }
        }
    }
    // Include dynamic voice codes from interactive editing in this session
    scoreCustomVoices.addAll(dynamicVoiceCodes.map { it.uppercase().trim() })

    // Add cross-family unisons and special indicators if present in score or dynamic session
    if (scoreCustomVoices.any { it in setOf("UNISON_TB", "TB") }) {
        addPartIfNew("UNISON_TB", "Tenor / Bass", Color(0xFF0D9488))
    }
    if (scoreCustomVoices.any { it in setOf("UNISON_SA", "SA") }) {
        addPartIfNew("UNISON_SA", "Soprano / Alto", Color(0xFF4F46E5))
    }
    if (scoreCustomVoices.any { it in setOf("TUTTI", "UNISON_ALL", "ALL") }) {
        addPartIfNew("TUTTI", "Tutti (All)", Color(0xFFCA8A04))
    }

    // Add divisi parts if present in note voices
    if (scoreCustomVoices.any { it in setOf("S1", "SOPRANO 1", "SOPRANO1") }) {
        addPartIfNew("S1", "Soprano 1", Color(0xFFFB7185))
    }
    if (scoreCustomVoices.any { it in setOf("S2", "SOPRANO 2", "SOPRANO2") }) {
        addPartIfNew("S2", "Soprano 2", Color(0xFF881337))
    }
    if (scoreCustomVoices.any { it in setOf("A1", "ALTO 1", "ALTO1") }) {
        addPartIfNew("A1", "Alto 1", Color(0xFFC084FC))
    }
    if (scoreCustomVoices.any { it in setOf("A2", "ALTO 2", "ALTO2") }) {
        addPartIfNew("A2", "Alto 2", Color(0xFF4C1D95))
    }
    if (scoreCustomVoices.any { it in setOf("T1", "TENOR 1", "TENOR1") }) {
        addPartIfNew("T1", "Tenor 1", Color(0xFF0284C7))
    }
    if (scoreCustomVoices.any { it in setOf("T2", "TENOR 2", "TENOR2") }) {
        addPartIfNew("T2", "Tenor 2", Color(0xFF1E3A8A))
    }
    if (scoreCustomVoices.any { it in setOf("B1", "BASS 1", "BASS1") }) {
        addPartIfNew("B1", "Bass 1", Color(0xFF65A30D))
    }
    if (scoreCustomVoices.any { it in setOf("B2", "BASS 2", "BASS2") }) {
        addPartIfNew("B2", "Bass 2", Color(0xFF064E3B))
    }

    // Dynamic divisi or other parts added by user during editing
    dynamicVoiceCodes.forEach { code ->
        val upper = code.uppercase().trim()
        when (upper) {
            "S" -> addPartIfNew("S", "Soprano", Color(0xFFE11D48))
            "S1" -> addPartIfNew("S1", "Soprano 1", Color(0xFFFB7185))
            "S2" -> addPartIfNew("S2", "Soprano 2", Color(0xFF881337))
            "A" -> addPartIfNew("A", "Alto", Color(0xFF8E24AA))
            "A1" -> addPartIfNew("A1", "Alto 1", Color(0xFFC084FC))
            "A2" -> addPartIfNew("A2", "Alto 2", Color(0xFF4C1D95))
            "T" -> addPartIfNew("T", "Tenor", Color(0xFF2563EB))
            "T1" -> addPartIfNew("T1", "Tenor 1", Color(0xFF0284C7))
            "T2" -> addPartIfNew("T2", "Tenor 2", Color(0xFF1E3A8A))
            "B" -> addPartIfNew("B", "Bass", Color(0xFF16A34A))
            "B1" -> addPartIfNew("B1", "Bass 1", Color(0xFF65A30D))
            "B2" -> addPartIfNew("B2", "Bass 2", Color(0xFF064E3B))
            "UNISON_TB", "TB" -> addPartIfNew("UNISON_TB", "Tenor / Bass", Color(0xFF0D9488))
            "UNISON_SA", "SA" -> addPartIfNew("UNISON_SA", "Soprano / Alto", Color(0xFF4F46E5))
            "TUTTI", "UNISON_ALL", "ALL" -> addPartIfNew("TUTTI", "Tutti (All)", Color(0xFFCA8A04))
            "SOLO", "BARITONE SOLO", "BARITONE", "TENOR SOLO", "SOPRANO SOLO", "ALTO SOLO", "BASS SOLO" -> addPartIfNew("SOLO", "Solo", Color(0xFFFF6D00))
            "OTHERS" -> addPartIfNew("OTHERS", "Accompaniment", Color(0xFF546E7A))
        }
    }

    return if (result.isNotEmpty()) result else listOf(
        ScorePartLegendItem("S", "Soprano", Color(0xFFE11D48)),
        ScorePartLegendItem("A", "Alto", Color(0xFF8E24AA)),
        ScorePartLegendItem("T", "Tenor", Color(0xFF2563EB)),
        ScorePartLegendItem("B", "Bass", Color(0xFF16A34A))
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriginalScoreViewer(
    uriString: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uriString, currentPage) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val uri = android.net.Uri.parse(uriString)
                val isPdf = uriString.endsWith(".pdf", ignoreCase = true) ||
                        context.contentResolver.getType(uri)?.contains("pdf") == true
                if (isPdf) {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val renderer = android.graphics.pdf.PdfRenderer(pfd)
                        totalPages = renderer.pageCount
                        val safePage = currentPage.coerceIn(0, totalPages - 1)
                        val page = renderer.openPage(safePage)
                        val bmp = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        pfd.close()
                        bitmap = bmp
                    } else {
                        errorMessage = "Could not open document"
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Could not load preview: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Original Uploaded Score",
                    fontWeight = FontWeight.Bold,
                    fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                    color = VoxTextPrimary
                )
                Text(
                    text = "Reference document for verification",
                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                    color = VoxTextSubtitle
                )
            }
            if (totalPages > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev Page")
                    }
                    Text("${currentPage + 1} / $totalPages", fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                        enabled = currentPage < totalPages - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Original Score Page",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = Color.Gray)
            } else {
                CircularProgressIndicator(color = VoxPurplePrimary)
            }
        }
    }
}
