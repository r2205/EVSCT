package com.evsct.app.util

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Hammer [Format] from many threads concurrently and check that every output
 * matches what a single-threaded call produces. Would have failed loudly
 * against the prior SimpleDateFormat / shared-DecimalFormat implementation
 * (intermittent ArrayIndexOutOfBoundsException, NumberFormatException, or
 * silently corrupted strings); should pass cleanly now that dates go through
 * DateTimeFormatter and DecimalFormat is per-thread via ThreadLocal.
 */
class FormatThreadSafetyTest {

    @Test
    fun `Format is thread-safe under heavy contention`() {
        // Snapshot expected outputs on the main thread first. Every concurrent
        // call below must produce the same string back; any deviation is
        // corruption.
        val epoch = 1_700_000_000_000L
        val expectedDate = Format.date(epoch)
        val expectedDateTime = Format.dateTime(epoch)
        val expectedTime = Format.time(epoch)
        val expectedMoney = Format.money(245.50, "CAD")
        val expectedRate = Format.rate(0.385, "kWh")
        val expectedMoneyRate = Format.moneyRate(0.385, "kWh")
        val expectedKwh = Format.kwh(82.345)
        val expectedKm = Format.km(1234.5)
        val expectedDistance = Format.distance(1234.5, useMiles = false)

        val threads = (Runtime.getRuntime().availableProcessors() * 4).coerceAtLeast(8)
        val iterations = 5_000
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val mismatches = AtomicInteger()
        val errors = ConcurrentLinkedQueue<Throwable>()

        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            pool.execute {
                try {
                    // Park every worker at the gate, then release them all at
                    // once to maximize the racing window.
                    ready.countDown()
                    go.await()
                    repeat(iterations) {
                        if (Format.date(epoch) != expectedDate) mismatches.incrementAndGet()
                        if (Format.dateTime(epoch) != expectedDateTime) mismatches.incrementAndGet()
                        if (Format.time(epoch) != expectedTime) mismatches.incrementAndGet()
                        if (Format.money(245.50, "CAD") != expectedMoney) mismatches.incrementAndGet()
                        if (Format.rate(0.385, "kWh") != expectedRate) mismatches.incrementAndGet()
                        if (Format.moneyRate(0.385, "kWh") != expectedMoneyRate) mismatches.incrementAndGet()
                        if (Format.kwh(82.345) != expectedKwh) mismatches.incrementAndGet()
                        if (Format.km(1234.5) != expectedKm) mismatches.incrementAndGet()
                        if (Format.distance(1234.5, useMiles = false) != expectedDistance) {
                            mismatches.incrementAndGet()
                        }
                    }
                } catch (t: Throwable) {
                    errors += t
                }
            }
        }
        ready.await()
        go.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "Test timed out")

        assertEquals(emptyList<Throwable>(), errors.toList(), "Format threw under concurrency")
        assertEquals(0, mismatches.get(), "Format produced corrupted output under concurrency")
    }
}
