package com.example.ytdownloader.ui.screens

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ytdownloader.data.Settings
import com.example.ytdownloader.data.Store

@Composable
fun SettingsScreen() {
    var settings by remember { mutableStateOf(Store.getSettings()) }
    var saved by remember { mutableStateOf(false) }

    val qualityOptions = listOf("best") + Store.ALLOWED_HEIGHTS.map { it.toString() }
    val bitrateOptions = listOf(320, 192, 128)
    val actionOptions = listOf("ask", "video", "mp3")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        DropdownSelector(
            label = "Preferred quality",
            selected = settings.preferredHeight,
            options = qualityOptions,
            optionLabel = { if (it == "best") "Best available (no cap)" else "${it}p" },
            onSelect = { settings = settings.copy(preferredHeight = it); saved = false },
        )

        DropdownSelector(
            label = "Tap behavior",
            selected = settings.defaultAction,
            options = actionOptions,
            optionLabel = { actionLabel(it) },
            onSelect = { settings = settings.copy(defaultAction = it); saved = false },
        )

        DropdownSelector(
            label = "MP3 bitrate",
            selected = settings.mp3BitrateKbps,
            options = bitrateOptions,
            optionLabel = { "$it kbps" },
            onSelect = { settings = settings.copy(mp3BitrateKbps = it); saved = false },
        )

        Text("Storage", style = MaterialTheme.typography.titleSmall)
        Text(
            "Videos: ${settings.downloadDir}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Music: ${settings.mp3Dir}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "(App-specific storage — no permissions needed. MediaStore export is a later phase.)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        ToggleRow(
            title = "Auto-delete after watching",
            subtitle = "Deletes the file and library entry when playback ends.",
            checked = settings.autoDeleteAfterWatch,
            onCheckedChange = { settings = settings.copy(autoDeleteAfterWatch = it); saved = false },
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            title = "Picture-in-picture",
            subtitle = "Float the video while you use other apps.",
            checked = settings.pictureInPicture,
            onCheckedChange = { settings = settings.copy(pictureInPicture = it); saved = false },
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                settings = Store.setSettings(settings)
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
        if (saved) {
            Spacer(Modifier.height(8.dp))
            Text("Settings saved.", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun actionLabel(a: String): String = when (a) {
    "video" -> "Always download video"
    "mp3" -> "Always download MP3"
    else -> "Ask every time"
}

@Composable
private fun <T> DropdownSelector(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(optionLabel(selected)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(optionLabel(o)) },
                    onClick = { onSelect(o); expanded = false },
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
