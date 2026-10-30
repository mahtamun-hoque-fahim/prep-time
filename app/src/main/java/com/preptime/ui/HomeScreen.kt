package com.preptime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.preptime.data.Settings
import com.preptime.data.ThemeMode
import com.preptime.data.store
import com.preptime.model.Template
import com.preptime.model.formatClock
import com.preptime.ui.theme.Swatches
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (id: String) -> Unit, onEdit: (id: String?) -> Unit) {
    val store = LocalContext.current.store
    val templates by store.templates.collectAsStateWithLifecycle(emptyList())
    val settings by store.settings.collectAsStateWithLifecycle(Settings())
    var showSettings by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Template?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prep Time") },
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Palette, "Theme") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New") },
            )
        },
    ) { pad ->
        if (templates.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No templates yet. Tap New to make one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(templates, key = { it.id }) { t ->
                    Card(onClick = { onOpen(t.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleMedium)
                                Text("${t.speakers} × ${formatClock(t.durationMs)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { onEdit(t.id) }) { Icon(Icons.Rounded.Edit, "Edit ${t.name}") }
                            IconButton(onClick = { deleting = t }) { Icon(Icons.Rounded.Delete, "Delete ${t.name}") }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${t.name}?") },
            confirmButton = {
                TextButton(onClick = { scope.launch { store.delete(t.id) }; deleting = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    if (showSettings) {
        val save = { s: Settings -> scope.launch { store.saveSettings(s) }; Unit }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Swatches.forEachIndexed { i, c ->
                            val selected = i == settings.swatch
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .padding(if (selected) 5.dp else 0.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { save(settings.copy(swatch = i)) }
                                    .semantics { contentDescription = "Color ${i + 1}" },
                            )
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "System")
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = settings.mode == mode,
                                onClick = { save(settings.copy(mode = mode)) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(label) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } },
        )
    }
}
