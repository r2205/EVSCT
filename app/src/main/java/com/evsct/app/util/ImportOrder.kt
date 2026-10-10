package com.evsct.app.util

/**
 * The order to insert sessions from a backup or CSV in, so their fresh ids
 * keep the order the app already had them in.
 *
 * The app's timeline breaks ties on sessionStart by id: date-only imports
 * stamp every row on a day with the same midnight, and only the id says
 * which of those came first (see EfficiencyAnalysis, OdometerDistance and
 * the continuity DAO queries). A restore or import hands out fresh ids in
 * the order rows are inserted, but exports list sessions newest first, so
 * inserting in file order reversed every tie. Each round trip flipped them
 * again, walking the odometer backwards across the tie and overcounting
 * the distance around it.
 *
 * Inserting oldest first, ties by the id the session had when it was
 * exported, keeps the order. Rows without one (a hand-made CSV with no id
 * column) keep their file order among themselves, after any that have one.
 */
object ImportOrder {

    fun <T> chronological(rows: List<T>, sessionStart: (T) -> Long, sourceId: (T) -> Long?): List<T> =
        rows.withIndex()
            .sortedWith(
                compareBy<IndexedValue<T>>(
                    { sessionStart(it.value) },
                    { sourceId(it.value) == null },
                    { sourceId(it.value) },
                    { it.index },
                ),
            )
            .map { it.value }
}
