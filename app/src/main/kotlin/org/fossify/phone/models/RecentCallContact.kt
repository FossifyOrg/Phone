package org.fossify.phone.models

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact

class RecentCallContact : Comparable<RecentCallContact> {
    val isContact: Boolean
    val contact: Contact
    val recentCall: RecentCall?

    constructor(contact: Contact) {
        this.contact = contact
        this.recentCall = null
        this.isContact = true
    }

    constructor(recentCall: RecentCall) {
        this.contact = Contact(
            recentCall.id,
            phoneNumbers = arrayListOf(PhoneNumber(recentCall.phoneNumber, 0, recentCall.phoneNumber, recentCall.phoneNumber, true)),
            contactId = 0,
        )
        this.recentCall = recentCall
        this.isContact = false
    }

    override fun compareTo(other: RecentCallContact): Int {
        return if(isContact) {
            if(other.isContact)
                contact.compareTo(other.contact)
            else
                -1
        } else {
            if(other.isContact)
                1
            else
                other.recentCall!!.startTS.compareTo(recentCall!!.startTS)
        }
    }
}
