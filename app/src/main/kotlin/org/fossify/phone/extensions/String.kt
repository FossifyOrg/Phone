package org.fossify.phone.extensions

import android.telephony.PhoneNumberUtils

/**
 * Formats a phone number for display. Numbers without a country code are formatted
 * using the conventions of [countryIso], usually from [android.content.Context.getCurrentCountryIso].
 */
fun String.formatPhoneNumber(countryIso: String, minimumLength: Int = 4): String {
    return if (length >= minimumLength) {
        PhoneNumberUtils.formatNumber(this, countryIso) ?: this
    } else {
        this
    }
}
