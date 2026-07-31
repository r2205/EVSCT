package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import kotlin.math.abs

/**
 * Tag reuse for the session form: the ranked history of every tag the user
 * has ever typed, plus the matching that turns a half-typed draft into
 * tappable chips.
 *
 * Tags are free text, so the only thing stopping "road trip", "roadtrip"
 * and "Road Trip" from becoming three separate labels is how easy it is to
 * pick the existing one. Matching therefore works on a normalized form
 * (lowercase, punctuation and spaces stripped) and tolerates a typo, so a
 * partially typed tag still finds what the user already has.
 */
object TagSuggestions {

    /** How many chips the form offers at once — a single scrollable row. */
    const val MAX_SUGGESTIONS = 8

    /** Typo tolerance only kicks in from this length. On a 2–3 character
     *  draft an edit distance of 1 matches almost every tag, which buries
     *  the genuine prefix matches the user is actually aiming for. */
    private const val MIN_FUZZY_LENGTH = 4

    /**
     * Every distinct tag in [sessions], most recently used first (ties
     * broken by how often it was used, then A–Z). Recency leads because the
     * blank-draft chips answer "what did I tag the last few charges with" —
     * on a road trip that is exactly the tag wanted again.
     *
     * Grouping is case-insensitive, and the surviving label is the casing
     * used most often — ties going to the most recent — matching how
     * [BrandSpend] labels merged brands.
     */
    fun history(sessions: List<ChargingSession>): List<String> =
        sessions.asSequence()
            .flatMap { s -> Tags.parse(s.tags).asSequence().map { Use(it, s.sessionStart) } }
            .groupBy { it.label.lowercase() }
            .map { (_, uses) ->
                Ranked(
                    label = displayLabel(uses),
                    count = uses.size,
                    lastUsedAt = uses.maxOf { it.at },
                )
            }
            .sortedWith(
                compareByDescending<Ranked> { it.lastUsedAt }
                    .thenByDescending { it.count }
                    .thenBy { it.label.lowercase() },
            )
            .map { it.label }

    /**
     * The tags from [history] worth offering for the in-progress [draft],
     * best match first and capped at [limit]. Tags in [exclude] — the ones
     * already on this session — are dropped: re-adding them is a no-op.
     *
     * A blank draft returns the head of the history unchanged, so the chips
     * are useful before a single key is pressed.
     */
    fun match(
        history: List<String>,
        draft: String,
        exclude: List<String> = emptyList(),
        limit: Int = MAX_SUGGESTIONS,
    ): List<String> {
        if (limit <= 0) return emptyList()
        val taken = exclude.mapTo(HashSet()) { it.trim().lowercase() }
        val available = history.filter { it.trim().lowercase() !in taken }
        val query = normalize(draft)
        if (query.isEmpty()) return available.take(limit)
        return available
            .mapNotNull { tag -> tier(tag, query)?.let { tag to it } }
            // sortedBy is stable, so history order (recency) survives as the
            // tie-break inside each match tier — no second sort needed.
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * [tag] re-cased to however the user already writes it, when [history]
     * knows it. Committing "work" when every past session says "Work" would
     * otherwise leave the log carrying both spellings — and since the whole
     * app treats tags case-insensitively (dedupe, filtering, these
     * suggestions), the second spelling is noise nobody asked for.
     */
    fun canonical(tag: String, history: List<String>): String {
        val clean = tag.trim()
        return history.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean
    }

    /** How well [tag] answers [query] — lower is better, null is no match. */
    private fun tier(tag: String, query: String): Int? {
        val normalized = normalize(tag)
        // "roa" → "road trip". Checked before the tag is split into words,
        // which is wasted work on the tier most drafts land in.
        if (normalized.startsWith(query)) return PREFIX
        val words = tokens(tag)
        return when {
            // "trip" → "road trip": the user reached for the word they
            // remember, which isn't always the first one.
            words.any { it.startsWith(query) } -> WORD_PREFIX
            // "adtr" → "road trip"
            normalized.contains(query) -> CONTAINS
            // "raod trip" → "road trip"
            query.length >= MIN_FUZZY_LENGTH && isTypoOf(normalized, words, query) -> TYPO
            else -> null
        }
    }

    /** True when [query] is [tolerance] edits away from the whole tag or
     *  from any single word in it — "wnter" should still find "winter
     *  test", whose full normalized form is nowhere near it. */
    private fun isTypoOf(normalized: String, tokens: List<String>, query: String): Boolean {
        val tolerance = if (query.length <= 5) 1 else 2
        if (withinEditDistance(normalized, query, tolerance)) return true
        return tokens.size > 1 && tokens.any { withinEditDistance(it, query, tolerance) }
    }

    /** Levenshtein distance, bailing out as soon as every alignment is
     *  already past [max] — cheaper than computing a distance that only
     *  gets compared to a small threshold. */
    private fun withinEditDistance(a: String, b: String, max: Int): Boolean {
        if (abs(a.length - b.length) > max) return false
        if (a == b) return true
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var best = cur[0]
            for (j in 1..b.length) {
                val substitute = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, substitute)
                best = minOf(best, cur[j])
            }
            if (best > max) return false
            val swap = prev
            prev = cur
            cur = swap
        }
        return prev[b.length] <= max
    }

    /** Comparison form: case, spaces and punctuation dropped, so
     *  "Road-Trip" and "road trip" are the same thing to match against. */
    private fun normalize(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    /** The tag's individual words in comparison form. */
    private fun tokens(s: String): List<String> =
        s.lowercase().split(WORD_BREAK).filter { it.isNotEmpty() }

    /** Label for a case-insensitively merged tag: its most frequent
     *  original casing, ties broken by which variant was used most
     *  recently. Mirrors [BrandSpend]'s rule for merged brand names. */
    private fun displayLabel(uses: List<Use>): String =
        uses.groupBy { it.label }
            .entries
            .maxWith(
                compareBy(
                    { it.value.size },
                    { it.value.maxOf { use -> use.at } },
                ),
            )
            .key

    /** Match tiers, best first. */
    private const val PREFIX = 0
    private const val WORD_PREFIX = 1
    private const val CONTAINS = 2
    private const val TYPO = 3

    private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")

    private data class Use(val label: String, val at: Long)

    private data class Ranked(val label: String, val count: Int, val lastUsedAt: Long)
}
