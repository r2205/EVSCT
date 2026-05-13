package com.evsct.app.util

/**
 * Free-form session tags are stored as a single comma-joined string on
 * [com.evsct.app.data.entity.ChargingSession.tags] so the schema can stay
 * a one-line change. This helper handles parsing back to a list, sanitizing
 * user input (commas would otherwise break round-trip), and case-insensitive
 * dedupe so "Work" and "work" don't end up as two separate tags.
 */
object Tags {
    private const val DELIMITER = ","

    /** Parse the stored string back to an ordered, deduped list. */
    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        return raw.split(DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { seen.add(it.lowercase()) }
    }

    /** Serialize a list back to storage form. Returns null when empty so
     *  the column reverts to NULL instead of an empty string. */
    fun serialize(tags: List<String>): String? {
        val cleaned = tags.map(::sanitize).filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return null
        // Re-dedupe at serialize time too, in case the caller appended
        // duplicates without going through [add].
        val seen = HashSet<String>()
        return cleaned.filter { seen.add(it.lowercase()) }.joinToString(DELIMITER)
    }

    /** Strip the delimiter from a single tag and trim whitespace. Empty
     *  string out means "skip this tag". */
    fun sanitize(tag: String): String =
        tag.replace(DELIMITER, " ").trim()

    /** Append [tag] to [current] honoring case-insensitive dedupe. Returns
     *  the same list when the tag was empty after sanitizing, or already
     *  present. */
    fun add(current: List<String>, tag: String): List<String> {
        val clean = sanitize(tag)
        if (clean.isEmpty()) return current
        if (current.any { it.equals(clean, ignoreCase = true) }) return current
        return current + clean
    }

    /** Remove [tag] case-insensitively. */
    fun remove(current: List<String>, tag: String): List<String> =
        current.filterNot { it.equals(tag, ignoreCase = true) }
}
