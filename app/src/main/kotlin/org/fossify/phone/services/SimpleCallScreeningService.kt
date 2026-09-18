package org.fossify.phone.services

import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.phone.helpers.BrazilianPhoneNumberMatcher

class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        when {
            number != null && isNumberBlocked(number) -> {
                respondToCall(callDetails, isBlocked = true)
            }

            number != null && baseConfig.blockUnknownNumbers -> {
                val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                val result = SimpleContactsHelper(this).existsSync(number, privateCursor)
                // Brazilian carriers deliver the same number with varying country/operator code
                // prefixes (e.g. "+55XX912345678", "0XXXX912345678", "XX912345678"), which the
                // exact/system lookups above may fail to match. Fall back to a DDD+number aware
                // comparison before treating the caller as unknown.
                val isBlocked = when (result) {
                    ContactLookupResult.Found -> false
                    ContactLookupResult.Undetermined -> false
                    ContactLookupResult.NotFound -> !isKnownContactNumber(number)
                }
                respondToCall(callDetails, isBlocked = isBlocked)
            }

            number == null && baseConfig.blockHiddenNumbers -> {
                respondToCall(callDetails, isBlocked = true)
            }

            else -> {
                respondToCall(callDetails, isBlocked = false)
            }
        }
    }

    private fun isKnownContactNumber(number: String): Boolean {
        val contactNumbers = getPrivateContactNumbers() + getSystemContactNumbers()
        return contactNumbers.any { BrazilianPhoneNumberMatcher.matches(number, it) }
    }

    private fun getPrivateContactNumbers(): List<String> {
        val cursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        return MyContactsContentProvider.getSimpleContacts(this, cursor)
            .flatMap { it.phoneNumbers }
            .map { it.value }
    }

    private fun getSystemContactNumbers(): List<String> {
        if (!hasPermission(PERMISSION_READ_CONTACTS)) {
            return emptyList()
        }

        val numbers = ArrayList<String>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    cursor.getString(numberIndex)?.let { numbers.add(it) }
                }
            }
        } catch (ignored: Exception) {
        }
        return numbers
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
