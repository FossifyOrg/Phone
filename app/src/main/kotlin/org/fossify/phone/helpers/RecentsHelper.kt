package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.provider.CallLog.Calls
import android.provider.CallLog.Calls.PRESENTATION_UNAVAILABLE
import android.provider.CallLog.Calls.PRESENTATION_UNKNOWN
import android.telephony.PhoneNumberUtils
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.extensions.getAvailableSIMCardLabels
import org.fossify.phone.models.RecentCall
import org.fossify.phone.models.SIMAccount

class RecentsHelper(private val context: Context) {
    companion object {
        private const val COMPARABLE_PHONE_NUMBER_LENGTH = 9
        const val QUERY_LIMIT = 100
    }

    private val contentUri = Calls.CONTENT_URI
    private var queryLimit = QUERY_LIMIT

    fun getRecentCalls(
        previousRecents: List<RecentCall> = ArrayList(),
        queryLimit: Int = QUERY_LIMIT,
        callback: (List<RecentCall>) -> Unit,
    ) {
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            callback(ArrayList())
            return
        }

        ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
            ensureBackgroundThread {
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                }

                this.queryLimit = queryLimit
                val recentCalls = if (previousRecents.isNotEmpty()) {
                    val previousRecentCalls = previousRecents
                        .flatMap { it.groupedCalls ?: listOf(it) }
                        .map { it.copy(groupedCalls = null) }

                    val newerRecents = getRecents(
                        contacts = contacts,
                        selection = "${Calls.DATE} > ?",
                        selectionParams = arrayOf("${previousRecentCalls.first().startTS}")
                    )

                    val olderRecents = getRecents(
                        contacts = contacts,
                        selection = "${Calls.DATE} < ?",
                        selectionParams = arrayOf("${previousRecentCalls.last().startTS}")
                    )

                    newerRecents + previousRecentCalls + olderRecents
                } else {
                    getRecents(contacts)
                }

                callback(
                    recentCalls
                        .sortedByDescending { it.startTS }
                        .distinctBy { it.id }
                )
            }
        }
    }

    fun getGroupedRecentCalls(
        previousRecents: List<RecentCall> = ArrayList(),
        queryLimit: Int = QUERY_LIMIT,
        callback: (List<RecentCall>) -> Unit,
    ) {
        getRecentCalls(previousRecents, queryLimit) { recentCalls ->
            callback(
                groupSubsequentCalls(calls = recentCalls)
            )
        }
    }

    private fun shouldGroupCalls(callA: RecentCall, callB: RecentCall): Boolean {
        val differentSim = callA.simID != callB.simID
        val differentDay = callA.dayCode != callB.dayCode
        val namesAreBothRealAndDifferent =
            callA.name != callB.name &&
                    callA.name != callA.phoneNumber &&
                    callB.name != callB.phoneNumber

        if (differentSim || differentDay || namesAreBothRealAndDifferent) return false

        @Suppress("DEPRECATION")
        return PhoneNumberUtils.compare(callA.phoneNumber, callB.phoneNumber)
    }

    private fun groupSubsequentCalls(calls: List<RecentCall>): List<RecentCall> {
        val result = mutableListOf<RecentCall>()
        if (calls.isEmpty()) return result

        var currentCall = calls[0]
        for (i in 1 until calls.size) {
            val nextCall = calls[i]
            if (shouldGroupCalls(currentCall, nextCall)) {
                if (currentCall.groupedCalls.isNullOrEmpty()) {
                    currentCall = currentCall.copy(groupedCalls = mutableListOf(currentCall))
                }

                currentCall.groupedCalls?.add(nextCall)
            } else {
                result += currentCall
                currentCall = nextCall
            }
        }

        result.add(currentCall)
        return result
    }

    @SuppressLint("NewApi")
    private fun getRecents(
        contacts: List<Contact>,
        selection: String? = null,
        selectionParams: Array<String>? = null,
    ): List<RecentCall> {
        val recentCalls = mutableListOf<RecentCall>()
        var previousStartTS = 0L
        val contactsNumbersMap = HashMap<String, String>()
        val contactPhotosMap = HashMap<String, String>()

        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.CACHED_NAME,
            Calls.CACHED_PHOTO_URI,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.PHONE_ACCOUNT_ID,
            Calls.NUMBER_PRESENTATION
        )

        val accountIdToSimAccountMap = HashMap<String, SIMAccount>()
        context.getAvailableSIMCardLabels().forEach {
            accountIdToSimAccountMap[it.handle.id] = it
        }

        val cursor = if (isNougatPlus()) {
            // https://issuetracker.google.com/issues/175198972?pli=1#comment6
            val limitedUri = contentUri.buildUpon()
                .appendQueryParameter(Calls.LIMIT_PARAM_KEY, queryLimit.toString())
                .build()
            val sortOrder = "${Calls.DATE} DESC"
            context.contentResolver.query(limitedUri, projection, selection, selectionParams, sortOrder)
        } else {
            val sortOrder = "${Calls.DATE} DESC LIMIT $queryLimit"
            context.contentResolver.query(contentUri, projection, selection, selectionParams, sortOrder)
        }

        val contactsWithMultipleNumbers = contacts.filter { it.phoneNumbers.size > 1 }
        val numbersToContactIDMap = HashMap<String, Int>()
        contactsWithMultipleNumbers.forEach { contact ->
            contact.phoneNumbers.forEach { phoneNumber ->
                numbersToContactIDMap[phoneNumber.value] = contact.contactId
                numbersToContactIDMap[phoneNumber.normalizedNumber] = contact.contactId
            }
        }

        cursor?.use {
            if (!cursor.moveToFirst()) {
                return@use
            }

            do {
                val id = cursor.getIntValue(Calls._ID)
                var isUnknownNumber = false
                val number = cursor.getStringValueOrNull(Calls.NUMBER)
                val presentation = cursor.getIntValueOrNull(Calls.NUMBER_PRESENTATION) ?: Calls.PRESENTATION_ALLOWED
                val presentationBlocked = presentation == PRESENTATION_UNKNOWN
                        || presentation == PRESENTATION_UNAVAILABLE
                        || presentation == Calls.PRESENTATION_RESTRICTED
                if (presentationBlocked || number.isNullOrBlank() || number == "-1") {
                    isUnknownNumber = true
                }

                var name = cursor.getStringValueOrNull(Calls.CACHED_NAME)
                if (name.isNullOrEmpty() || name == "-1") {
                    name = number.orEmpty()
                }

                if (name == number && !isUnknownNumber) {
                    if (contactsNumbersMap.containsKey(number)) {
                        name = contactsNumbersMap[number]!!
                    } else {
                        val normalizedNumber = number.normalizePhoneNumber()
                        if (normalizedNumber!!.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
                            name = contacts.filter { it.phoneNumbers.isNotEmpty() }.firstOrNull { contact ->
                                val curNumber = contact.phoneNumbers.first().normalizedNumber
                                if (curNumber.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
                                    if (curNumber.substring(curNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH) == normalizedNumber.substring(
                                            normalizedNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH
                                        )
                                    ) {
                                        contactsNumbersMap[number] = contact.getNameToDisplay()
                                        return@firstOrNull true
                                    }
                                }
                                false
                            }?.name ?: number
                        }
                    }
                }

                if (name.isEmpty() || name == "-1") {
                    name = context.getString(R.string.unknown)
                }

                var photoUri = cursor.getStringValue(Calls.CACHED_PHOTO_URI) ?: ""
                if (photoUri.isEmpty() && !number.isNullOrEmpty()) {
                    if (contactPhotosMap.containsKey(number)) {
                        photoUri = contactPhotosMap[number]!!
                    } else {
                        val contact = contacts.firstOrNull { it.doesHavePhoneNumber(number) }
                        if (contact != null) {
                            photoUri = contact.photoUri
                            contactPhotosMap[number] = contact.photoUri
                        }
                    }
                }

                val startTS = cursor.getLongValue(Calls.DATE)
                if (previousStartTS == startTS) {
                    continue
                } else {
                    previousStartTS = startTS
                }

                val duration = cursor.getIntValue(Calls.DURATION)
                val type = cursor.getIntValue(Calls.TYPE)
                val accountId = cursor.getStringValue(Calls.PHONE_ACCOUNT_ID)
                val simAccount = accountIdToSimAccountMap[accountId]
                var specificNumber = ""
                var specificType = ""

                val contactIdWithMultipleNumbers = numbersToContactIDMap[number]
                if (contactIdWithMultipleNumbers != null) {
                    val specificPhoneNumber =
                        contacts.firstOrNull { it.contactId == contactIdWithMultipleNumbers }?.phoneNumbers?.firstOrNull { it.value == number }
                    if (specificPhoneNumber != null) {
                        specificNumber = specificPhoneNumber.value
                        specificType = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                    }
                }

                recentCalls.add(
                    RecentCall(
                        id = id,
                        phoneNumber = number.orEmpty(),
                        name = name,
                        photoUri = photoUri,
                        startTS = startTS,
                        duration = duration,
                        type = type,
                        simID = simAccount?.id ?: -1,
                        simColor = simAccount?.color ?: -1,
                        specificNumber = specificNumber,
                        specificType = specificType,
                        isUnknownNumber = isUnknownNumber
                    )
                )
            } while (cursor.moveToNext() && recentCalls.size < queryLimit)
        }

        val blockedNumbers = context.getBlockedNumbers()

        return recentCalls
            .filter { !context.isNumberBlocked(it.phoneNumber, blockedNumbers) }
    }

    fun removeRecentCalls(ids: List<Int>, callback: () -> Unit) {
        ensureBackgroundThread {
            ids.chunked(30).forEach { chunk ->
                val selection = "${Calls._ID} IN (${getQuestionMarks(chunk.size)})"
                val selectionArgs = chunk.map { it.toString() }.toTypedArray()
                context.contentResolver.delete(contentUri, selection, selectionArgs)
            }
            callback()
        }
    }

    @SuppressLint("MissingPermission")
    fun removeAllRecentCalls(activity: SimpleActivity, callback: () -> Unit) {
        activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
            if (it) {
                ensureBackgroundThread {
                    context.contentResolver.delete(contentUri, null, null)
                    callback()
                }
            }
        }
    }

    fun restoreRecentCalls(activity: SimpleActivity, objects: List<RecentCall>, callback: () -> Unit) {
        activity.handlePermission(PERMISSION_WRITE_CALL_LOG) { granted ->
            if (granted) {
                ensureBackgroundThread {
                    val values = objects
                        .sortedBy { it.startTS }
                        .map {
                            ContentValues().apply {
                                put(Calls.NUMBER, it.phoneNumber)
                                put(Calls.TYPE, it.type)
                                put(Calls.DATE, it.startTS)
                                put(Calls.DURATION, it.duration)
                                put(Calls.CACHED_NAME, it.name)
                            }
                        }.toTypedArray()

                    context.contentResolver.bulkInsert(contentUri, values)
                    callback()
                }
            }
        }
    }
}
