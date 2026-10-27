package com.cit.kaido.voxsight.ui.screens.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.storage.LocalScoreManager
import com.cit.kaido.voxsight.storage.LocalScoreMetadata
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import com.cit.kaido.voxsight.ui.theme.VoxTextPrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextSubtitle
import com.cit.kaido.voxsight.util.PremiumManager
import com.composables.icons.lucide.Filter
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

data class MusicSheetEntry(
    val id: String,
    val title: String,
    val composer: String,
    val keySignature: String,
    val isBlueBadge: Boolean = false,
    val localMetadata: LocalScoreMetadata
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMusicSheetsScreen(
    onBackClicked: () -> Unit,
    onUpgradeClicked: () -> Unit = {},
    onOpenScore: (MusicXmlScore) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val initialCached = remember { LocalScoreManager.peekCachedScores() }
    var searchQuery by remember { mutableStateOf("") }
    var isLoadingScores by remember { mutableStateOf(initialCached == null) }
    var savedSheets by remember {
        mutableStateOf(initialCached?.let { mapScoresToSheetEntries(it) } ?: emptyList())
    }
    var isPremium by remember { mutableStateOf(PremiumManager.isUserPremium(context)) }

    // Bottom sheet context menu & delete confirmation states
    var selectedSheetForMenu by remember { mutableStateOf<MusicSheetEntry?>(null) }
    var sheetToDelete by remember { mutableStateOf<MusicSheetEntry?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val deepPurple = Color(0xFF38036B)
    val screenBg = Color(0xFFFAF9FE)
    val searchBarBg = Color(0xFFEBEBEF)
    val vaultCardBg = Color(0xFFF3F1F8)
    val vaultTrackBg = Color(0xFFE4E2EC)

    suspend fun reloadScores() {
        val loaded = LocalScoreManager.loadSavedScores(context)
        savedSheets = mapScoresToSheetEntries(loaded)
        isLoadingScores = false
    }

    LaunchedEffect(Unit) {
        reloadScores()
        isPremium = PremiumManager.syncFromSupabase(context)
    }

    // Filter user-uploaded sheets by title or composer
    val filteredSheets = remember(savedSheets, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            savedSheets
        } else {
            savedSheets.filter { sheet ->
                sheet.title.contains(q, ignoreCase = true) ||
                    sheet.composer.contains(q, ignoreCase = true)
            }
        }
    }

    // Group alphabetically by first letter of title
    val groupedSheets = remember(filteredSheets) {
        filteredSheets.groupBy { sheet ->
            val firstChar = sheet.title.trim().firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar.toString() else "#"
        }.toSortedMap()
    }

    val vaultUsed = savedSheets.size
    val vaultMax = if (isPremium) 100 else 20
    val vaultProgress = (vaultUsed.toFloat() / vaultMax.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        // ── Top Search Header ─────────────────────────────────────────
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClicked,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF111827)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Pill Search Bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(searchBarBg)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF4B5563),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search titles or composers",
                                    color = Color(0xFF6B7280),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color(0xFF111827),
                                    fontSize = 15.sp
                                ),
                                cursorBrush = SolidColor(deepPurple),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Filter Icon (UI ready, functionality to be wired later)
                IconButton(
                    onClick = { /* Filter to be implemented later */ },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Filter,
                        contentDescription = "Filter",
                        tint = Color(0xFF111827),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ── Scrollable Content ────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // Vault Banner Card
            item {
                Surface(
                    color = vaultCardBg,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Vault",
                                    tint = deepPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buildAnnotatedString {
                                        append(if (isPremium) "Premium Vault: " else "Free Vault: ")
                                        withStyle(
                                            style = SpanStyle(
                                                color = deepPurple,
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append("$vaultUsed / $vaultMax")
                                        }
                                        append(" Scores")
                                    },
                                    fontSize = 14.sp,
                                    color = Color(0xFF111827)
                                )
                            }

                            if (!isPremium) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onUpgradeClicked() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Upgrade",
                                        color = deepPurple,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Upgrade",
                                        tint = deepPurple,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { vaultProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = deepPurple,
                            trackColor = vaultTrackBg,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            if (groupedSheets.isEmpty() && !isLoadingScores) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No music sheets matching \"$searchQuery\""
                            } else {
                                "No uploaded music sheets yet."
                            },
                            color = Color(0xFF6B7280),
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                groupedSheets.forEach { (letter, sheetsInGroup) ->
                    item(key = "header_$letter") {
                        Text(
                            text = letter,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            color = Color(0xFF111827),
                            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 6.dp)
                        )
                    }

                    items(
                        items = sheetsInGroup,
                        key = { it.id }
                    ) { sheet ->
                        MusicSheetCard(
                            sheet = sheet,
                            onCardClick = {
                                coroutineScope.launch {
                                    val fullScore = LocalScoreManager.loadFullScore(context, sheet.localMetadata)
                                    if (fullScore != null) {
                                        onOpenScore(fullScore)
                                    }
                                }
                            },
                            onMoreClick = {
                                selectedSheetForMenu = sheet
                            }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
        }
    }

    // ── Slide-up Context Menu Bottom Sheet ────────────────────────
    selectedSheetForMenu?.let { activeSheet ->
        val badgeBg = if (activeSheet.isBlueBadge) Color(0xFFDCE4FF) else Color(0xFFEAD9FE)
        val badgeFg = if (activeSheet.isBlueBadge) Color(0xFF3649A8) else Color(0xFF38036B)

        ModalBottomSheet(
            onDismissRequest = { selectedSheetForMenu = null },
            sheetState = bottomSheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                // Sheet Header Row (Thumbnail badge + Title & Composer)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = activeSheet.keySignature,
                                color = badgeFg,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeSheet.title,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF111827),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeSheet.composer,
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color(0xFFE5E7EB)
                )

                // Delete Option Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val target = activeSheet
                            selectedSheetForMenu = null
                            sheetToDelete = target
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Delete",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    }

    // ── Delete Confirmation Dialog (Matching Home Screen) ─────────
    sheetToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { sheetToDelete = null },
            title = {
                Text(
                    text = "Delete Score?",
                    style = MaterialTheme.typography.titleMedium,
                    color = VoxTextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${target.title}'? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoxTextSubtitle
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            LocalScoreManager.deleteScore(context, target.id)
                            reloadScores()
                            Toast.makeText(context, "Deleted ${target.title}", Toast.LENGTH_SHORT).show()
                        }
                        sheetToDelete = null
                    }
                ) {
                    Text("DELETE", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sheetToDelete = null }) {
                    Text("CANCEL", color = VoxTextSubtitle)
                }
            }
        )
    }
}

@Composable
private fun MusicSheetCard(
    sheet: MusicSheetEntry,
    onCardClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val badgeBg = if (sheet.isBlueBadge) Color(0xFFDCE4FF) else Color(0xFFEAD9FE)
    val badgeFg = if (sheet.isBlueBadge) Color(0xFF3649A8) else Color(0xFF38036B)

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCardClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Key & Note Badge
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = badgeFg,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = sheet.keySignature,
                        color = badgeFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title & Composer
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = sheet.title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF111827),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sheet.composer,
                    fontSize = 13.sp,
                    color = Color(0xFF4B5563),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color(0xFF374151),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun mapScoresToSheetEntries(loaded: List<LocalScoreMetadata>): List<MusicSheetEntry> {
    return loaded
        .sortedBy { it.title.lowercase() }
        .mapIndexed { index, meta ->
            MusicSheetEntry(
                id = meta.id,
                title = meta.title,
                composer = meta.composer.ifBlank { "Unknown Composer" },
                keySignature = meta.keySignature?.takeIf { it.isNotBlank() } ?: "C MAJ",
                isBlueBadge = index % 3 == 2,
                localMetadata = meta
            )
        }
}
