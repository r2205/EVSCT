package com.evsct.app.data.db

import com.evsct.app.data.entity.PaymentMethod
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `payment method round-trips by name`() {
        PaymentMethod.entries.forEach { method ->
            assertEquals(method, converters.stringToPaymentMethod(converters.paymentMethodToString(method)))
        }
    }

    @Test
    fun `an unrecorded payment method stays null`() {
        assertNull(converters.paymentMethodToString(null))
        assertNull(converters.stringToPaymentMethod(null))
    }

    @Test
    fun `an unknown payment method reads as unrecorded instead of throwing`() {
        // A row written by a newer build, read after a downgrade: valueOf
        // would throw on every query that loads the table.
        assertNull(converters.stringToPaymentMethod("CRYPTO"))
    }
}
