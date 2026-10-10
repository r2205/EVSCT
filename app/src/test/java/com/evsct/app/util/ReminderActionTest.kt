package com.evsct.app.util

import org.junit.Test
import kotlin.test.assertEquals

class ReminderActionTest {

    private val hour = 60L * 60 * 1000
    private val now = 1_000L * hour

    @Test
    fun `a reminder already in the shade is refreshed without sound`() {
        // The bug: every app foregrounding re-posted it, sounding each time.
        assertEquals(ReminderAction.REFRESH_SILENTLY, reminderAction(isShowing = true, lastPostedAt = now - hour, now = now))
    }

    @Test
    fun `a reminder dismissed today stays dismissed`() {
        assertEquals(ReminderAction.LEAVE_DISMISSED, reminderAction(isShowing = false, lastPostedAt = now - 2 * hour, now = now))
    }

    @Test
    fun `the next day's nag posts again`() {
        assertEquals(ReminderAction.POST, reminderAction(isShowing = false, lastPostedAt = now - 24 * hour, now = now))
        assertEquals(ReminderAction.POST, reminderAction(isShowing = false, lastPostedAt = now - 20 * hour, now = now))
    }

    @Test
    fun `a first reminder posts`() {
        assertEquals(ReminderAction.POST, reminderAction(isShowing = false, lastPostedAt = null, now = now))
    }

    @Test
    fun `a last-posted time in the future after a clock change does not block it`() {
        assertEquals(ReminderAction.POST, reminderAction(isShowing = false, lastPostedAt = now + hour, now = now))
    }
}
