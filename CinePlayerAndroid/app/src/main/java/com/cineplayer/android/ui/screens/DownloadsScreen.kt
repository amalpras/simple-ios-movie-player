package com.cineplayer.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cineplayer.android.models.MediaItem
import com.cineplayer.android.models.MediaType
import com.cineplayer.android.viewmodels.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    onItemClick: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val library = remember { com.cineplayer.android.services.MediaLibrary.getInstance(context) }
    val allItems by library.allItems.collectAsStateWithLifecycle()
    val lastError by library.lastError.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    var itemForContextMenu by remember { mutableStateOf<MediaItem?>(null) }
    var itemToDelete by remember { mutableStateOf<MediaItem?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addMedia(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Files") },
                actions = {
                    IconButton(onClick = { filePicker.launch(arrayOf("video/*")) }) {
                        Icon(Icons.Default.Add, "Import files")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Error banner
            if (lastError != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            lastError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { library.clearError() }) {
                            Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (allItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text("No Files Yet", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Import video files to your library",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Button(onClick = { filePicker.launch(arrayOf("video/*")) }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import Files")
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        allItems.sortedByDescending { it.dateAdded },
                        key = { it.id }
                    ) { item ->
                        val playbackState = viewModel.getPlaybackState(item.id)
                        DownloadItemRow(
                            item = item,
                            progress = playbackState?.progressPercentage ?: item.progressPercentage,
                            onClick = { onItemClick(item.id) },
                            onLongClick = { itemForContextMenu = item }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }

        // Context menu (long-press)
        itemForContextMenu?.let { item ->
            ModalBottomSheet(onDismissRequest = { itemForContextMenu = null }) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    Text(
                        item.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    HorizontalDivider()

                    // Mark watched / unwatch
                    if (item.isWatched) {
                        ListItem(
                            headlineContent = { Text("Mark as Unwatched") },
                            leadingContent = { Icon(Icons.Default.RemoveCircleOutline, null) },
                            modifier = Modifier.clickableWithRipple {
                                viewModel.resetProgress(item.id)
                                itemForContextMenu = null
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text("Mark as Watched") },
                            leadingContent = { Icon(Icons.Default.CheckCircleOutline, null) },
                            modifier = Modifier.clickableWithRipple {
                                viewModel.markWatched(item.id)
                                itemForContextMenu = null
                            }
                        )
                    }

                    ListItem(
                        headlineContent = { Text("Refresh Metadata") },
                        leadingContent = { Icon(Icons.Default.Refresh, null) },
                        modifier = Modifier.clickableWithRipple {
                            viewModel.refreshMetadata(item.id)
                            itemForContextMenu = null
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Find Subtitles") },
                        leadingContent = { Icon(Icons.Default.Subtitles, null) },
                        modifier = Modifier.clickableWithRipple {
                            viewModel.refreshSubtitle(item.id)
                            itemForContextMenu = null
                        }
                    )

                    HorizontalDivider()

                    ListItem(
                        headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingContent = {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.clickableWithRipple {
                            itemToDelete = item
                            itemForContextMenu = null
                        }
                    )
                }
            }
        }

        // Delete confirmation
        if (itemToDelete != null) {
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("Delete File?") },
                text = { Text("Remove \"${itemToDelete!!.displayTitle}\" from your library?") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deleteItem(itemToDelete!!.id); itemToDelete = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DownloadItemRow(
    item: MediaItem,
    progress: Double,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val posterUrl = remember(item.posterPath) {
        item.posterPath?.let { "https://image.tmdb.org/t/p/w92/${it.trimStart('/')}" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Poster thumbnail
        Box(
            modifier = Modifier
                .size(56.dp, 80.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        item.title.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
            if (item.isWatched) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Watched",
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.mediaType == MediaType.EPISODE && item.seriesName != null) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        item.seriesName!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.fileSize > 0) {
                    Text(
                        formatFileSize(item.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                if (item.duration > 0) {
                    Text(
                        formatDuration(item.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }

            if (item.subtitleTracks.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.Subtitles,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Text(
                        "${item.subtitleTracks.size} subtitle${if (item.subtitleTracks.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            // Progress bar
            if (progress > 0.01 && progress < 0.99) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.toFloat() },
                    modifier = Modifier.fillMaxWidth(0.6f).height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val gb = bytes.toDouble() / 1_073_741_824.0
    if (gb >= 1) return "%.1f GB".format(gb)
    val mb = bytes.toDouble() / 1_048_576.0
    if (mb >= 1) return "%.0f MB".format(mb)
    return "${bytes / 1024} KB"
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000L
    return if (s >= 3600) "%dh %dm".format(s / 3600, (s % 3600) / 60)
    else "%dm %ds".format(s / 60, s % 60)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.clickableWithRipple(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

// Convenience alias
private fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
