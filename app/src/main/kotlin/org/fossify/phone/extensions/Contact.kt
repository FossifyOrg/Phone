package org.fossify.phone.extensions

import org.fossify.commons.helpers.getProperText
import org.fossify.commons.models.contacts.Contact

fun Contact.matchesSearchQuery(query: String, shouldNormalize: Boolean): Boolean {
    return getProperText(getNameToDisplay(), shouldNormalize).contains(query, true) ||
        getProperText(nickname, shouldNormalize).contains(query, true) ||
        (query.toLongOrNull() != null && doesContainPhoneNumber(query, true)) ||
        emails.any { it.value.contains(query, true) } ||
        addresses.any { getProperText(it.value, shouldNormalize).contains(query, true) } ||
        IMs.any { it.value.contains(query, true) } ||
        getProperText(notes, shouldNormalize).contains(query, true) ||
        getProperText(organization.company, shouldNormalize).contains(query, true) ||
        getProperText(organization.jobPosition, shouldNormalize).contains(query, true) ||
        websites.any { it.contains(query, true) }
}
