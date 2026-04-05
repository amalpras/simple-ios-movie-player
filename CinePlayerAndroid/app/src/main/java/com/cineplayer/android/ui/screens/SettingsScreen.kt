package com.cineplayer.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cineplayer.android.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val s = viewModel.settings
    var tmdbKey by remember { mutableStateOf(s.tmdbApiKey) }
    var osKey by remember { mutableStateOf(s.openSubtitlesApiKey) }
    var osUser by remember { mutableStateOf(s.openSubtitlesUsername) }
    var osPass by remember { mutableStateOf(s.openSubtitlesPassword) }
    var showTmdbKey by remember { mutableStateOf(false) }
    var showOsKey by remember { mutableStateOf(false) }
    var showOsPass by remember { mutableStateOf(false) }
    var subtitleLang by remember { mutableStateOf(s.preferredSubtitleLanguage) }
    var subtitleFontSize by remember { mutableFloatStateOf(s.subtitleFontSize) }
    var subtitleColor by remember { mutableStateOf(s.subtitleTextColor) }
    var subtitleBgOpacity by remember { mutableFloatStateOf(s.subtitleBackgroundOpacity) }
    var defaultSpeed by remember { mutableFloatStateOf(s.defaultPlaybackSpeed) }
    var skipFwd by remember { mutableIntStateOf(s.skipForwardSeconds) }
    var skipBwd by remember { mutableIntStateOf(s.skipBackwardSeconds) }
    var autoPlay by remember { mutableStateOf(s.autoPlayNextEpisode) }
    var autoFetchSubs by remember { mutableStateOf(s.autoFetchSubtitles) }
    var autoFetchMeta by remember { mutableStateOf(s.autoFetchMetadata) }
    var resume by remember { mutableStateOf(s.resumePlayback) }
    var countdown by remember { mutableIntStateOf(s.countdownToNextEpisode) }

    val subtitleLanguages = listOf(
        "af","ar","az","be","bg","bn","bs","ca","cs","da","de","el","en","eo","es","et","eu","fa","fi","fr",
        "gl","he","hi","hr","hu","hy","id","is","it","ja","ka","kk","ko","lt","lv","mk","ml","mn","mr","ms",
        "mt","nb","nl","pl","pt","ro","ru","sk","sl","sq","sr","sv","sw","ta","te","th","tl","tr","uk","ur",
        "uz","vi","zh"
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SettingsSectionHeader("API Keys") }
            item {
                ApiKeyField("TMDB API Key", tmdbKey, showTmdbKey,
                    onValueChange = { tmdbKey = it; s.tmdbApiKey = it },
                    onToggleVisibility = { showTmdbKey = !showTmdbKey }
                )
            }
            item {
                ApiKeyField("OpenSubtitles API Key", osKey, showOsKey,
                    onValueChange = { osKey = it; s.openSubtitlesApiKey = it },
                    onToggleVisibility = { showOsKey = !showOsKey }
                )
            }
            item {
                OutlinedTextField(
                    value = osUser,
                    onValueChange = { osUser = it; s.openSubtitlesUsername = it },
                    label = { Text("OpenSubtitles Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = osPass,
                    onValueChange = { osPass = it; s.openSubtitlesPassword = it },
                    label = { Text("OpenSubtitles Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showOsPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showOsPass = !showOsPass }) {
                            Icon(if (showOsPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item { SettingsSectionHeader("Subtitle Settings") }
            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = subtitleLang.uppercase(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subtitle Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        subtitleLanguages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.uppercase()) },
                                onClick = { subtitleLang = lang; s.preferredSubtitleLanguage = lang; expanded = false }
                            )
                        }
                    }
                }
            }
            item {
                LabeledSlider("Font Size: ${subtitleFontSize.toInt()}sp", subtitleFontSize, 12f, 32f) {
                    subtitleFontSize = it; s.subtitleFontSize = it
                }
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = subtitleColor.replaceFirstChar { it.uppercaseChar() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subtitle Color") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("white", "yellow", "gray").forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.replaceFirstChar { it.uppercaseChar() }) },
                                onClick = { subtitleColor = c; s.subtitleTextColor = c; expanded = false }
                            )
                        }
                    }
                }
            }
            item {
                LabeledSlider(
                    "Background Opacity: ${"%.0f".format(subtitleBgOpacity * 100)}%",
                    subtitleBgOpacity, 0f, 1f
                ) { subtitleBgOpacity = it; s.subtitleBackgroundOpacity = it }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item { SettingsSectionHeader("Playback Settings") }
            item {
                var expanded by remember { mutableStateOf(false) }
                val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = if (defaultSpeed == 1.0f) "Normal (1.0x)" else "${defaultSpeed}x",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Default Speed") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        speeds.forEach { sp ->
                            DropdownMenuItem(
                                text = { Text(if (sp == 1.0f) "Normal (1.0x)" else "${sp}x") },
                                onClick = { defaultSpeed = sp; s.defaultPlaybackSpeed = sp; expanded = false }
                            )
                        }
                    }
                }
            }
            item { LabeledIntSlider("Skip Forward: ${skipFwd}s", skipFwd, 5, 120) { skipFwd = it; s.skipForwardSeconds = it } }
            item { LabeledIntSlider("Skip Backward: ${skipBwd}s", skipBwd, 5, 120) { skipBwd = it; s.skipBackwardSeconds = it } }
            item { SwitchRow("Auto-Play Next Episode", autoPlay) { autoPlay = it; s.autoPlayNextEpisode = it } }
            item { SwitchRow("Auto-Fetch Subtitles", autoFetchSubs) { autoFetchSubs = it; s.autoFetchSubtitles = it } }
            item { SwitchRow("Auto-Fetch Metadata", autoFetchMeta) { autoFetchMeta = it; s.autoFetchMetadata = it } }
            item { SwitchRow("Resume Playback", resume) { resume = it; s.resumePlayback = it } }
            item { LabeledIntSlider("Next Episode Countdown: ${countdown}s", countdown, 3, 15) { countdown = it; s.countdownToNextEpisode = it } }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    showKey: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
            }
        }
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = min..max, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LabeledIntSlider(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = max - min - 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
