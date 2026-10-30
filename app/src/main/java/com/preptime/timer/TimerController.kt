package com.preptime.timer

import com.preptime.model.ClockEvent
import com.preptime.model.Phase
import com.preptime.model.SpeakerClock
import com.preptime.model.Template
import com.preptime.model.clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface BellPort {
    fun ding()
    fun startLoop()
    fun stopLoop()
}

/** Runs a template's speakers in order. Only the first speaker not yet done can be controlled. */
class TimerController(template: Template, private val bell: BellPort, private val now: () -> Long) {
    private val _clocks = MutableStateFlow(List(template.speakers) { template.clock() })
    val clocks: StateFlow<List<SpeakerClock>> = _clocks

    val activeIndex: Int get() = _clocks.value.indexOfFirst { it.phase != Phase.DONE }

    val isLive: Boolean get() = active()?.phase.let { it == Phase.RUNNING || it == Phase.PAUSED || it == Phase.OVERTIME }

    fun start() = update { it.start(now()) }

    fun togglePause() = update { if (it.phase == Phase.PAUSED) it.resume(now()) else it.pause(now()) }

    fun extend(ms: Long) {
        stopLoopIfOvertime()
        update { it.extend(now(), ms) }
    }

    fun endTurn() {
        stopLoopIfOvertime()
        update { it.end(now()) }
    }

    fun tick() {
        val c = active() ?: return
        val (next, event) = c.tick(now())
        if (next !== c) update { next }
        when (event) {
            ClockEvent.ALERT -> bell.ding()
            ClockEvent.TIME_UP -> bell.startLoop()
            null -> {}
        }
    }

    fun close() = bell.stopLoop()

    private fun active() = _clocks.value.getOrNull(activeIndex)

    private fun stopLoopIfOvertime() {
        if (active()?.phase == Phase.OVERTIME) bell.stopLoop()
    }

    private fun update(f: (SpeakerClock) -> SpeakerClock) {
        val i = activeIndex
        if (i < 0) return
        _clocks.value = _clocks.value.toMutableList().also { it[i] = f(it[i]) }
    }
}
