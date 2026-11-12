package org.fossify.phone.helpers

import android.content.Context
import android.net.Uri
import android.telecom.Call
import android.telecom.TelecomManager
import org.fossify.commons.extensions.formatPhoneNumber
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getPhoneNumberTypeText
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.isConference
import org.fossify.phone.models.CallContact

fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
    if (call.isConference()) {
        callback(CallContact(context.getString(R.string.conference), "", "", ""))
        return
    }

    val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
    ensureBackgroundThread {
        val callContact = CallContact("", "", "", "")
        val handle = try {
            call?.details?.handle?.toString()
        } catch (e: NullPointerException) {
            null
        }

        if (handle == null) {
            callback(callContact)
            return@ensureBackgroundThread
        }

        val uri = Uri.decode(handle)
        if (uri.startsWith("tel:")) {
            val number = uri.substringAfter("tel:")
            ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                }

                val contactsWithMultipleNumbers = contacts.filter { it.phoneNumbers.size > 1 }
                val numbersToContactIDMap = HashMap<String, Int>()
                contactsWithMultipleNumbers.forEach { contact ->
                    contact.phoneNumbers.forEach { phoneNumber ->
                        numbersToContactIDMap[phoneNumber.value] = contact.contactId
                        numbersToContactIDMap[phoneNumber.normalizedNumber] = contact.contactId
                    }
                }

                callContact.number = if (context.config.formatPhoneNumbers) {
                    number.formatPhoneNumber()
                } else {
                    number
                }

                val contact = contacts.firstOrNull { it.doesHavePhoneNumber(number) }
                if (contact != null) {
                    callContact.name = contact.getNameToDisplay()
                    callContact.photoUri = contact.photoUri

                    if (contact.phoneNumbers.size > 1) {
                        val specificPhoneNumber = contact.phoneNumbers.firstOrNull { it.value == number }
                        if (specificPhoneNumber != null) {
                            callContact.numberLabel = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                        }
                    }
                } else {
                    // Not a saved contact: prefer the caller name provided by the network (CNAP),
                    // which the call log already shows, and fall back to the number.
                    callContact.name = getNetworkCallerName(call) ?: callContact.number
                }

                callback(callContact)
            }
        }
    }
}

/**
 * Caller name supplied by the network (CNAP), only when the network allows showing it.
 */
fun getNetworkCallerName(call: Call?): String? {
    val details = try {
        call?.details
    } catch (_: Exception) {
        null
    } ?: return null

    if (details.callerDisplayNamePresentation != TelecomManager.PRESENTATION_ALLOWED) {
        return null
    }
    return details.callerDisplayName?.trim()?.takeIf { it.isNotEmpty() }
}
