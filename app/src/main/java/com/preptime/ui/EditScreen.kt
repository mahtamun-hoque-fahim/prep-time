package com.preptime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.preptime.data.store
import com.preptime.model.Template
import com.preptime.model.validate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

private fun toMs(h: String, m: String, s: String) =
    ((h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) * 1000

private class Alert(m: String, s: String) {
    var m by mutableStateOf(m)
    var s by mutableStateOf(s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(id: String?, onDone: () -> Unit) {
    val store = LocalContext.current.store
    val scope = rememberCoroutineScope()
    val newId = remember { UUID.randomUUID().toString() }
    var loaded by remember { mutableStateOf(id == null) }
    var name by remember { mutableStateOf("") }
    var speakers by remember { mutableStateOf("3") }
    var h by remember { mutableStateOf("0") }
    var m by remember { mutableStateOf("10") }
    var s by remember { mutableStateOf("0") }
    val alerts = remember { mutableStateListOf<Alert>() }

    LaunchedEffect(id) {
        val t = id?.let { store.templates.first().find { it.id == id } } ?: return@LaunchedEffect
        val secs = t.durationMs / 1000
        name = t.name; speakers = "${t.speakers}"
        h = "${secs / 3600}"; m = "${secs / 60 % 60}"; s = "${secs % 60}"
        alerts.clear()
        t.alertsMs.sorted().forEach { val a = it / 1000; alerts += Alert("${a / 60}", "${a % 60}") }
        loaded = true
    }

    val draft = Template(
        id = id ?: newId,
        name = name.trim(),
        speakers = speakers.toIntOrNull() ?: 0,
        durationMs = toMs(h, m, s),
        alertsMs = alerts.map { toMs("0", it.m, it.s) },
    )
    val error = draft.validate()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "New template" else "Edit template") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            NumberField(speakers, { speakers = it }, "Speakers", Modifier.fillMaxWidth())

            Text("Speech length", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(h, { h = it }, "Hours", Modifier.weight(1f))
                NumberField(m, { m = it }, "Minutes", Modifier.weight(1f))
                NumberField(s, { s = it }, "Seconds", Modifier.weight(1f))
            }

            Text("Bell at (time elapsed)", style = MaterialTheme.typography.titleSmall)
            alerts.forEachIndexed { i, a ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField(a.m, { a.m = it }, "Minutes", Modifier.weight(1f))
                    NumberField(a.s, { a.s = it }, "Seconds", Modifier.weight(1f))
                    IconButton(onClick = { alerts.removeAt(i) }) { Icon(Icons.Rounded.Close, "Remove alert ${i + 1}") }
                }
            }
            OutlinedButton(onClick = { alerts += Alert("", "0") }) {
                Icon(Icons.Rounded.Add, null)
                Text("Add alert", Modifier.padding(start = 8.dp))
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { scope.launch { store.save(draft); onDone() } },
                enabled = error == null && loaded,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(4)) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
