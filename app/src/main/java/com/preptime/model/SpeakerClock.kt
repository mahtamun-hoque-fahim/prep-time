package com.preptime.model

enum class Phase { READY, RUNNING, PAUSED, OVERTIME, DONE }

enum class ClockEvent { ALERT, TIME_UP }

/** One speaker's countdown. Pure: every call takes the current time, so it is testable and drift-free. */
data class SpeakerClock(
    val durationMs: Long,
    val alertsMs: List<Long>, // elapsed moments, ascending
    val phase: Phase = Phase.READY,
    val baseElapsedMs: Long = 0,
    val runningSince: Long = 0,
    val nextAlert: Int = 0,
) {
    private val ticking get() = phase == Phase.RUNNING || phase == Phase.OVERTIME

    fun elapsedMs(now: Long): Long = baseElapsedMs + if (ticking) now - runningSince else 0

    fun remainingMs(now: Long): Long = durationMs - elapsedMs(now)

    fun start(now: Long) = if (phase == Phase.READY) copy(phase = Phase.RUNNING, runningSince = now) else this

    fun pause(now: Long) =
        if (phase == Phase.RUNNING) copy(phase = Phase.PAUSED, baseElapsedMs = elapsedMs(now)) else this

    fun resume(now: Long) = if (phase == Phase.PAUSED) copy(phase = Phase.RUNNING, runningSince = now) else this

    fun end(now: Long) = if (phase == Phase.DONE) this else copy(phase = Phase.DONE, baseElapsedMs = elapsedMs(now))

    fun extend(now: Long, ms: Long) = when (phase) {
        Phase.RUNNING, Phase.PAUSED -> copy(durationMs = durationMs + ms)
        // Overtime already used is not deducted: the extension starts from the tap.
        Phase.OVERTIME -> copy(durationMs = elapsedMs(now) + ms, phase = Phase.RUNNING)
        else -> this
    }

    /** Advances alerts; a late tick that crosses several moments reports one event, and time up wins. */
    fun tick(now: Long): Pair<SpeakerClock, ClockEvent?> {
        if (phase != Phase.RUNNING) return this to null
        val e = elapsedMs(now)
        var n = nextAlert
        while (n < alertsMs.size && alertsMs[n] <= e) n++
        if (e >= durationMs) return copy(phase = Phase.OVERTIME, nextAlert = n) to ClockEvent.TIME_UP
        return copy(nextAlert = n) to if (n > nextAlert) ClockEvent.ALERT else null
    }
}

/** `HH:MM:SS`, seconds rounded up while counting down; `+HH:MM:SS` for overtime. */
fun formatClock(ms: Long): String {
    val s = if (ms >= 0) (ms + 999) / 1000 else -ms / 1000
    return "%s%02d:%02d:%02d".format(java.util.Locale.ROOT, if (ms < 0) "+" else "", s / 3600, s / 60 % 60, s % 60)
}
