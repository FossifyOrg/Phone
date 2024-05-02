package org.fossify.phone.models

import android.telephony.PhoneNumberUtils
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.toDayCode

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
    val startTS: Long,
    val duration: Int,
    val type: Int,
    val simID: Int,
    val specificNumber: String,
    val specificType: String,
    val isUnknownNumber: Boolean,
    val groupedCalls: MutableList<RecentCall>? = null,
) : CallLogItem() {
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

    fun getDayCode() = startTS.toDayCode()
}
