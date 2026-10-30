package com.preptime.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.preptime.model.Phase
import com.preptime.model.SpeakerClock
import com.preptime.model.formatClock
import com.preptime.timer.TimerController
import com.preptime.timer.TimerViewModel
import kotlin.math.cos
import kotlin.math.sin

private val Live = setOf(Phase.RUNNING, Phase.PAUSED, Phase.OVERTIME)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(onBack: () -> Unit, vm: TimerViewModel = viewModel()) {
    val ctl by vm.controller.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val c = ctl

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var confirmLeave by remember { mutableStateOf(false) }
    var showExtend by remember { mutableStateOf(false) }
    val leave = { if (c?.isLive == true) confirmLeave = true else onBack() }
    BackHandler { leave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        if (c == null) return@Scaffold
        val clocks by c.clocks.collectAsStateWithLifecycle()
        val ai = c.activeIndex
        val shown = clocks.getOrNull(ai) ?: clocks.last()

        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Ring(shown, now, Modifier.fillMaxSize().padding(24.dp))
                Text(formatClock(shown.remainingMs(now)), style = MaterialTheme.typography.displaySmall)
                val playing = shown.phase == Phase.RUNNING
                FilledIconButton(
                    onClick = { if (shown.phase == Phase.READY) c.start() else c.togglePause() },
                    enabled = ai >= 0 && shown.phase != Phase.OVERTIME,
                    modifier = Modifier.align(Alignment.TopStart).size(48.dp),
                ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play") }
                FilledTonalIconButton(
                    onClick = c::endTurn,
                    enabled = c.isLive,
                    modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
                ) { Icon(Icons.Rounded.SkipNext, "End speaker's turn") }
            }

            clocks.forEachIndexed { i, k ->
                SpeakerPill(i + 1, k, active = i == ai, now = now, ctl = c)
                if (i == ai && k.phase in Live) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(onClick = { showExtend = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Add, "Extend time")
                        }
                        Text("Extend", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (showExtend) ExtendSheet(onDismiss = { showExtend = false }) { ms -> c.extend(ms); showExtend = false }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the timer?") },
            text = { Text("The current speaker's time will stop.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

/** Primary arc = time left; it shrinks clockwise from the top so its edge meets each alert dot at its moment. */
@Composable
private fun Ring(k: SpeakerClock, now: Long, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val w = 18.dp.toPx()
        val r = (size.minDimension - w) / 2
        val topLeft = Offset(center.x - r, center.y - r)
        val arc = Size(r * 2, r * 2)
        val style = Stroke(w, cap = StrokeCap.Round)
        drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
        if (k.phase == Phase.OVERTIME) {
            drawArc(over, 0f, 360f, false, topLeft, arc, style = Stroke(w))
            return@Canvas
        }
        val done = (k.elapsedMs(now).toFloat() / k.durationMs).coerceIn(0f, 1f)
        if (done < 1f) drawArc(primary, -90f + 360f * done, 360f * (1 - done), false, topLeft, arc, style = style)
        k.alertsMs.forEach { a ->
            val rad = Math.toRadians(-90.0 + 360.0 * a / k.durationMs)
            val p = Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
            drawCircle(surface, w * 0.75f, p)
            drawCircle(primary, w * 0.5f, p)
        }
    }
}

@Composable
private fun SpeakerPill(n: Int, k: SpeakerClock, active: Boolean, now: Long, ctl: TimerController) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val state = when (k.phase) {
        Phase.READY -> if (active) "ready" else "waiting"
        Phase.RUNNING -> "speaking"
        Phase.PAUSED -> "paused"
        Phase.OVERTIME -> "time up"
        Phase.DONE -> "done"
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = true) { contentDescription = "Speaker $n, $state" },
    ) {
        val fill = when (k.phase) {
            Phase.READY -> 0f
            Phase.OVERTIME, Phase.DONE -> 1f
            else -> (k.elapsedMs(now).toFloat() / k.durationMs).coerceIn(0f, 1f)
        }
        if (fill > 0f) {
            // Never thinner than the icon slot, so the user icon always sits on colour.
            Box(Modifier.fillMaxHeight().width(maxOf(maxWidth * fill, 56.dp)).clip(CircleShape).background(primary))
        }
        if (k.phase == Phase.RUNNING || k.phase == Phase.PAUSED) {
            k.alertsMs.forEach { a ->
                val x = maxWidth * (a.toFloat() / k.durationMs) - 10.dp
                Box(Modifier.align(Alignment.CenterStart).offset(x = x).size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
            }
        }
        when (k.phase) {
            Phase.READY -> if (active) {
                Box(
                    Modifier.fillMaxHeight().width(88.dp).clip(CircleShape).background(primary).clickable(onClick = ctl::start),
                    contentAlignment = Alignment.Center,
                ) { Text("Start", color = onPrimary, style = MaterialTheme.typography.labelLarge) }
            } else {
                PillLabel(Icons.Rounded.Person, "Speaker $n", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Phase.RUNNING, Phase.PAUSED -> PillLabel(Icons.Rounded.Person, if (k.phase == Phase.PAUSED) "Paused" else "", onPrimary)
            Phase.OVERTIME -> {
                PillLabel(Icons.Rounded.Person, "Speaker $n · time up", onPrimary)
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(6.dp).size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface).clickable(onClick = ctl::endTurn)
                        .semantics { contentDescription = "Stop bell" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Notifications, null, tint = primary) }
            }
            Phase.DONE -> PillLabel(Icons.Rounded.Check, "Speaker $n", onPrimary)
        }
    }
}

@Composable
private fun PillLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(Modifier.fillMaxHeight().padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExtendSheet(onDismiss: () -> Unit, onExtend: (Long) -> Unit) {
    var custom by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Add time", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "+15s", 30 to "+30s", 45 to "+45s", 60 to "+1m", 120 to "+2m").forEach { (s, label) ->
                    AssistChip(onClick = { onExtend(s * 1000L) }, label = { Text(label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { v -> custom = v.filter(Char::isDigit).take(4) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Seconds") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                val secs = custom.toLongOrNull() ?: 0
                Button(onClick = { onExtend(secs * 1000) }, enabled = secs > 0) { Text("Add") }
            }
        }
    }
}
