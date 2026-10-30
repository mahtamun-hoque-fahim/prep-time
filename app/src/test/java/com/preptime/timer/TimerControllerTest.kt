package com.preptime.timer

import com.preptime.model.Phase
import com.preptime.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerControllerTest {
    private class FakeBell : BellPort {
        val calls = mutableListOf<String>()
        override fun ding() { calls += "ding" }
        override fun startLoop() { calls += "loop" }
        override fun stopLoop() { calls += "stop" }
    }

    private var t = 0L
    private val bell = FakeBell()
    private val ctl = TimerController(Template("x", "t", 2, 10_000, listOf(5_000)), bell) { t }

    @Test fun runsSpeakersInOrder() {
        ctl.start()
        assertEquals(0, ctl.activeIndex)
        t = 5_000; ctl.tick()
        t = 10_000; ctl.tick()
        assertEquals(listOf("ding", "loop"), bell.calls)
        ctl.endTurn()
        assertEquals(listOf("ding", "loop", "stop"), bell.calls)
        assertEquals(1, ctl.activeIndex)
        assertEquals(Phase.READY, ctl.clocks.value[1].phase)
        ctl.endTurn()
        assertEquals(-1, ctl.activeIndex)
    }

    @Test fun endTurnEarly_noBell() {
        ctl.start(); t = 2_000; ctl.endTurn()
        assertTrue(bell.calls.isEmpty())
        assertEquals(1, ctl.activeIndex)
    }

    @Test fun extendInOvertime_stopsLoop() {
        ctl.start(); t = 10_000; ctl.tick(); t = 12_000; ctl.extend(15_000)
        assertEquals(listOf("loop", "stop"), bell.calls.filter { it != "ding" })
        assertEquals(Phase.RUNNING, ctl.clocks.value[0].phase)
    }

    @Test fun togglePause_freezesClock() {
        ctl.start(); t = 1_000; ctl.togglePause(); t = 9_000; ctl.tick()
        assertEquals(9_000, ctl.clocks.value[0].remainingMs(t))
    }

    @Test fun close_stopsLoop() {
        ctl.start(); t = 10_000; ctl.tick(); ctl.close()
        assertEquals("stop", bell.calls.last())
    }
}
