package org.fossify.phone.models

import android.content.Context
import org.fossify.commons.extensions.getPhoneNumberTypeText

data class SpeedDial(
    val id: Int,
    var number: String,
    var displayName: String,
    var type: Int? = null,
    var label: String? = null
) {
    fun isValid() = number.trim().isNotEmpty()

    fun getName(context: Context) = if (type != null && label != null) {
        "$displayName - ${context.getPhoneNumberTypeText(type!!, label!!)}"
    } else {
        displayName
    }
}
