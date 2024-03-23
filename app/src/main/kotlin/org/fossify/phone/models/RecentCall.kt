package org.fossify.phone.models

import android.telephony.PhoneNumberUtils
import org.fossify.commons.extensions.normalizePhoneNumber

/**
 * Used at displaying recent calls.
 * For contacts with multiple numbers specify the number and type
 */
@kotlinx.serialization.Serializable
data class RecentCall(
    val id: Int,
    val phoneNumber: String,
    val name: String,
    val photoUri: String,
    val startTS: Int,
    val duration: Int,
    val type: Int,
    val neighbourIDs: MutableList<Int>,
    val simID: Int,
    val simColor: Int,
    val specificNumber: String,
    val specificType: String,
    val isUnknownNumber: Boolean,
) {
    fun doesContainPhoneNumber(text: String): Boolean {
        return if (text.toIntOrNull() != null) {
            val normalizedText = text.normalizePhoneNumber()
            PhoneNumberUtils.compare(phoneNumber.normalizePhoneNumber(), normalizedText) ||
                phoneNumber.contains(text) ||
                phoneNumber.normalizePhoneNumber().contains(normalizedText) ||
                phoneNumber.contains(normalizedText)
        } else {
            false
        }
    }
}
