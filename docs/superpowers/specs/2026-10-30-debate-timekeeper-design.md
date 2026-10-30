# Prep Time — Debate Time Keeper (Android) — Design

Date: 2026-10-30

## Intent

A time keeper's tool for debate sessions. The time keeper sits at the table, starts a countdown when a speaker reaches the dias, and the app rings a bell loud enough for the whole room at chosen moments, then rings continuously when time is up until the time keeper silences it.

The pain it removes: in a team round (typically 3 speakers) there is only a short gap between speakers. Re-entering durations and alert moments for each speaker is too slow. A template sets everything once; the time keeper just presses Start for each speaker.

## Goals

- Countdown from a custom duration (hh:mm:ss).
- Any number of custom alert moments per timer (not fixed to two). Each alert rings a loud, room-audible bell once.
- When time ends, the bell rings in a loop until the time keeper taps the bell knob to stop it. (Deliberate: a speaker who keeps talking keeps the bell ringing, which pressures them to stop or ask the judge for more time.)
- Extend: add time to the current speaker from presets 15s, 30s, 45s, 1m, 2m, plus a custom input (rarely used, kept "just in case").
- Templates: N speakers (user enters N, default 3). All speakers in a template share the same duration and the same alert moments.
- Play/pause of the clock.
- Material UI, fully rounded look everywhere (pills, round knobs, rounded cards/sheets, no sharp corners).
- Color theme chosen from preset swatches; blue is the default.
- Dark / light mode switch.

## Non-goals

- Per-speaker names, per-speaker different durations.
- Debate-format libraries (BP, AP, etc.), scoring, judging, speaker records.
- Accounts, sync, cloud, Play Store release. App is sideloaded APK, offline-only.
- Free color picker (presets only).

## Anti-goals

- A bell that can be missed: it must be audible to the room, including when the phone is on silent/vibrate.
- Setup friction between speakers: moving from one speaker to the next must need nothing more than one tap on Start.
- A clock that drifts or stops when the screen turns off.

## Screens

### 1. Home
- List of saved templates (name + summary, e.g. "Team 7:00 ×3", "Solo 5:00").
- **+ New** button opens the template form.
- Tapping a template opens the Timer screen.
- Entry point to theme settings (color swatch + dark/light). Placement is the builder's call.

### 2. Template form (new / edit)
- Name.
- Speaker count (default 3; 1 is allowed and covers the solo case). No separate Team/Solo choice — Solo was dropped by the user.
- Duration (hh:mm:ss).
- Alert moments: add/remove any number.
- Save → back to Home.

### 3. Timer (from the user's mockups)
Top to bottom:
- No Team/Solo toggle (dropped by the user).
- **Two round knobs flanking the ring**: left = play/pause the current speaker's clock; right = end the current speaker's turn (speaker finished early, or after time up). Ending moves that speaker's pill to Done and the next speaker's pill to Ready.
- Leaving the timer and returning to Home: system back / a top-bar back arrow (builder's call).
- **Circular countdown ring**, `hh:mm:ss` in the center. Blue arc = remaining time, grey track = elapsed. Dots on the ring mark alert moments.
- **Speaker list** (Team mode): one pill per speaker, in order. Each pill has states:
  1. **Ready** — grey pill with a blue "Start" knob at the left. Tap Start to begin that speaker.
  2. **Running** — blue fill grows left to right as time passes; user icon at left; grey dots on the pill mark alert moments. Under the running pill: a round **+** button that opens the Extend sheet.
  3. **Time up** — pill fully blue, a bell knob at the right end; the bell loops. Tap the bell knob to stop the sound.
  4. **Done** — blue pill with a tick icon and "Speaker N".
- A 1-speaker template shows the same screen with a single pill.

### Extend sheet
- Rounded bottom sheet: chips +15s, +30s, +45s, +1m, +2m, and a custom input.
- Adds the chosen time to the current speaker's remaining time.

## Behavior

- Alert moments are measured as **time elapsed** from the speaker's start (user's example: 10 min speech, alerts at 5 and 7 min).
- Warning alerts: bell rings once.
- End: bell loops until the bell knob is tapped.
- Play/pause on the left knob pauses/resumes the current speaker's clock.
- Bell plays on the alarm audio stream so it sounds when the phone is silenced.
- Screen stays awake while the Timer screen is open.
- Clock keeps correct time if the app is backgrounded or the screen turns off, and alerts still fire.
- Templates and theme settings persist on the device.

## Decisions made (stated by the user)

- Platform: native Android. Android Studio is already set up.
- Material UI, blue theme default, preset color swatches, dark/light switch, fully rounded.
- Circular ring countdown with time inside.
- Sound that the whole room hears, not vibration.
- Pill states and knob positions as above.

## Builder's calls (Claude's defaults, not confirmed by the user)

- Kotlin + Jetpack Compose + Material 3; local storage via DataStore or Room.
- Dark/light switch offers Light / Dark / Follow system.
- Exact set of preset color swatches (around 5–6, blue first).
- Bell sound asset(s); a shorter ding for warnings and a longer bell for time up, or one bell for both.
- After the bell is stopped, the pill moves to Done and the next speaker's pill shows Start.
- Whether Extend during the ringing bell also silences it (assumed yes: extending resumes the countdown).
- Location of theme settings on Home.

## Overtime and volume (decided by the user)

- While the end bell loops, the clock counts overtime up (`+0:12`, `+0:25`…) so the judge sees how far over the speaker went. Overtime stops when the bell knob or the right knob is tapped.
- The app raises the alarm stream volume to maximum while the Timer screen is open, and restores the previous volume when leaving it.

## Builder's calls added after review

- Extend during overtime: the extension starts fresh from the moment of the tap (overtime already used is not deducted), and the bell stops.
- Template delete/edit, validation that alert moments fall inside the duration, ring rescaling after Extend, confirm-on-leave while a speaker is running: builder decides.
