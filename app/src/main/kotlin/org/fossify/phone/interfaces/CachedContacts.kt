package org.fossify.phone.interfaces

import android.content.Context
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.extensions.config

interface CachedContacts {
    var cachedContacts: ArrayList<Contact>

    fun cacheContacts(context: Context) {
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
            if (SMT_PRIVATE !in context.config.ignoredContactSources) {
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                    contacts.sort()
                }
            }

            try {
                cachedContacts.clear()
                cachedContacts.addAll(contacts)
            } catch (ignored: Exception) {
            }
        }
    }
}
