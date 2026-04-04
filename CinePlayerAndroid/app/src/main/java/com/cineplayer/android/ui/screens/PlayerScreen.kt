package com.cineplayer.android.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.cineplayer.android.data.AppSettings
import com.cineplayer.android.models.MediaItem
import com.cineplayer.android.models.SubtitleTrack
import com.cineplayer.android.services.MediaLibrary
import com.cineplayer.android.viewmodels.PlayerUiState
import com.cineplayer.android.viewmodels.PlayerViewModel

@Composable
fun PlayerScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = remember { AppSettings.getInstance(context) }
    val mediaLibrary = remember { MediaLibrary.getInstance(context) }
    val item = remember(itemId) { mediaLibrary.item(itemId) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        item?.let { viewModel.loadMedia(it, context) }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.exoPlayer
                    useController = false
                    setOnClickListener { viewModel.showControls() }
                }
            },
            modifier = Modifier.fillMaxSize().clickable { viewModel.showControls() }
        )

        // Subtitle overlay
        if (!uiState.isPipMode && uiState.currentSubtitleText.isNotBlank()) {
            val fontSize = settings.subtitleFontSize
            val color = when (settings.subtitleTextColor) {
                "yellow" -> Color.Yellow
                "gray" -> Color.LightGray
                else -> Color.White
            }
            val bgAlpha = settings.subtitleBackgroundOpacity
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 72.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.currentSubtitleText,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = bgAlpha), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = color,
                    fontSize = androidx.compose.ui.unit.TextUnit(
                        fontSize, androidx.compose.ui.unit.TextUnitType.Sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Player controls overlay
        AnimatedVisibility(
            visible = uiState.showControls && !uiState.isPipMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerControlsOverlay(
                uiState = uiState,
                skipFwd = settings.skipForwardSeconds,
                skipBwd = settings.skipBackwardSeconds,
                onBack = onBack,
                onTogglePlay = viewModel::togglePlayPause,
                onSkipForward = viewModel::skipForward,
                onSkipBackward = viewModel::skipBackward,
                onSeek = viewModel::seekTo,
                onShowSpeed = { showSpeedPicker = true },
                onShowSubtitles = { showSubtitlePicker = true },
                onPip = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity?.enterPictureInPictureMode(
                            PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                        )
                    }
                }
            )
        }

        // Loading / buffering indicator
        if (uiState.isLoading || uiState.isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Error snackbar
        uiState.error?.let { err ->
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                Text(err)
            }
        }

        // Next episode countdown
        if (uiState.showNextEpisodeCountdown) {
            NextEpisodeCountdownCard(
                countdown = uiState.nextEpisodeCountdownSeconds,
                nextItem = viewModel.getNextEpisode(),
                onPlayNow = {
                    viewModel.getNextEpisode()?.let { next ->
                        viewModel.loadMedia(next, context)
                        viewModel.cancelNextEpisodeCountdown()
                    }
                },
                onCancel = viewModel::cancelNextEpisodeCountdown,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }

        // Speed picker dialog
        if (showSpeedPicker) {
            SpeedPickerDialog(
                current = uiState.playbackSpeed,
                onSelect = { viewModel.setPlaybackSpeed(it); showSpeedPicker = false },
                onDismiss = { showSpeedPicker = false }
            )
        }

        // Subtitle picker dialog
        if (showSubtitlePicker) {
            SubtitlePickerDialog(
                tracks = item?.subtitleTracks ?: emptyList(),
                selectedIndex = item?.selectedSubtitleIndex,
                onSelect = { track -> viewModel.setSubtitleTrack(track); showSubtitlePicker = false },
                onDismiss = { showSubtitlePicker = false }
            )
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    skipFwd: Int,
    skipBwd: Int,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSeek: (Long) -> Unit,
    onShowSpeed: () -> Unit,
    onShowSubtitles: () -> Unit,
    onPip: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IconButton(onClick = onPip) {
                    Icon(Icons.Default.PictureInPicture, "PiP", tint = Color.White)
                }
            }
            IconButton(onClick = onShowSubtitles) {
                Icon(Icons.Default.Subtitles, "Subtitles", tint = Color.White)
            }
            IconButton(onClick = onShowSpeed) {
                Icon(Icons.Default.Speed, "Speed", tint = Color.White)
            }
        }

        // Center controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSkipBackward,
                modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Replay, "Back ${skipBwd}s", tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("${skipBwd}s", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(72.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(
                onClick = onSkipForward,
                modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Forward10, "Fwd ${skipFwd}s", tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("${skipFwd}s", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Bottom seek bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val dur = uiState.durationMs.coerceAtLeast(1L)
            val pos = uiState.currentPositionMs
            Slider(
                value = pos.toFloat() / dur.toFloat(),
                onValueChange = { onSeek((it * dur).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(pos), color = Color.White, style = MaterialTheme.typography.labelSmall)
                if (uiState.playbackSpeed != 1f) {
                    Text(
                        "${uiState.playbackSpeed}x",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(formatTime(dur), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun NextEpisodeCountdownCard(
    countdown: Int,
    nextItem: MediaItem?,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(240.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Next Episode in $countdown...", style = MaterialTheme.typography.titleSmall)
            nextItem?.let {
                Text(
                    it.displayTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = onPlayNow, modifier = Modifier.weight(1f)) { Text("Play Now") }
            }
        }
    }
}

@Composable
private fun SpeedPickerDialog(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(speed) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == speed, onClick = { onSelect(speed) })
                        Spacer(Modifier.width(8.dp))
                        Text(if (speed == 1.0f) "Normal (1.0x)" else "${speed}x")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SubtitlePickerDialog(
    tracks: List<SubtitleTrack>,
    selectedIndex: Int?,
    onSelect: (SubtitleTrack?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitles") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedIndex == null, onClick = { onSelect(null) })
                    Spacer(Modifier.width(8.dp))
                    Text("Off")
                }
                tracks.forEachIndexed { index, track ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(track) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedIndex == index, onClick = { onSelect(track) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(track.language)
                            Text(
                                track.source.name.lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000L
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
