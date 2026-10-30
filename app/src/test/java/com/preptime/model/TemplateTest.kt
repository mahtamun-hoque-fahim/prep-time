package com.preptime.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateTest {
    private val ok = Template("1", "BP", 3, 420_000, listOf(60_000, 360_000))

    @Test fun validate_acceptsGood() {
        assertNull(ok.validate())
        assertNull(ok.copy(speakers = 1, alertsMs = emptyList()).validate())
    }

    @Test fun validate_rejectsBadInput() {
        listOf(
            ok.copy(name = " "), ok.copy(speakers = 0), ok.copy(speakers = 21), ok.copy(durationMs = 0),
            ok.copy(alertsMs = listOf(0)), ok.copy(alertsMs = listOf(420_000)), ok.copy(alertsMs = listOf(500_000)),
            ok.copy(alertsMs = listOf(60_000, 60_000)),
        ).forEach { assertNotNull("should reject $it", it.validate()) }
    }

    @Test fun clock_sortsAlerts() {
        assertEquals(listOf(1L, 2L), ok.copy(alertsMs = listOf(2, 1)).clock().alertsMs)
    }
}
