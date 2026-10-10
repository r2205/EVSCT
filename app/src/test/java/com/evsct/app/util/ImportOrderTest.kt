package com.evsct.app.util

import org.junit.Test
import kotlin.test.assertEquals

class ImportOrderTest {

    private data class Row(val name: String, val start: Long, val id: Long?)

    private fun order(vararg rows: Row) =
        ImportOrder.chronological(rows.toList(), { it.start }, { it.id }).map { it.name }

    @Test
    fun `a newest-first export comes back oldest first`() {
        assertEquals(
            listOf("a", "b", "c"),
            order(Row("c", 300, 3), Row("b", 200, 2), Row("a", 100, 1)),
        )
    }

    @Test
    fun `same-timestamp rows keep their exported id order`() {
        // The bug: these arrive id 3 then id 2, and inserting them in that
        // order handed the later charge the lower new id.
        assertEquals(
            listOf("first", "second"),
            order(Row("second", 100, 3), Row("first", 100, 2)),
        )
    }

    @Test
    fun `rows without an id keep their file order, after those with one`() {
        assertEquals(
            listOf("with-id", "x", "y"),
            order(Row("x", 100, null), Row("y", 100, null), Row("with-id", 100, 7)),
        )
    }
}
