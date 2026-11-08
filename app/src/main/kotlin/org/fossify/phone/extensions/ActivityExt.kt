package org.fossify.phone.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import org.fossify.commons.extensions.isPackageInstalled
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.launchViewContactIntent
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.CONTACT_ID
import org.fossify.commons.helpers.FIRST_CONTACT_ID
import org.fossify.commons.helpers.IS_PRIVATE
import org.fossify.commons.helpers.ON_CLICK_CALL_CONTACT
import org.fossify.commons.helpers.ON_CLICK_VIEW_CONTACT
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity

fun SimpleActivity.handleGenericContactClick(contact: Contact) {
    when (config.onContactClick) {
        ON_CLICK_CALL_CONTACT -> startCallWithConfirmationCheck(contact)
        ON_CLICK_VIEW_CONTACT -> startContactDetailsIntent(contact)
    }
}

fun SimpleActivity.launchCreateNewContactIntent() {
    Intent().apply {
        action = Intent.ACTION_INSERT
        data = ContactsContract.Contacts.CONTENT_URI
        launchActivityIntent(this)
    }
}

// handle private contacts differently, only Simple Contacts Pro can open them
fun Activity.startContactDetailsIntent(contact: Contact) {
    val simpleContacts = "org.fossify.contacts"
    val simpleContactsDebug = "org.fossify.contacts.debug"
    val isPrivateContact = contact.rawId > FIRST_CONTACT_ID
            && contact.contactId > FIRST_CONTACT_ID
            && contact.rawId == contact.contactId
            && (isPackageInstalled(simpleContacts) || isPackageInstalled(simpleContactsDebug))
    if (isPrivateContact) {
        Intent().apply {
            action = Intent.ACTION_VIEW
            putExtra(CONTACT_ID, contact.rawId)
            putExtra(IS_PRIVATE, true)
            `package` =
                if (isPackageInstalled(simpleContacts)) simpleContacts else simpleContactsDebug
            setDataAndType(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                "vnd.android.cursor.dir/person"
            )
            launchActivityIntent(this)
        }
    } else {
        ensureBackgroundThread {
            val lookupKey =
                getVerifiedLookupKey(contact)
            if (lookupKey.isNullOrEmpty()) {
                runOnUiThread { toast(R.string.could_not_access_contacts) }
                return@ensureBackgroundThread
            }
            val publicUri =
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            runOnUiThread {
                launchViewContactIntent(publicUri)
            }
        }
    }
}


/**
 * Work with only the raw contact you selected in the Phone search results.
 * Sometimes, Commons' StructuredName-only lookup might return an empty key
 * even for valid contacts.
 */
private fun Activity.getVerifiedLookupKey(contact: Contact): String? {
    if (contact.rawId <= 0 || contact.contactId <= 0) return null

    return try {
        val rawUri = android.content.ContentUris.withAppendedId(
            ContactsContract.RawContacts.CONTENT_URI, contact.rawId.toLong()
        )
        val providerContactId = contentResolver.query(
            rawUri, arrayOf(ContactsContract.RawContacts.CONTACT_ID), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
        if (providerContactId != contact.contactId.toLong()) return null

        val contactUri = android.content.ContentUris.withAppendedId(
            ContactsContract.Contacts.CONTENT_URI, providerContactId
        )
        contentResolver.query(
            contactUri, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                cursor.getString(0)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
