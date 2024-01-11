package org.fossify.phone.services

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.helpers.SimpleContactsHelper

@RequiresApi(Build.VERSION_CODES.N)
class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        when {
            number != null && isNumberBlocked(number.normalizePhoneNumber()) -> {
                respondToCall(callDetails, isBlocked = true)
            }

            number != null && baseConfig.blockUnknownNumbers -> {
                val simpleContactsHelper = SimpleContactsHelper(this)
                val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                simpleContactsHelper.exists(number, privateCursor) { exists ->
                    respondToCall(callDetails, isBlocked = !exists)
                }
            }

            number == null && baseConfig.blockHiddenNumbers -> {
                respondToCall(callDetails, isBlocked = true)
            }

            else -> {
                respondToCall(callDetails, isBlocked = false)
            }
        }
    }

    private fun respondToCall(callDetails: Call.Details, isBlocked: Boolean) {
        val response = CallResponse.Builder()
            .setDisallowCall(isBlocked)
            .setRejectCall(isBlocked)
            .setSkipCallLog(isBlocked)
            .setSkipNotification(isBlocked)
            .build()

        respondToCall(callDetails, response)
    }
}
