package com.preptime.model

import kotlinx.serialization.Serializable

/** Every speaker in a template shares the same duration and alert moments (elapsed). */
@Serializable
data class Template(
    val id: String,
    val name: String,
    val speakers: Int,
    val durationMs: Long,
    val alertsMs: List<Long>,
)

/** `null` when the template can be saved, otherwise a short message for the user. */
fun Template.validate(): String? = when {
    name.isBlank() -> "Enter a name"
    speakers !in 1..20 -> "Speakers must be 1 to 20"
    durationMs <= 0 -> "Duration must be more than zero"
    alertsMs.any { it <= 0 || it >= durationMs } -> "Alerts must fall inside the duration"
    alertsMs.toSet().size != alertsMs.size -> "Alerts must be different"
    else -> null
}

fun Template.clock() = SpeakerClock(durationMs, alertsMs.sorted())
