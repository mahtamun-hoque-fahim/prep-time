# Prep Time — Debate Time Keeper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A sideloaded Android app where a debate time keeper picks a saved template (N speakers, duration, alert moments) and runs each speaker's countdown with a loud bell at alert moments and a looping bell at time up.

**Architecture:** Single `app` module. All timing rules live in pure Kotlin (`SpeakerClock`, `TimerController`) driven by an injected `now()` so they are JVM-unit-tested. A `ViewModel` ticks the controller every 100 ms using `SystemClock.elapsedRealtime()` (no drift, survives screen-off), a foreground service with a partial wake lock keeps the process alive while a speaker is live, and Compose screens render state.

**Tech Stack:** Kotlin 2.3.0, AGP 8.13.2, Gradle wrapper 9.1.0, Jetpack Compose (BOM 2025.12.01) + Material 3, Navigation Compose, DataStore Preferences + kotlinx.serialization JSON, JUnit 4. compileSdk/targetSdk 36, minSdk 26.

**Spec:** `docs/superpowers/specs/2026-10-30-debate-timekeeper-design.md`

## Global Constraints

- Package / applicationId: `com.preptime`. App name: "Prep Time".
- Native Android, Kotlin + Jetpack Compose + Material 3. Offline only; no accounts, no network permission.
- Fully rounded everywhere: no sharp corners on any surface, button, field, chip, sheet, dialog or card.
- Default color: blue `0xFF0052FF`. Color theme is chosen from preset swatches only (no free picker).
- Dark/light switch: Light / Dark / Follow system.
- Alert moments are **time elapsed** since the speaker started.
- Warning alert = bell rings once. Time up = bell loops until tapped.
- All sound plays on the alarm stream (`AudioAttributes.USAGE_ALARM` / `AudioManager.STREAM_ALARM`) so it sounds on silent.
- Alarm volume raised to max while the Timer screen is open; previous volume restored on leaving it.
- Overtime counts up while the bell loops, shown as `+HH:MM:SS`.
- Extend presets: +15s, +30s, +45s, +1m, +2m, plus a custom seconds input.
- Extend during overtime: new duration = elapsed at tap + extension; bell stops.
- Screen stays awake while the Timer screen is open.
- Toolchain on this PC: Android SDK at `%LOCALAPPDATA%\Android\Sdk` (platforms 36, build-tools 36.0.0). Run Gradle with `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` (JDK 25; the system Oracle JDK 26 is too new for Gradle).

## Review Focus

1. **Extend while the bell loops** — bell must stop, countdown resumes from the extension, alerts already rung must not ring again. (Task 2 test `extendInOvertime_restartsFromTap`, Task 4 test `extendInOvertime_stopsLoop`.)
2. **Pause across an alert moment** — paused time must not count; the alert rings once when real speaking time crosses it, not at resume. (Task 2 test `pause_doesNotCountAndAlertFiresOnceAfterResume`.)
3. **Late tick** (phone lagged or backgrounded, one tick jumps past several alerts and time up) — one sound only: the loop wins over a ding; no burst of dings. (Task 2 test `lateTick_crossingAlertsAndEnd_reportsOnlyTimeUp`.)
4. **Leaving the timer while live or ringing** — bell must stop, volume restored, service stopped; leaving while live asks for confirmation. (Task 4 test `close_stopsLoop`; Task 7 manual check.)
5. **Bad template input** — alert at 0, at/after the duration, duplicate alerts, zero duration, 0 speakers, blank name must be rejected before save. (Task 3 test `validate_rejectsBadInput`.)

---

### Task 1: Project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties` (+ `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`), `local.properties` (`sdk.dir`), `.gitignore`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/preptime/MainActivity.kt`, `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: buildable `:app`; `MainActivity : ComponentActivity` calling `setContent { }`.

- [ ] **Step 1: Write Gradle files.** Plugins: `com.android.application` 8.13.2, `org.jetbrains.kotlin.android` 2.3.0, `org.jetbrains.kotlin.plugin.compose` 2.3.0, `org.jetbrains.kotlin.plugin.serialization` 2.3.0. Dependencies: compose BOM 2025.12.01 (`ui`, `material3`, `ui-tooling-preview`, debug `ui-tooling`), `androidx.compose.material:material-icons-extended:1.7.8`, `androidx.activity:activity-compose:1.10.1`, `androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1`, `androidx.lifecycle:lifecycle-runtime-compose:2.9.1`, `androidx.navigation:navigation-compose:2.9.0`, `androidx.datastore:datastore-preferences:1.1.7`, `org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1`, test `junit:junit:4.13.2`. If a version fails to resolve, take the nearest newer one and note it in the commit.
- [ ] **Step 2: Create the wrapper (Gradle 9.1.0).** Copy `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` from the user's other project `E:\codes\Heyon` (the wrapper jar is version-independent). Write `gradle-wrapper.properties` with `distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-bin.zip`.
- [ ] **Step 3: Manifest + empty activity.** `MainActivity` shows `Text("Prep Time")`. `.gitignore`: `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `.kotlin/`.
- [ ] **Step 4: Verify.** Run: `./gradlew assembleDebug` → `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 5: Commit** `chore: scaffold Android project`.

### Task 2: Speaker clock (pure logic)

**Files:**
- Create: `app/src/main/java/com/preptime/model/SpeakerClock.kt`
- Test: `app/src/test/java/com/preptime/model/SpeakerClockTest.kt`

**Interfaces:**
- Produces:
  - `enum class Phase { READY, RUNNING, PAUSED, OVERTIME, DONE }`
  - `enum class ClockEvent { ALERT, TIME_UP }`
  - `data class SpeakerClock(val durationMs: Long, val alertsMs: List<Long>, val phase: Phase = Phase.READY, val baseElapsedMs: Long = 0, val runningSince: Long = 0, val nextAlert: Int = 0)` — `alertsMs` is sorted ascending.
  - `fun elapsedMs(now: Long): Long` — `baseElapsedMs` plus `now - runningSince` while RUNNING or OVERTIME.
  - `fun remainingMs(now: Long): Long = durationMs - elapsedMs(now)` (negative = overtime).
  - `fun start(now: Long): SpeakerClock` (READY→RUNNING), `pause(now)` (RUNNING→PAUSED), `resume(now)` (PAUSED→RUNNING), `end(now)` (any→DONE, freezes elapsed), `extend(now: Long, ms: Long)`. Each returns `this` unchanged when called from a phase it doesn't apply to.
  - `fun tick(now: Long): Pair<SpeakerClock, ClockEvent?>` — only acts in RUNNING.
  - `fun formatClock(ms: Long): String` (top-level) — `ms >= 0`: seconds rounded **up**, `"HH:MM:SS"`; `ms < 0`: `"+HH:MM:SS"` of `-ms` rounded down.

- [ ] **Step 1: Write the failing tests.**

```kotlin
private val c = SpeakerClock(durationMs = 10_000, alertsMs = listOf(5_000, 7_000))

@Test fun format() {
    assertEquals("00:10:00", formatClock(600_000)); assertEquals("00:00:01", formatClock(1))
    assertEquals("00:00:00", formatClock(0)); assertEquals("+00:00:12", formatClock(-12_400))
    assertEquals("01:00:00", formatClock(3_600_000))
}
@Test fun alertsFireOnceInOrder() {
    var k = c.start(0)
    k.tick(4_999).also { assertNull(it.second) }
    k = k.tick(5_000).also { assertEquals(ClockEvent.ALERT, it.second) }.first
    k = k.tick(5_100).also { assertNull(it.second) }.first
    k = k.tick(7_000).also { assertEquals(ClockEvent.ALERT, it.second) }.first
    val (end, ev) = k.tick(10_000)
    assertEquals(ClockEvent.TIME_UP, ev); assertEquals(Phase.OVERTIME, end.phase)
    assertEquals(-2_000, end.remainingMs(12_000))
}
@Test fun pause_doesNotCountAndAlertFiresOnceAfterResume() {
    val k = c.start(0).pause(4_000).resume(9_000)          // 5 s paused
    assertEquals(4_000, k.elapsedMs(9_000))
    assertNull(k.tick(9_500).second)                       // 4.5 s spoken
    assertEquals(ClockEvent.ALERT, k.tick(10_000).second)  // 5 s spoken
}
@Test fun lateTick_crossingAlertsAndEnd_reportsOnlyTimeUp() {
    val (k, ev) = c.start(0).tick(11_000)
    assertEquals(ClockEvent.TIME_UP, ev); assertNull(k.tick(12_000).second)
}
@Test fun lateTick_crossingTwoAlerts_reportsOneAlert() {
    val (k, ev) = c.start(0).tick(8_000)
    assertEquals(ClockEvent.ALERT, ev); assertNull(k.tick(8_100).second)
}
@Test fun extendWhileRunning_addsToDuration() {
    assertEquals(25_000, c.start(0).extend(1_000, 15_000).durationMs)
}
@Test fun extendInOvertime_restartsFromTap() {
    val over = c.start(0).tick(10_000).first
    val k = over.extend(13_000, 30_000)                    // 3 s over when extended
    assertEquals(Phase.RUNNING, k.phase); assertEquals(30_000, k.remainingMs(13_000))
    assertNull(k.tick(20_000).second)                      // no old alert re-rings
    assertEquals(ClockEvent.TIME_UP, k.tick(43_000).second)
}
@Test fun endFreezesTime() {
    val k = c.start(0).end(3_000)
    assertEquals(Phase.DONE, k.phase); assertEquals(3_000, k.elapsedMs(99_000))
}
@Test fun invalidTransitionsAreNoOps() {
    assertSame(c, c.pause(0)); assertSame(c, c.tick(0).first)
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "com.preptime.model.SpeakerClockTest"` → FAIL (unresolved references).
- [ ] **Step 3: Implement** `SpeakerClock.kt`. In `tick`: advance `nextAlert` past every alert `<= elapsed`; if `elapsed >= durationMs` return OVERTIME + `TIME_UP` (no ALERT); else return `ALERT` if any alert was passed this tick.
- [ ] **Step 4: Run** the same command → PASS.
- [ ] **Step 5: Commit** `feat: speaker clock state machine`.

### Task 3: Template model, validation, storage

**Files:**
- Create: `app/src/main/java/com/preptime/model/Template.kt`, `app/src/main/java/com/preptime/data/Store.kt`
- Test: `app/src/test/java/com/preptime/model/TemplateTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class Template(val id: String, val name: String, val speakers: Int, val durationMs: Long, val alertsMs: List<Long>)`
  - `fun Template.validate(): String?` — `null` when valid, else a short user-facing message.
  - `fun Template.clock(): SpeakerClock` — `SpeakerClock(durationMs, alertsMs.sorted())`.
  - `enum class ThemeMode { LIGHT, DARK, SYSTEM }`; `@Serializable data class Settings(val swatch: Int = 0, val mode: ThemeMode = ThemeMode.SYSTEM)`
  - `class Store(context: Context)` with `val templates: Flow<List<Template>>`, `suspend fun save(t: Template)` (insert or replace by id), `suspend fun delete(id: String)`, `val settings: Flow<Settings>`, `suspend fun saveSettings(s: Settings)`. Backed by `preferencesDataStore("prep")`, each value stored as one JSON string key. A single `Store` per process (`val Context.store` extension over an application-scoped instance).

- [ ] **Step 1: Write the failing test.**

```kotlin
private val ok = Template("1", "BP", 3, 420_000, listOf(60_000, 360_000))
@Test fun validate_acceptsGood() { assertNull(ok.validate()); assertNull(ok.copy(speakers = 1, alertsMs = emptyList()).validate()) }
@Test fun validate_rejectsBadInput() {
    listOf(
        ok.copy(name = " "), ok.copy(speakers = 0), ok.copy(speakers = 21), ok.copy(durationMs = 0),
        ok.copy(alertsMs = listOf(0)), ok.copy(alertsMs = listOf(420_000)), ok.copy(alertsMs = listOf(500_000)),
        ok.copy(alertsMs = listOf(60_000, 60_000)),
    ).forEach { assertNotNull("should reject $it", it.validate()) }
}
@Test fun clock_sortsAlerts() { assertEquals(listOf(1L, 2L), ok.copy(alertsMs = listOf(2, 1)).clock().alertsMs) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "com.preptime.model.TemplateTest"` → FAIL.
- [ ] **Step 3: Implement** `Template.kt` (speakers 1..20) and `Store.kt`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: templates and settings storage`.

### Task 4: Timer controller (pure logic over all speakers)

**Files:**
- Create: `app/src/main/java/com/preptime/timer/TimerController.kt`
- Test: `app/src/test/java/com/preptime/timer/TimerControllerTest.kt`

**Interfaces:**
- Consumes: `SpeakerClock`, `Phase`, `ClockEvent` (Task 2); `Template.clock()` (Task 3).
- Produces:
  - `interface BellPort { fun ding(); fun startLoop(); fun stopLoop() }`
  - `class TimerController(template: Template, private val bell: BellPort, private val now: () -> Long)`
    - `val clocks: StateFlow<List<SpeakerClock>>` — `template.speakers` copies of `template.clock()`.
    - `val activeIndex: Int` — first clock not DONE, `-1` when all done.
    - `val isLive: Boolean` — active clock is RUNNING, PAUSED or OVERTIME.
    - `fun start()` (active clock READY→RUNNING), `fun togglePause()` (RUNNING↔PAUSED), `fun extend(ms: Long)`, `fun endTurn()` (active→DONE; used by both the right knob and the bell knob), `fun tick()`, `fun close()` (stops loop).
    - Bell rules: `tick` ALERT→`ding()`, TIME_UP→`startLoop()`; `extend` or `endTurn` on an OVERTIME clock→`stopLoop()`.

- [ ] **Step 1: Write the failing tests** with `FakeBell` recording calls into a `MutableList<String>` and `var t = 0L; now = { t }`; template `Template("x","t",2,10_000,listOf(5_000))`.

```kotlin
@Test fun runsSpeakersInOrder() {
    ctl.start(); assertEquals(0, ctl.activeIndex)
    t = 5_000; ctl.tick(); t = 10_000; ctl.tick()
    assertEquals(listOf("ding", "loop"), bell.calls)
    ctl.endTurn(); assertEquals(listOf("ding", "loop", "stop"), bell.calls)
    assertEquals(1, ctl.activeIndex); assertEquals(Phase.READY, ctl.clocks.value[1].phase)
    ctl.endTurn(); assertEquals(-1, ctl.activeIndex)
}
@Test fun endTurnEarly_noBell() { ctl.start(); t = 2_000; ctl.endTurn(); assertTrue(bell.calls.isEmpty()); assertEquals(1, ctl.activeIndex) }
@Test fun extendInOvertime_stopsLoop() {
    ctl.start(); t = 10_000; ctl.tick(); t = 12_000; ctl.extend(15_000)
    assertEquals(listOf("loop", "stop"), bell.calls.filter { it != "ding" })
    assertEquals(Phase.RUNNING, ctl.clocks.value[0].phase)
}
@Test fun togglePause_freezesClock() { ctl.start(); t = 1_000; ctl.togglePause(); t = 9_000; ctl.tick(); assertEquals(9_000, ctl.clocks.value[0].remainingMs(t)) }
@Test fun close_stopsLoop() { ctl.start(); t = 10_000; ctl.tick(); ctl.close(); assertEquals("stop", bell.calls.last()) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "com.preptime.timer.TimerControllerTest"` → FAIL.
- [ ] **Step 3: Implement** `TimerController.kt`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: timer controller`.

### Task 5: Theme, Home screen, settings, navigation

**Files:**
- Create: `app/src/main/java/com/preptime/ui/theme/Theme.kt`, `app/src/main/java/com/preptime/ui/HomeScreen.kt`
- Modify: `app/src/main/java/com/preptime/MainActivity.kt`

**Interfaces:**
- Consumes: `Store`, `Settings`, `ThemeMode`, `Template` (Task 3); `formatClock` (Task 2).
- Produces:
  - `val Swatches: List<Color>` = Blue `0xFF0052FF`, Teal `0xFF00897B`, Green `0xFF2E7D32`, Purple `0xFF6A1B9A`, Orange `0xFFEF6C00`, Pink `0xFFC2185B` (index 0 = default).
  - `@Composable fun PrepTheme(settings: Settings, content: @Composable () -> Unit)` — M3 light/dark scheme with `primary` = swatch (`onPrimary` white); `Shapes(extraSmall/small = RoundedCornerShape(50), medium/large/extraLarge = RoundedCornerShape(28.dp))`.
  - Nav routes in `MainActivity`: `"home"`, `"edit?id={id}"`, `"timer/{id}"`.
  - `@Composable fun HomeScreen(onOpen: (id: String) -> Unit, onEdit: (id: String?) -> Unit)`.

- [ ] **Step 1: Implement theme.** `MainActivity` collects `store.settings` and wraps the NavHost in `PrepTheme`.
- [ ] **Step 2: Implement Home.** Top bar with a settings icon. List of templates as rounded cards: name + summary `"${speakers} × ${formatClock(durationMs)}"`; tap → `onOpen(id)`; trailing edit icon → `onEdit(id)`; delete from a confirm dialog. Empty state text + **+ New** FAB → `onEdit(null)`. Settings opens a rounded dialog: swatch circles (selected one ringed) and a 3-way `SingleChoiceSegmentedButtonRow` Light / Dark / System; changes save immediately.
- [ ] **Step 3: Verify.** `./gradlew assembleDebug` → `BUILD SUCCESSFUL`; install `adb install -r app/build/outputs/apk/debug/app-debug.apk`, switch swatches and modes, confirm the whole UI recolors and corners are rounded.
- [ ] **Step 4: Commit** `feat: theme, home and settings`.

### Task 6: Template editor

**Files:**
- Create: `app/src/main/java/com/preptime/ui/EditScreen.kt`
- Modify: `app/src/main/java/com/preptime/MainActivity.kt` (wire `"edit?id={id}"`)

**Interfaces:**
- Consumes: `Template`, `Template.validate()`, `Store.save` (Task 3).
- Produces: `@Composable fun EditScreen(id: String?, onDone: () -> Unit)`.

- [ ] **Step 1: Implement.** Fields: name; speaker count (number, default 3); duration as three number fields H / M / S; alert list — each row M / S (elapsed) with remove button, plus an **Add alert** button. New template id = `UUID.randomUUID().toString()`. Show `validate()` message under the form; Save disabled while it is non-null. Save → `store.save`, `onDone()`.
- [ ] **Step 2: Verify** on device: create "BP" 3 × 00:07:00 with alerts 1:00 and 6:00; an alert at 7:00 shows an error and blocks Save; edit and delete round-trip.
- [ ] **Step 3: Commit** `feat: template editor`.

### Task 7: Bell, foreground service, timer screen

**Files:**
- Create: `app/src/main/java/com/preptime/audio/Bell.kt`, `app/src/main/java/com/preptime/timer/TimerService.kt`, `app/src/main/java/com/preptime/timer/TimerViewModel.kt`, `app/src/main/java/com/preptime/ui/TimerScreen.kt`
- Modify: `AndroidManifest.xml` (permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`; `<service android:name=".timer.TimerService" android:foregroundServiceType="specialUse">` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = "debate timer"), `MainActivity.kt` (wire `"timer/{id}"`)

**Interfaces:**
- Consumes: `TimerController`, `BellPort` (Task 4); `Store`, `Template` (Task 3); `formatClock`, `Phase` (Task 2).
- Produces:
  - `class Bell(context: Context) : BellPort` + `fun raiseVolume()`, `fun restoreVolume()`. `ding()` = `ToneGenerator(AudioManager.STREAM_ALARM, 100)` short tone; `startLoop()` = `RingtoneManager.getRingtone` of the default `TYPE_ALARM` URI (fallback `TYPE_RINGTONE`), `audioAttributes` `USAGE_ALARM`, `isLooping = true`. Volume: remember `getStreamVolume(STREAM_ALARM)`, set `getStreamMaxVolume`; wrap in try/catch (`SecurityException` under Do Not Disturb). Mark ToneGenerator ding with `// shortcut: synthetic tone, swap for a bundled bell asset if it sounds wrong`.
  - `TimerService` — `startForeground` with a low-importance notification ("Timer running"), holds a `PARTIAL_WAKE_LOCK` until destroyed; `companion fun start(ctx)`, `fun stop(ctx)`.
  - `class TimerViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel` — loads template `id`, builds `TimerController(t, bell, SystemClock::elapsedRealtime)`, exposes `controller` and `now: StateFlow<Long>`; loop `while (isActive) { controller.tick(); now.value = elapsedRealtime(); delay(100) }`; starts/stops `TimerService` when `isLive` changes; `raiseVolume()` on init; `onCleared()` → `controller.close()`, `restoreVolume()`, `TimerService.stop`.
  - `@Composable fun TimerScreen(onBack: () -> Unit)`.

- [ ] **Step 1: Implement Bell and TimerService.**
- [ ] **Step 2: Implement TimerViewModel.**
- [ ] **Step 3: Implement TimerScreen** per the spec's Timer section:
  - Keep screen on (`LocalView.current.keepScreenOn = true` in a `DisposableEffect`).
  - Two round knobs beside the top of the ring: left play/pause (`togglePause`), right end turn (`endTurn`).
  - Canvas ring: grey track, primary arc = `remaining / duration` starting at 12 o'clock, alert dots at `360° × alert / duration` clockwise from top; centre text `formatClock(remainingMs(now))` (shows `+HH:MM:SS` in overtime).
  - One pill per speaker: READY (active) grey with a primary "Start" knob on the left; READY (waiting) grey with user icon; RUNNING/PAUSED primary fill = `elapsed / duration`, user icon, grey alert dots; OVERTIME full primary with a bell knob on the right (tap → `endTurn`); DONE full primary, check icon, "Speaker N".
  - Round **+** button under the live pill opens a `ModalBottomSheet`: chips +15s, +30s, +45s, +1m, +2m and a custom seconds field with Add → `extend(ms)`.
  - Back (top-bar arrow and system back via `BackHandler`) asks for confirmation when `isLive`, then `onBack()`.
- [ ] **Step 4: Verify** `./gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Device run-through** (phone on silent, alarm volume low): 3 × 0:00:20 template with alerts 0:05 and 0:10.
  - Volume jumps to max on entering the timer.
  - Dings at 5 s and 10 s; loop at 20 s with `+00:00:0x` counting.
  - Bell knob stops it and the next pill shows Start.
  - Pause holds time; resume continues; an alert never rings twice.
  - +30s during overtime stops the bell and shows 00:00:30.
  - Screen off for 30 s mid-speech: bell still rings on time.
  - Leaving mid-speech asks to confirm; after leaving, the bell is silent and the alarm volume is back to its old level.
- [ ] **Step 6: Commit** `feat: timer screen with bell and foreground service`.
