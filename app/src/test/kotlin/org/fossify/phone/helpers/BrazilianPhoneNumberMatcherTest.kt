package org.fossify.phone.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrazilianPhoneNumberMatcherTest {

    @Test
    fun `matches when comparing full E164 number to bare DDD plus number`() {
        assertTrue(BrazilianPhoneNumberMatcher.matches("+5511912345678", "11912345678"))
    }

    @Test
    fun `matches when carrier long-distance operator code is inserted before the DDD`() {
        // e.g. 015 selects the Vivo carrier for a long-distance call
        assertTrue(BrazilianPhoneNumberMatcher.matches("+5511912345678", "01511912345678"))
    }

    @Test
    fun `matches when caller id omits both country code and operator code`() {
        assertTrue(BrazilianPhoneNumberMatcher.matches("+5511912345678", "11912345678"))
    }

    @Test
    fun `matches when only the subscriber number is available, without DDD`() {
        assertTrue(BrazilianPhoneNumberMatcher.matches("+5511912345678", "912345678"))
    }

    @Test
    fun `matches when a national trunk prefix zero is present`() {
        assertTrue(BrazilianPhoneNumberMatcher.matches("011912345678", "+5511912345678"))
    }

    @Test
    fun `matches ignoring punctuation and whitespace formatting`() {
        assertTrue(BrazilianPhoneNumberMatcher.matches("(11) 91234-5678", "+55 11 91234 5678"))
    }

    @Test
    fun `does not match when the DDD differs`() {
        assertFalse(BrazilianPhoneNumberMatcher.matches("11912345678", "21912345678"))
    }

    @Test
    fun `does not match when the subscriber number differs`() {
        assertFalse(BrazilianPhoneNumberMatcher.matches("11912345678", "11987654321"))
    }

    @Test
    fun `matches old 8-digit mobile format against new 9-digit format with same DDD`() {
        // contact saved before the mandatory 9th digit rollout
        val oldFormatContact = "1198765432"
        val newFormatCallerId = "+5511998765432"
        assertTrue(BrazilianPhoneNumberMatcher.matches(oldFormatContact, newFormatCallerId))
    }

    @Test
    fun `matches new 9-digit format against old 8-digit format regardless of argument order`() {
        val oldFormatContact = "1198765432"
        val newFormatCallerId = "+5511998765432"
        assertTrue(BrazilianPhoneNumberMatcher.matches(newFormatCallerId, oldFormatContact))
    }

    @Test
    fun `does not match old and new format numbers when the last 8 digits differ`() {
        val oldFormatContact = "1198765432"
        val differentNewFormatNumber = "+5511998765431"
        assertFalse(BrazilianPhoneNumberMatcher.matches(oldFormatContact, differentNewFormatNumber))
    }

    @Test
    fun `does not match old and new format numbers when the DDD differs`() {
        val oldFormatContact = "1198765432"
        val newFormatDifferentDdd = "+5521998765432"
        assertFalse(BrazilianPhoneNumberMatcher.matches(oldFormatContact, newFormatDifferentDdd))
    }

    @Test
    fun `matches landline numbers by DDD plus 8-digit subscriber number`() {
        assertTrue(BrazilianPhoneNumberMatcher.matches("+551123456789", "1123456789"))
    }

    @Test
    fun `matches when one side has no DDD and only the subscriber number can be compared`() {
        // only the last 8-9 digits are known for one of the two numbers
        assertTrue(BrazilianPhoneNumberMatcher.matches("98765432", "1198765432"))
    }

    @Test
    fun `does not match unrelated numbers`() {
        assertFalse(BrazilianPhoneNumberMatcher.matches("11912345678", "21987654321"))
    }

    @Test
    fun `does not match when input is too short to be a valid number`() {
        assertFalse(BrazilianPhoneNumberMatcher.matches("1234567", "11912345678"))
    }

    @Test
    fun `does not match blank or empty input`() {
        assertFalse(BrazilianPhoneNumberMatcher.matches("", "11912345678"))
        assertFalse(BrazilianPhoneNumberMatcher.matches("11912345678", ""))
    }
}
