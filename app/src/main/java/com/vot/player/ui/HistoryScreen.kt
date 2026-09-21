package com.vot.player.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vot.player.data.db.WatchHistoryItem
import com.vot.player.ui.theme.AccentRed
import com.vot.player.ui.theme.DarkBackground
import com.vot.player.ui.theme.DarkCard
import com.vot.player.ui.theme.TextPrimary
import com.vot.player.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    historyItems: List<WatchHistoryItem>,
    updateInfo: com.vot.player.data.update.AppUpdateInfo? = null,
    detectedClipboardUrl: String? = null,
    onDismissClipboard: () -> Unit = {},
    onDownloadUpdate: (com.vot.player.data.update.AppUpdateInfo) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onPlayUrl: (url: String, startPositionMs: Long) -> Unit,
    onDeleteItem: (videoId: String) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    var inputUrl by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredHistory = remember(historyItems, searchQuery) {
        if (searchQuery.isBlank()) historyItems
        else historyItems.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    val continueWatching = remember(historyItems) {
        historyItems.filter { it.lastPositionMs > 5_000L && (it.durationMs == 0L || (it.durationMs - it.lastPositionMs) > 10_000L) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "VOT Player",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Voice-Over Translation for Mobile",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = AccentRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "PRO",
                                color = AccentRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    }
                }

                // In-App Update Banner
                if (updateInfo != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3A4B)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Update Available: v${updateInfo.versionName}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = updateInfo.releaseNotes.take(60),
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            Button(
                                onClick = { onDownloadUpdate(updateInfo) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Clipboard Detected Banner
                if (!detectedClipboardUrl.isNullOrEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = AccentRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Link detected in clipboard", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = detectedClipboardUrl.take(45) + if (detectedClipboardUrl.length > 45) "..." else "",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            TextButton(onClick = { onPlayUrl(detectedClipboardUrl, 0L) }) {
                                Text("Play", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            IconButton(onClick = onDismissClipboard) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // URL Input Bar
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = { Text("Paste YouTube, Twitch, TikTok, or MP4 link...", color = TextSecondary, fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkCard,
                        unfocusedContainerColor = DarkCard,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                            if (inputUrl.isNotEmpty()) {
                                IconButton(onClick = { inputUrl = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                if (clipText.startsWith("http://") || clipText.startsWith("https://")) {
                                    inputUrl = clipText
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = AccentRed)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            onPlayUrl(inputUrl.trim(), 0L)
                        }
                    },
                    enabled = inputUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentRed,
                        disabledContainerColor = AccentRed.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Translate & Play", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(28.dp))
            }

            // Continue Watching Section
            if (continueWatching.isNotEmpty()) {
                item {
                    Text(
                        text = "Continue Watching",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 8.dp)
                    ) {
                        items(continueWatching, key = { "continue_${it.videoId}" }) { item ->
                            ContinueWatchingCard(
                                item = item,
                                onClick = { onPlayUrl(item.url, item.lastPositionMs) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // Watch History Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Watch History",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (historyItems.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Clear all", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                if (historyItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search history by title...", color = TextSecondary, fontSize = 13.sp) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkCard,
                            unfocusedContainerColor = DarkCard,
                            focusedBorderColor = AccentRed,
                            unfocusedBorderColor = Color(0x22FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // History List Items
            if (historyItems.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No watch history yet", color = TextSecondary, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Share videos from YouTube or paste a URL above to start listening with AI translation",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            lineHeight = 16.sp
                        )
                    }
                }
            } else if (filteredHistory.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No videos match \"$searchQuery\"", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                items(filteredHistory, key = { "hist_${it.videoId}" }) { item ->
                    HistoryListItem(
                        item = item,
                        onClick = { onPlayUrl(item.url, item.lastPositionMs) },
                        onDelete = { onDeleteItem(item.videoId) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: WatchHistoryItem,
    onClick: () -> Unit
) {
    val progress = if (item.durationMs > 0) {
        (item.lastPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = Modifier
            .width(230.dp)
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = formatMs(item.lastPositionMs),
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                color = AccentRed,
                trackColor = Color(0x33FFFFFF),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to resume",
                    color = AccentRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun HistoryListItem(
    item: WatchHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 58.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.lastPositionMs > 0) {
                    val p = if (item.durationMs > 0) (item.lastPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { p },
                        color = AccentRed,
                        trackColor = Color(0x33FFFFFF),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(item.updatedAt),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
