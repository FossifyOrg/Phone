package org.fossify.phone.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersMatchTest {
    @Test
    fun `same number with and without country code`() {
        assertTrue(phoneNumbersMatch("+351912345678", "912345678"))
        assertTrue(phoneNumbersMatch("00351912345678", "+351912345678"))
    }

    @Test
    fun `different numbers do not match`() {
        assertFalse(phoneNumbersMatch("+351912345678", "+351912345679"))
    }

    @Test
    fun `short numbers never match`() {
        assertFalse(phoneNumbersMatch("112", "112"))
        assertFalse(phoneNumbersMatch("12345678", "912345678"))
    }
}
