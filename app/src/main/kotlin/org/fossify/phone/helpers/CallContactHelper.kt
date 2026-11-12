package org.fossify.phone.helpers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import android.telecom.TelecomManager
import org.fossify.commons.extensions.formatPhoneNumber
import org.fossify.commons.extensions.getIntValueOrNull
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getPhoneNumberTypeText
import org.fossify.commons.extensions.getStringValueOrNull
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
        if (!uri.startsWith("tel:")) {
            // e.g. sip: or voicemail: handles — never leave the caller (notification, call screen)
            // waiting without an answer
            callContact.name = getNetworkCallerName(call) ?: uri.substringAfter(':')
            callback(callContact)
            return@ensureBackgroundThread
        }

        val number = uri.substringAfter("tel:")
        callContact.number = if (context.config.formatPhoneNumbers) {
            number.formatPhoneNumber()
        } else {
            number
        }

        // Look up only this number. This used to load the whole address book (plus private
        // contacts) on every call state change, which took seconds with large address books and
        // delayed or froze the incoming call screen and notification.
        val contact = findSavedContact(context, number) ?: findPrivateContact(context, privateCursor, number)

        if (contact != null) {
            callContact.name = contact.name
            callContact.photoUri = contact.photoUri
            if (contact.hasMultipleNumbers) {
                callContact.numberLabel = contact.numberLabel.orEmpty()
            }
        } else {
            // Not a saved contact: prefer the caller name provided by the network (CNAP),
            // which the call log already shows, and fall back to the number.
            callContact.name = getNetworkCallerName(call) ?: callContact.number
        }

        callback(callContact)
    }
}

private class SavedContact(
    val name: String,
    val photoUri: String,
    val hasMultipleNumbers: Boolean,
    val numberLabel: String?,
)

/**
 * Finds the saved contact for [number] with a single indexed PhoneLookup query, then loads just
 * that contact so the name is formatted like everywhere else in the app (surname first, etc.).
 */
private fun findSavedContact(context: Context, number: String): SavedContact? {
    val row = queryPhoneLookup(context, number) ?: return null
    val contact = row.lookupKey?.let {
        try {
            ContactsHelper(context).getContactWithLookupKey(it)
        } catch (_: Exception) {
            null
        }
    }

    return SavedContact(
        name = contact?.getNameToDisplay()?.takeIf { it.isNotBlank() } ?: row.displayName.orEmpty(),
        photoUri = contact?.photoUri?.takeIf { it.isNotEmpty() } ?: row.photoUri.orEmpty(),
        hasMultipleNumbers = (contact?.phoneNumbers?.size ?: 1) > 1,
        numberLabel = row.numberLabel,
    )
}

private class PhoneLookupRow(
    val lookupKey: String?,
    val displayName: String?,
    val photoUri: String?,
    val numberLabel: String?,
)

private fun queryPhoneLookup(context: Context, number: String): PhoneLookupRow? {
    val lookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        PhoneLookup.LOOKUP_KEY,
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.PHOTO_URI,
        PhoneLookup.TYPE,
        PhoneLookup.LABEL
    )

    return try {
        context.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }

            val label = cursor.getStringValueOrNull(PhoneLookup.LABEL).orEmpty()
            val numberLabel = cursor.getIntValueOrNull(PhoneLookup.TYPE)?.let { type ->
                context.getPhoneNumberTypeText(type, label)
            }
            PhoneLookupRow(
                lookupKey = cursor.getStringValueOrNull(PhoneLookup.LOOKUP_KEY),
                displayName = cursor.getStringValueOrNull(PhoneLookup.DISPLAY_NAME),
                photoUri = cursor.getStringValueOrNull(PhoneLookup.PHOTO_URI),
                numberLabel = numberLabel,
            )
        }
    } catch (_: Exception) {
        // e.g. contacts permission revoked: behave like an unknown number
        null
    }
}

/** Contacts kept only in Fossify Contacts' private storage are not visible to PhoneLookup. */
private fun findPrivateContact(context: Context, privateCursor: Cursor?, number: String): SavedContact? {
    val contact = MyContactsContentProvider.getContacts(context, privateCursor)
        .firstOrNull { it.doesHavePhoneNumber(number) } ?: return null
    val specificNumber = contact.phoneNumbers.firstOrNull { it.value == number }
    return SavedContact(
        name = contact.getNameToDisplay(),
        photoUri = contact.photoUri,
        hasMultipleNumbers = contact.phoneNumbers.size > 1,
        numberLabel = specificNumber?.let { context.getPhoneNumberTypeText(it.type, it.label) },
    )
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
