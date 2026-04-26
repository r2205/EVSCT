package com.evsct.app.util

/**
 * Charging-duration parsing and formatting. Two display forms are used:
 *   - "pretty"  — "1h 25m 0s", shown when the field is not focused.
 *   - "editable" — "1:25:00", shown while the user is editing so they can
 *                  retype digits without working around the h/m/s letters.
 *
 * Inputs accepted (case-insensitive):
 *   "11"          -> 11 minutes
 *   "1:25"        -> 1h 25m  (two-part is hours:minutes, the natural
 *                              charging-session reading)
 *   "0:11:00"     -> 0h 11m 0s
 *   "1h 25m 30s"  -> exact
 *   "1h 25m" / "25m" / "30s" -> partial pretty
 */
object DurationFormat {

    fun parse(text: String): Long? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null

        // Pretty: 1h 25m 30s, or partial like 1h, 25m, 30s, 1h 30s, etc.
        val pretty = Regex("""^\s*(?:(\d+)\s*h)?\s*(?:(\d+)\s*m)?\s*(?:(\d+)\s*s)?\s*$""")
        pretty.matchEntire(t)?.let { match ->
            val (h, m, s) = match.destructured
            if (h.isBlank() && m.isBlank() && s.isBlank()) return@let
            return (h.toLongOrNull() ?: 0L) * 3600 +
                (m.toLongOrNull() ?: 0L) * 60 +
                (s.toLongOrNull() ?: 0L)
        }

        // Colon-separated: h:m:s, h:m, or m
        if (':' in t) {
            val parts = t.split(":").map { it.trim().toLongOrNull() ?: return null }
            return when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 3600 + parts[1] * 60
                else -> null
            }
        }

        // Bare integer: minutes
        return t.toLongOrNull()?.let { it * 60 }
    }

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
        return "%d:%02d:%02d".format(h, m, s)
    }
}
