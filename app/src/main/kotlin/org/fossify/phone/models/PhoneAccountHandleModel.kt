package org.fossify.phone.models

import android.content.ComponentName
import android.telecom.PhoneAccountHandle

data class PhoneAccountHandleModel(
    val packageName: String,
    val className: String,
    val id: String
) {
    fun toPhoneAccountHandle(): PhoneAccountHandle {
        return PhoneAccountHandle(
            ComponentName(packageName, className), id
        )
    }
}
