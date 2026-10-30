package com.preptime.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SpeakerClockTest {
    private val c = SpeakerClock(durationMs = 10_000, alertsMs = listOf(5_000, 7_000))

    @Test fun format() {
        assertEquals("00:10:00", formatClock(600_000))
        assertEquals("00:00:01", formatClock(1))
        assertEquals("00:00:00", formatClock(0))
        assertEquals("+00:00:12", formatClock(-12_400))
        assertEquals("01:00:00", formatClock(3_600_000))
    }

    @Test fun format_usesLatinDigitsInAnyLocale() {
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            assertEquals("00:10:00", formatClock(600_000))
        } finally {
            java.util.Locale.setDefault(old)
        }
    }

    @Test fun alertsFireOnceInOrder() {
        var k = c.start(0)
        assertNull(k.tick(4_999).second)
        k = k.tick(5_000).also { assertEquals(ClockEvent.ALERT, it.second) }.first
        k = k.tick(5_100).also { assertNull(it.second) }.first
        k = k.tick(7_000).also { assertEquals(ClockEvent.ALERT, it.second) }.first
        val (end, ev) = k.tick(10_000)
        assertEquals(ClockEvent.TIME_UP, ev)
        assertEquals(Phase.OVERTIME, end.phase)
        assertEquals(-2_000, end.remainingMs(12_000))
    }

    @Test fun pause_doesNotCountAndAlertFiresOnceAfterResume() {
        val k = c.start(0).pause(4_000).resume(9_000) // 5 s paused
        assertEquals(4_000, k.elapsedMs(9_000))
        assertNull(k.tick(9_500).second) // 4.5 s spoken
        assertEquals(ClockEvent.ALERT, k.tick(10_000).second) // 5 s spoken
    }

    @Test fun lateTick_crossingAlertsAndEnd_reportsOnlyTimeUp() {
        val (k, ev) = c.start(0).tick(11_000)
        assertEquals(ClockEvent.TIME_UP, ev)
        assertNull(k.tick(12_000).second)
    }

    @Test fun lateTick_crossingTwoAlerts_reportsOneAlert() {
        val (k, ev) = c.start(0).tick(8_000)
        assertEquals(ClockEvent.ALERT, ev)
        assertNull(k.tick(8_100).second)
    }

    @Test fun extendWhileRunning_addsToDuration() {
        assertEquals(25_000, c.start(0).extend(1_000, 15_000).durationMs)
    }

    @Test fun extendInOvertime_restartsFromTap() {
        val over = c.start(0).tick(10_000).first
        val k = over.extend(13_000, 30_000) // 3 s over when extended
        assertEquals(Phase.RUNNING, k.phase)
        assertEquals(30_000, k.remainingMs(13_000))
        assertNull(k.tick(20_000).second) // no old alert re-rings
        assertEquals(ClockEvent.TIME_UP, k.tick(43_000).second)
    }

    @Test fun endFreezesTime() {
        val k = c.start(0).end(3_000)
        assertEquals(Phase.DONE, k.phase)
        assertEquals(3_000, k.elapsedMs(99_000))
    }

    @Test fun invalidTransitionsAreNoOps() {
        assertSame(c, c.pause(0))
        assertSame(c, c.tick(0).first)
    }
}
