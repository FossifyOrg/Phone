package org.fossify.phone.helpers

import org.fossify.commons.models.contacts.Contact
import java.text.Normalizer

private val COMBINING_MARKS = "\\p{Mn}+".toRegex()
private val WHITESPACE = "\\s+".toRegex()

/**
 * Lowercase, collapse whitespace and strip accents, so that "José", "JOSE" and "jose" compare
 * equal in both directions. Pure JVM code, unit tested.
 */
fun normalizeForSearch(text: String): String {
    if (text.isEmpty()) {
        return text
    }
    val decomposed = Normalizer.normalize(text.trim().replace(WHITESPACE, " "), Normalizer.Form.NFD)
    return decomposed.replace(COMBINING_MARKS, "").lowercase()
}

/**
 * A contact with its searchable fields normalized once, up front. Previously every keystroke
 * re-normalized every field of every contact on the UI thread, which froze the app with large
 * address books.
 */
class ContactSearchEntry(
    val contact: Contact,
    val normalizedName: String,
    private val normalizedFields: List<String>,
) {
    fun matches(normalizedQuery: String): Boolean = searchFieldsMatch(normalizedQuery, normalizedFields)

    companion object {
        fun from(contact: Contact): ContactSearchEntry {
            val name = normalizeForSearch(contact.getNameToDisplay())
            val fields = buildList {
                add(name)
                add(normalizeForSearch(contact.nickname))
                contact.emails.forEach { add(normalizeForSearch(it.value)) }
                contact.addresses.forEach { add(normalizeForSearch(it.value)) }
                contact.IMs.forEach { add(normalizeForSearch(it.value)) }
                add(normalizeForSearch(contact.notes))
                add(normalizeForSearch(contact.organization.company))
                add(normalizeForSearch(contact.organization.jobPosition))
                contact.websites.forEach { add(normalizeForSearch(it)) }
            }.filter { it.isNotEmpty() }
            return ContactSearchEntry(contact, name, fields)
        }
    }
}

fun searchFieldsMatch(normalizedQuery: String, normalizedFields: List<String>): Boolean {
    return normalizedFields.any { it.contains(normalizedQuery) }
}

/**
 * Filters and orders contacts for [query]. Runs off the UI thread. Contacts whose name starts with
 * (or contains) the query come first, the rest keep their original order.
 */
fun searchContacts(entries: List<ContactSearchEntry>, query: String): ArrayList<Contact> {
    val normalizedQuery = normalizeForSearch(query)
    val isNumber = query.trim().toLongOrNull() != null
    val matches = entries.filter { entry ->
        entry.matches(normalizedQuery) || (isNumber && entry.contact.doesContainPhoneNumber(query.trim(), true))
    }

    val nameFirst = matches.sortedBy { entry ->
        !entry.normalizedName.startsWith(normalizedQuery) && !entry.normalizedName.contains(normalizedQuery)
    }
    return nameFirst.mapTo(ArrayList(nameFirst.size)) { it.contact }
}
