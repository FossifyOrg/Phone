package org.fossify.phone.models

import org.fossify.commons.models.contacts.Contact

class DialpadItem {
    val id: Int
    val header: String?
    val isHeaderForContacts: Boolean
    val contact: Contact?
    val recentCall: RecentCall?
    val itemType: DialpadItemType

    constructor(header: String, isHeaderForContacts: Boolean) {
        this.id = increment()
        this.header = header
        this.isHeaderForContacts = isHeaderForContacts
        this.contact = null
        this.recentCall = null
        this.itemType = DialpadItemType.HEADER
    }

    constructor(contact: Contact) {
        this.id = increment()
        this.header = null
        isHeaderForContacts = false
        this.contact = contact
        this.recentCall = null
        this.itemType = DialpadItemType.CONTACT
    }

    constructor(recentCall: RecentCall) {
        this.id = increment()
        this.header = null
        isHeaderForContacts = false
        this.contact = null
        this.recentCall = recentCall
        this.itemType = DialpadItemType.RECENTCALL
    }

    fun isHeader(): Boolean = header != null

    fun isContact(): Boolean = contact != null

    fun isRecentCall(): Boolean = recentCall != null

    fun getItemId(): Int = this.id

    enum class DialpadItemType {
        HEADER,
        CONTACT,
        RECENTCALL
    }

    companion object {
        private var idCount = 0

        fun increment(): Int {
            return ++idCount
        }
    }
}
