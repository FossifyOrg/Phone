package org.fossify.phone.helpers

import android.content.Context
import android.net.Uri
import android.telecom.Call
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
import java.util.Collections

// Resolving a caller loads the whole contacts database, and the notification is rebuilt on every
// single call state change, so the result is cached per handle. Bounded at MAX_CACHE_SIZE because
// Telecom never hands us more than a couple of concurrent calls.
private const val MAX_CACHE_SIZE = 4
private const val CACHE_LOAD_FACTOR = 0.75f
private val contactCache = Collections.synchronizedMap(
    object : LinkedHashMap<String, CallContact>(MAX_CACHE_SIZE, CACHE_LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CallContact>) =
            size > MAX_CACHE_SIZE
    }
)

fun clearCallContactCache() = contactCache.clear()

fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
    if (call.isConference()) {
        callback(CallContact(context.getString(R.string.conference), "", "", ""))
        return
    }

    val handle = try {
        call?.details?.handle?.toString()
    } catch (_: NullPointerException) {
        null
    }

    // everything below has to stay off the main thread: callers do contacts and thumbnail lookups
    // inside the callback, so a cache hit must not shortcut the dispatch
    ensureBackgroundThread {
        val callContact = CallContact("", "", "", "")
        if (handle == null) {
            callback(callContact)
            return@ensureBackgroundThread
        }

        // CallContact is mutable, hand out copies so a caller cannot poison the cache
        contactCache[handle]?.let {
            callback(it.copy())
            return@ensureBackgroundThread
        }

        val uri = Uri.decode(handle)
        if (!uri.startsWith("tel:")) {
            // SIP and other schemes cannot be looked up by number, but the caller still needs an
            // answer or the notification is never posted
            callback(callContact)
            return@ensureBackgroundThread
        }

        val number = uri.substringAfter("tel:")
        val privateCursor = context.getMyContactsCursor(
            favoritesOnly = false,
            withPhoneNumbersOnly = true
        )

        ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
            val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
            if (privateContacts.isNotEmpty()) {
                contacts.addAll(privateContacts)
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
                        callContact.numberLabel = context.getPhoneNumberTypeText(
                            specificPhoneNumber.type, specificPhoneNumber.label
                        )
                    }
                }
            } else {
                callContact.name = callContact.number
            }

            contactCache[handle] = callContact.copy()
            callback(callContact)
        }
    }
}
