package com.cineplayer.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cineplayer.android.models.MediaItem
import com.cineplayer.android.models.TVSeries
import com.cineplayer.android.viewmodels.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: String,
    onBack: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val series = remember(seriesId) { viewModel.getSeries(seriesId) }
    var selectedSeason by remember { mutableIntStateOf(series?.seasons?.keys?.minOrNull() ?: 1) }
    val episodes = remember(seriesId, selectedSeason) { viewModel.getEpisodes(seriesId, selectedSeason) }

    if (series == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Series not found")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(series.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item { SeriesHeroSection(series) }
            item {
                if (series.seasons.keys.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = series.seasons.keys.sorted()
                            .indexOf(selectedSeason).coerceAtLeast(0),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        series.seasons.keys.sorted().forEachIndexed { _, season ->
                            Tab(
                                selected = selectedSeason == season,
                                onClick = { selectedSeason = season },
                                text = { Text("Season $season") }
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Season $selectedSeason · ${episodes.size} episodes",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            items(episodes) { episode ->
                val state = viewModel.getPlaybackState(episode.id)
                EpisodeRow(
                    episode = episode,
                    progress = state?.progressPercentage ?: episode.progressPercentage,
                    onClick = { onEpisodeClick(episode.id) }
                )
            }
        }
    }
}

@Composable
private fun SeriesHeroSection(series: TVSeries) {
    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        val bdUrl = series.backdropPath?.let { "https://image.tmdb.org/t/p/w780/${it.trimStart('/')}" }
        if (bdUrl != null) {
            AsyncImage(
                model = bdUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(series.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                series.firstAirYear?.let {
                    Text("$it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
                series.rating?.let {
                    Text("★ ${"%.1f".format(it)}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
                series.status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
            series.overview?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: MediaItem, progress: Double, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(120.dp, 68.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
            ) {
                val imgUrl = episode.posterPath?.let { "https://image.tmdb.org/t/p/w300/${it.trimStart('/')}" }
                if (imgUrl != null) {
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                if (progress > 0.01) {
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
                if (episode.isWatched) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val epNum = episode.episodeNumber
                val epTitle = episode.episodeTitle
                val label = when {
                    epNum != null && !epTitle.isNullOrBlank() -> "Ep $epNum: $epTitle"
                    epNum != null -> "Episode $epNum"
                    else -> episode.title
                }
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                episode.overview?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
