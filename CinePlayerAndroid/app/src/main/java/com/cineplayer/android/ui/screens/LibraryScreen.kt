package com.cineplayer.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cineplayer.android.data.LibraryViewStyle
import com.cineplayer.android.data.SortOrder
import com.cineplayer.android.models.MediaItem
import com.cineplayer.android.models.TVSeries
import com.cineplayer.android.ui.components.*
import com.cineplayer.android.viewmodels.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onMovieClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val movies by viewModel.filteredMovies.collectAsStateWithLifecycle()
    val series by viewModel.filteredSeries.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val viewStyle by viewModel.viewStyle.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addMedia(uris)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("CinePlayer") },
                    actions = {
                        IconButton(onClick = {
                            viewModel.setViewStyle(
                                if (viewStyle == LibraryViewStyle.GRID) LibraryViewStyle.LIST
                                else LibraryViewStyle.GRID
                            )
                        }) {
                            Icon(
                                if (viewStyle == LibraryViewStyle.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle view"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, "Sort")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                SortOrder.entries.forEach { o ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (o) {
                                                    SortOrder.RECENTLY_ADDED -> "Recently Added"
                                                    SortOrder.TITLE -> "Title"
                                                    SortOrder.RECENTLY_WATCHED -> "Recently Watched"
                                                }
                                            )
                                        },
                                        onClick = { viewModel.setSortOrder(o); showSortMenu = false },
                                        leadingIcon = {
                                            if (sortOrder == o) Icon(Icons.Default.Check, null)
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { filePicker.launch(arrayOf("video/*")) }) {
                            Icon(Icons.Default.Add, "Add videos")
                        }
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    },
                    singleLine = true
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Movies (${movies.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Series (${series.size})") }
                    )
                }
            }
        },
        floatingActionButton = {
            if (isScanning) {
                FloatingActionButton(onClick = {}) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> MovieListTab(movies, continueWatching, viewStyle, padding, onMovieClick, viewModel)
            1 -> SeriesListTab(series, viewStyle, padding, onSeriesClick, viewModel)
        }
    }
}

@Composable
private fun MovieListTab(
    movies: List<MediaItem>,
    continueWatching: List<MediaItem>,
    viewStyle: LibraryViewStyle,
    padding: PaddingValues,
    onMovieClick: (String) -> Unit,
    viewModel: LibraryViewModel
) {
    if (viewStyle == LibraryViewStyle.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(
                start = 8.dp, end = 8.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (continueWatching.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            "Continue Watching",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            items(continueWatching) { item ->
                                val state = viewModel.getPlaybackState(item.id)
                                ContinueWatchingCard(
                                    item,
                                    state?.progressPercentage ?: item.progressPercentage
                                ) { onMovieClick(item.id) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Movies (${movies.size})",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(movies) { item ->
                val state = viewModel.getPlaybackState(item.id)
                MovieCard(
                    item = item,
                    progress = state?.progressPercentage ?: item.progressPercentage,
                    onClick = { onMovieClick(item.id) }
                )
            }
            if (movies.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("No movies yet. Tap + to add videos.")
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(
                start = 8.dp, end = 8.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(movies) { item ->
                val state = viewModel.getPlaybackState(item.id)
                MovieListItem(item, state?.progressPercentage ?: 0.0) { onMovieClick(item.id) }
            }
            if (movies.isEmpty()) {
                item { EmptyState("No movies yet.") }
            }
        }
    }
}

@Composable
private fun SeriesListTab(
    series: List<TVSeries>,
    viewStyle: LibraryViewStyle,
    padding: PaddingValues,
    onSeriesClick: (String) -> Unit,
    viewModel: LibraryViewModel
) {
    if (viewStyle == LibraryViewStyle.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(
                start = 8.dp, end = 8.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(series) { s ->
                SeriesCard(s, onClick = { onSeriesClick(s.id) })
            }
            if (series.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("No series yet. Add TV show episodes.")
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(
                start = 8.dp, end = 8.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(series) { s ->
                SeriesListItem(s) { onSeriesClick(s.id) }
            }
            if (series.isEmpty()) {
                item { EmptyState("No series yet.") }
            }
        }
    }
}

@Composable
fun MovieListItem(item: MediaItem, progress: Double, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.small
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val imageUrl = item.posterPath?.let { "https://image.tmdb.org/t/p/w92/${it.trimStart('/')}" }
            Box(
                modifier = Modifier
                    .size(48.dp, 72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
            ) {
                if (imageUrl != null) {
                    coil.compose.AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.releaseYear?.let {
                    Text(
                        "$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (progress > 0.01) {
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesListItem(series: TVSeries, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.small
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val imageUrl = series.posterPath?.let { "https://image.tmdb.org/t/p/w92/${it.trimStart('/')}" }
            Box(
                modifier = Modifier
                    .size(48.dp, 72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
            ) {
                if (imageUrl != null) {
                    coil.compose.AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    series.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${series.seasonCount} Season${if (series.seasonCount != 1) "s" else ""} · ${series.totalEpisodeCount} Episodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
