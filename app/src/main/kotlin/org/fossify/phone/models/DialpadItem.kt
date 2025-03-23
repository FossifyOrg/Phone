package org.fossify.phone.models

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact

class DialpadItem {
    val header: String?
    val contact: Contact?
    val recentCall: RecentCall?
    val itemType: DialpadItemType

    constructor(header: String) {
        this.header = header
        this.contact = null
        this.recentCall = null
        this.itemType = DialpadItemType.HEADER
    }

    constructor(contact: Contact) {
        this.header = null
        this.contact = contact
        this.recentCall = null
        this.itemType = DialpadItemType.CONTACT
    }

    constructor(recentCall: RecentCall) {
        this.header = null
        this.contact = null
        this.recentCall = recentCall
        this.itemType = DialpadItemType.RECENTCALL
    }

    fun isHeader(): Boolean = header != null

    fun isContact(): Boolean = contact != null

    fun isRecentCall(): Boolean = recentCall != null

    fun getItemId(): Int {
        if(isContact()) {
            return contact!!.rawId
        }

        if (isRecentCall()) {
            return recentCall!!.getItemId()
        }

        return 0
    }

    /*override fun compareTo(other: DialpadItem): Int {
        return if (isContact) {
            if (other.isContact) {
                contact.compareTo(other.contact)
            } else {
                -1
            }
        } else {
            if (other.isContact) {
                1
            } else {
                other.recentCall!!.startTS.compareTo(recentCall!!.startTS)
            }
        }
    }*/

    enum class DialpadItemType {
        HEADER,
        CONTACT,
        RECENTCALL
    }
}
