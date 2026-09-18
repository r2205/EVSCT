package com.evsct.app.util

/**
 * Charging-duration parsing and formatting. Two display forms are used:
 *   - "pretty"  — "1h 25m 0s", shown when the field is not focused.
 *   - "editable" — "1:25:00", or "32:14" under an hour, shown while the
 *                  user is editing so they can retype digits without
 *                  working around the h/m/s letters.
 *
 * Inputs accepted (case-insensitive):
 *   "11"          -> 11 minutes
 *   "32:14"       -> 32m 14s  (two-part is minutes:seconds, the stopwatch
 *                              reading — sub-hour charges are the common
 *                              case; hours take all three parts)
 *   "1:25:00"     -> 1h 25m 0s
 *   "1h 25m 30s"  -> exact
 *   "1h 25m" / "25m" / "30s" -> partial pretty
 */
object DurationFormat {

    /**
     * Seconds for [text], or null when it isn't a duration. Every input
     * form is digits-only, so a non-null result is never negative: the
     * arithmetic is overflow-checked, and a digit run too long for a Long
     * is rejected rather than read as zero — "9223372036854775807h" used
     * to wrap to a negative total that the save path then stored.
     */
    fun parse(text: String): Long? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        return try {
            parseChecked(t)
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun parseChecked(t: String): Long? {
        // Pretty: 1h 25m 30s, or partial like 1h, 25m, 30s, 1h 30s, etc.
        val pretty = Regex("""^\s*(?:(\d+)\s*h)?\s*(?:(\d+)\s*m)?\s*(?:(\d+)\s*s)?\s*$""")
        pretty.matchEntire(t)?.let { match ->
            val (h, m, s) = match.destructured
            if (h.isBlank() && m.isBlank() && s.isBlank()) return@let
            return hms(part(h) ?: return null, part(m) ?: return null, part(s) ?: return null)
        }

        // Colon-separated: h:m:s or m:s. Parts must be pure digits —
        // checking the parsed values for negativity isn't enough because
        // toLongOrNull drops the sign of "-0", letting "-0:11:00" through.
        // (The pretty regex above is digits-only by construction.)
        if (':' in t) {
            val parts = t.split(":").map { it.trim() }
            if (parts.any { p -> p.isEmpty() || !p.all(Char::isDigit) }) return null
            val nums = parts.map { it.toLongOrNull() ?: return null }
            return when (nums.size) {
                3 -> hms(nums[0], nums[1], nums[2])
                2 -> hms(0L, nums[0], nums[1])
                else -> null
            }
        }

        // Bare integer: minutes — digits only, same reasoning.
        if (!t.all(Char::isDigit)) return null
        return t.toLongOrNull()?.let { Math.multiplyExact(it, 60L) }
    }

    /** An absent pretty-form group is zero; a present one that doesn't fit
     *  a Long is a rejection, not a zero. */
    private fun part(group: String): Long? =
        if (group.isBlank()) 0L else group.toLongOrNull()

    private fun hms(h: Long, m: Long, s: Long): Long =
        Math.addExact(
            Math.addExact(Math.multiplyExact(h, 3600L), Math.multiplyExact(m, 60L)),
            s,
        )

    fun pretty(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "${h}h ${m}m ${s}s"
    }

    fun editable(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        // Sub-hour drops the hours part so the text matches how it would
        // be typed (m:ss round-trips through parse).
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
