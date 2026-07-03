package com.evsct.app.util

import java.util.Locale
import java.util.concurrent.Executors
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Format's number rendering is pinned to US separators regardless of the
 * device locale: the money strings prepend a literal "$", and
 * locale-default separators used to produce hybrids like "$1.234,56 CAD"
 * on comma-decimal devices. Each check runs on a fresh thread so the
 * ThreadLocal formatter is created while the foreign default locale is in
 * effect — exactly the condition that used to leak the locale in.
 */
class FormatLocaleTest {

    @Test
    fun `money renders US separators under a comma-decimal locale`() {
        assertEquals("\$1,234.56 CAD", underLocale(Locale.GERMANY) { Format.money(1234.56, "CAD") })
    }

    @Test
    fun `rates and units render US separators under a comma-decimal locale`() {
        assertEquals("\$0.385/kWh", underLocale(Locale.GERMANY) { Format.moneyRate(0.385, "kWh") })
        assertEquals("1,234.5 km", underLocale(Locale.GERMANY) { Format.km(1234.5) })
        assertEquals("48.25 kWh", underLocale(Locale.GERMANY) { Format.kwh(48.25) })
    }

    @Test
    fun `duration digits are ASCII under any locale`() {
        assertEquals("1h 05m", underLocale(Locale.GERMANY) { Format.duration(3900) })
    }

    /** Run [block] on a brand-new thread while the JVM default locale is
     *  [locale], restoring the original default afterwards. */
    private fun <T> underLocale(locale: Locale, block: () -> T): T {
        val saved = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            val executor = Executors.newSingleThreadExecutor()
            try {
                return executor.submit(block).get()
            } finally {
                executor.shutdown()
            }
        } finally {
            Locale.setDefault(saved)
        }
    }
}
