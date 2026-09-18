package org.fossify.phone.helpers

/**
 * Brazilian carriers deliver caller ID in inconsistent formats for the very same line, e.g.
 * "+55XX912345678", "0XXXX912345678" (with a long-distance operator/carrier code such as 015 for
 * Vivo), "XX912345678" or just "912345678". Country code and operator code are never part of the
 * number itself, so contact matching must ignore them and compare only DDD (area code) + subscriber
 * number.
 *
 * Mobile subscriber numbers always have 9 digits and start with "9" (the "ninth digit" rule added
 * in 2016), while landlines have 8 digits, so that leading digit is what disambiguates where the
 * DDD ends when parsing a run of digits that has no explicit separators.
 */
object BrazilianPhoneNumberMatcher {
    private const val MOBILE_NUMBER_LENGTH = 9
    private const val LANDLINE_NUMBER_LENGTH = 8
    private const val DDD_LENGTH = 2
    private const val MOBILE_PREFIX = '9'

    private data class ParsedNumber(val ddd: String?, val number: String)

    private fun parse(rawNumber: String): ParsedNumber? {
        val digits = rawNumber.filter { it.isDigit() }
        if (digits.length < LANDLINE_NUMBER_LENGTH) {
            return null
        }

        val isMobile = digits.length >= MOBILE_NUMBER_LENGTH &&
            digits[digits.length - MOBILE_NUMBER_LENGTH] == MOBILE_PREFIX

        val numberLength = if (isMobile) MOBILE_NUMBER_LENGTH else LANDLINE_NUMBER_LENGTH
        val number = digits.takeLast(numberLength)
        val remainder = digits.dropLast(numberLength)
        val ddd = if (remainder.length >= DDD_LENGTH) remainder.takeLast(DDD_LENGTH) else null

        return ParsedNumber(ddd, number)
    }

    /**
     * Compares the most specific parts both numbers have in common. The operator/carrier code is
     * never considered, since it's not a stable part of the number.
     *
     * Old contacts saved before the mandatory 9th digit was rolled out may still have an 8-digit
     * mobile number, while an incoming call is reported in the current 9-digit format (or vice
     * versa). When the parsed lengths differ this way, only the last 8 digits are compared.
     */
    fun matches(a: String, b: String): Boolean {
        val parsedA = parse(a) ?: return false
        val parsedB = parse(b) ?: return false

        val numbersMatch = if (parsedA.number.length == parsedB.number.length) {
            parsedA.number == parsedB.number
        } else {
            parsedA.number.takeLast(LANDLINE_NUMBER_LENGTH) == parsedB.number.takeLast(LANDLINE_NUMBER_LENGTH)
        }

        if (!numbersMatch) {
            return false
        }

        return if (parsedA.ddd != null && parsedB.ddd != null) {
            parsedA.ddd == parsedB.ddd
        } else {
            true
        }
    }
}
