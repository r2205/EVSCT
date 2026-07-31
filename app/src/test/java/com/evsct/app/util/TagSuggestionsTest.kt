package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tag-reuse helper behind the session form's suggestion chips. The
 * substance is the ranking: what the user reaches for first with an empty
 * field (the tag from the last few charges) and what a half-typed draft is
 * allowed to find (the same tag written slightly differently, or fat-
 * fingered). Each rule exists to stop a re-used tag from becoming a second,
 * near-identical tag.
 */
class TagSuggestionsTest {

    // --- history ---------------------------------------------------------

    @Test
    fun `history is most recently used first`() {
        val history = TagSuggestions.history(
            listOf(
                session(t = 1, tags = "winter"),
                session(t = 2, tags = "work"),
                session(t = 3, tags = "road trip"),
            ),
        )
        assertEquals(listOf("road trip", "work", "winter"), history)
    }

    @Test
    fun `history dedupes case-insensitively`() {
        val history = TagSuggestions.history(
            listOf(
                session(t = 1, tags = "Work"),
                session(t = 2, tags = "WORK"),
                session(t = 3, tags = "work"),
            ),
        )
        assertEquals(1, history.size)
    }

    @Test
    fun `merged tag is labeled with its most frequent casing`() {
        val history = TagSuggestions.history(
            listOf(
                session(t = 1, tags = "Work"),
                session(t = 2, tags = "Work"),
                // The stray all-caps variant is the most recent, but a
                // one-off typo shouldn't rename the tag everywhere.
                session(t = 3, tags = "WORK"),
            ),
        )
        assertEquals(listOf("Work"), history)
    }

    @Test
    fun `casing tie goes to the most recently used variant`() {
        val history = TagSuggestions.history(
            listOf(
                session(t = 1, tags = "work"),
                session(t = 2, tags = "Work"),
            ),
        )
        assertEquals(listOf("Work"), history)
    }

    @Test
    fun `recency ties are broken by how often the tag was used`() {
        val history = TagSuggestions.history(
            listOf(
                session(t = 1, tags = "common"),
                session(t = 3, tags = "common"),
                session(t = 5, tags = "common,rare"),
            ),
        )
        assertEquals(listOf("common", "rare"), history)
    }

    @Test
    fun `sessions without tags contribute nothing`() {
        val history = TagSuggestions.history(
            listOf(
                session(t = 1, tags = null),
                session(t = 2, tags = ""),
                session(t = 3, tags = "  ,  "),
            ),
        )
        assertTrue(history.isEmpty())
    }

    @Test
    fun `a tag repeated inside one session counts once`() {
        // Tags.parse already dedupes, so the count that breaks recency ties
        // can't be inflated by a single session.
        val history = TagSuggestions.history(
            listOf(
                session(t = 5, tags = "work,Work,WORK"),
                session(t = 5, tags = "commute"),
                session(t = 4, tags = "commute"),
            ),
        )
        assertEquals(listOf("commute", "work"), history)
    }

    // --- match: no draft -------------------------------------------------

    @Test
    fun `a blank draft offers the head of the history`() {
        val history = (1..12).map { "tag$it" }
        assertEquals(history.take(TagSuggestions.MAX_SUGGESTIONS), TagSuggestions.match(history, ""))
        assertEquals(history.take(3), TagSuggestions.match(history, "   ", limit = 3))
    }

    @Test
    fun `tags already on the session are never suggested`() {
        val history = listOf("work", "winter", "road trip")
        assertEquals(
            listOf("winter", "road trip"),
            TagSuggestions.match(history, "", exclude = listOf("WORK")),
        )
        assertTrue(TagSuggestions.match(history, "wor", exclude = listOf(" Work ")).isEmpty())
    }

    // --- match: typing ---------------------------------------------------

    @Test
    fun `prefix matches rank above the middle of a word`() {
        val history = listOf("nightwork", "work charge")
        assertEquals(listOf("work charge", "nightwork"), TagSuggestions.match(history, "wor"))
    }

    @Test
    fun `a later word in the tag is matchable`() {
        // The user remembers "trip", not that the tag starts with "road".
        assertEquals(listOf("road trip"), TagSuggestions.match(listOf("road trip"), "trip"))
    }

    @Test
    fun `whole-tag prefix outranks a later word`() {
        val history = listOf("family trip", "tripcheck")
        assertEquals(listOf("tripcheck", "family trip"), TagSuggestions.match(history, "trip"))
    }

    @Test
    fun `spacing and punctuation don't have to be reproduced`() {
        val history = listOf("road-trip", "kid's hockey")
        assertEquals(listOf("road-trip"), TagSuggestions.match(history, "roadtr"))
        assertEquals(listOf("kid's hockey"), TagSuggestions.match(history, "kids ho"))
    }

    @Test
    fun `case is ignored while typing`() {
        assertEquals(listOf("Work Charge"), TagSuggestions.match(listOf("Work Charge"), "WORK c"))
    }

    @Test
    fun `an exact match is still offered so it can be tapped`() {
        assertEquals(listOf("winter"), TagSuggestions.match(listOf("winter"), "winter"))
    }

    @Test
    fun `a typo still finds the tag`() {
        assertEquals(listOf("winter"), TagSuggestions.match(listOf("winter"), "wnter"))
        assertEquals(listOf("road trip"), TagSuggestions.match(listOf("road trip"), "raodtrip"))
    }

    @Test
    fun `a typo in one word finds a multi-word tag`() {
        assertEquals(listOf("winter test"), TagSuggestions.match(listOf("winter test"), "wnter"))
    }

    @Test
    fun `typo tolerance stays off for very short drafts`() {
        // "wo" is one edit from "no" and half the tag list — matching there
        // would bury the prefix hits the user is aiming at.
        assertTrue(TagSuggestions.match(listOf("no charge"), "wo").isEmpty())
    }

    @Test
    fun `real matches rank above typo matches`() {
        // "winer" is one edit from "winter" but a genuine prefix of
        // "winery stop", which is far likelier to be what was meant.
        val history = listOf("winter", "winery stop")
        assertEquals(listOf("winery stop", "winter"), TagSuggestions.match(history, "winer"))
    }

    @Test
    fun `unrelated tags are not offered`() {
        assertTrue(TagSuggestions.match(listOf("work", "winter"), "hotel").isEmpty())
    }

    @Test
    fun `matches keep history order within a rank and respect the limit`() {
        val history = listOf("work c", "work b", "work a")
        assertEquals(listOf("work c", "work b"), TagSuggestions.match(history, "work", limit = 2))
        assertTrue(TagSuggestions.match(history, "work", limit = 0).isEmpty())
    }

    @Test
    fun `an empty history suggests nothing`() {
        assertTrue(TagSuggestions.match(emptyList(), "").isEmpty())
        assertTrue(TagSuggestions.match(emptyList(), "work").isEmpty())
    }

    // --- canonical -------------------------------------------------------

    @Test
    fun `a typed tag adopts the casing already in use`() {
        assertEquals("Work Charge", TagSuggestions.canonical("work charge", listOf("Work Charge")))
    }

    @Test
    fun `an unknown tag is kept as typed, trimmed`() {
        assertEquals("hotel", TagSuggestions.canonical("  hotel  ", listOf("Work")))
    }

    private fun session(t: Long, tags: String?): ChargingSession =
        ChargingSession(sessionStart = t, tags = tags)
}
