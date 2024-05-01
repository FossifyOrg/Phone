package org.fossify.phone.fragments

import android.content.Context
import android.util.AttributeSet
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.RecentCallsAdapter
import org.fossify.phone.databinding.FragmentRecentsBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.interfaces.RefreshItemsListener
import org.fossify.phone.models.CallLogItem
import org.fossify.phone.models.RecentCall

class RecentsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.RecentsInnerBinding>(context, attributeSet),
    RefreshItemsListener {
    private lateinit var binding: FragmentRecentsBinding
    private var allRecentCalls = listOf<CallLogItem>()
    private var recentsAdapter: RecentCallsAdapter? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentRecentsBinding.bind(this)
        innerBinding = RecentsInnerBinding(binding)
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        binding.recentsPlaceholder.text = context.getString(placeholderResId)
        binding.recentsPlaceholder2.apply {
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.recentsPlaceholder.setTextColor(textColor)
        binding.recentsPlaceholder2.setTextColor(properPrimaryColor)

        recentsAdapter?.apply {
            initDrawables()
            updateTextColor(textColor)
        }
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = context?.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        getRecentCalls { recents ->
            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)

                allRecentCalls = recents
                    .setNamesIfEmpty(contacts, privateContacts)
                    .hidePrivateContacts(privateContacts, SMT_PRIVATE in context.baseConfig.ignoredContactSources)

                activity?.runOnUiThread {
                    binding.progressIndicator.hide()
                    gotRecents(allRecentCalls)
                }
            }
        }
    }

    private fun getRecentCalls(callback: (List<CallLogItem>) -> Unit) {
        val context = context ?: return
        val recentsHelper = RecentsHelper(context)
        val previousRecents = allRecentCalls.filterIsInstance<RecentCall>()

        if (context.config.groupSubsequentCalls) {
            recentsHelper.getGroupedRecentCalls(previousRecents) {
                callback(prepareCallLog(it))
            }
        } else {
            recentsHelper.getRecentCalls(previousRecents) {
                callback(prepareCallLog(it))
            }
        }
    }

    private fun prepareCallLog(recentCalls: List<RecentCall>): List<CallLogItem> {
        if (recentCalls.isEmpty()) {
            return emptyList()
        }

        val log = mutableListOf<CallLogItem>()
        var lastDayCode = ""

        for (call in recentCalls) {
            val currentDayCode = call.getDayCode()
            if (currentDayCode != lastDayCode) {
                log += CallLogItem.Date(timestamp = call.startTS, dayCode = currentDayCode)
                lastDayCode = currentDayCode
            }

            log += call
        }

        return log
    }

    private fun gotRecents(recents: List<CallLogItem>) {
        if (recents.isEmpty()) {
            binding.apply {
                showOrHidePlaceholder(true)
                recentsPlaceholder2.beGoneIf(context.hasPermission(PERMISSION_READ_CALL_LOG))
                recentsList.beGone()
            }
        } else {
            binding.apply {
                showOrHidePlaceholder(false)
                recentsPlaceholder2.beGone()
                recentsList.beVisible()
            }

            if (binding.recentsList.adapter == null) {
                recentsAdapter = RecentCallsAdapter(
                    activity = activity as SimpleActivity,
                    recentCalls = recents.toMutableList(),
                    recyclerView = binding.recentsList,
                    refreshItemsListener = this,
                    showOverflowMenu = true,
                    itemDelete = { deleted ->
                        allRecentCalls = allRecentCalls.filter { it !in deleted }
                    },
                    itemClick = {
                        val recentCall = it as RecentCall
                        if (context.config.showCallConfirmation) {
                            CallConfirmationDialog(activity as SimpleActivity, recentCall.name) {
                                activity?.launchCallIntent(recentCall.phoneNumber)
                            }
                        } else {
                            activity?.launchCallIntent(recentCall.phoneNumber)
                        }
                    }
                )

                binding.recentsList.adapter = recentsAdapter

                if (context.areSystemAnimationsEnabled) {
                    binding.recentsList.scheduleLayoutAnimation()
                }

                binding.recentsList.endlessScrollListener = object : MyRecyclerView.EndlessScrollListener {
                    override fun updateTop() {}

                    override fun updateBottom() {
                        getMoreRecentCalls()
                    }
                }

            } else {
                recentsAdapter?.updateItems(recents)
            }
        }
    }

    private fun getMoreRecentCalls() {
        val privateCursor = context?.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        getRecentCalls { recents ->
            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)

                allRecentCalls = recents
                    .setNamesIfEmpty(contacts, privateContacts)
                    .hidePrivateContacts(privateContacts, SMT_PRIVATE in context.baseConfig.ignoredContactSources)

                activity?.runOnUiThread {
                    gotRecents(allRecentCalls)
                }
            }
        }
    }

    private fun requestCallLogPermission() {
        activity?.handlePermission(PERMISSION_READ_CALL_LOG) {
            if (it) {
                binding.recentsPlaceholder.text = context.getString(R.string.no_previous_calls)
                binding.recentsPlaceholder2.beGone()

                getRecentCalls { recents ->
                    activity?.runOnUiThread {
                        gotRecents(recents)
                    }
                }
            }
        }
    }

    override fun onSearchClosed() {
        showOrHidePlaceholder(allRecentCalls.isEmpty())
        recentsAdapter?.updateItems(allRecentCalls)
    }

    override fun onSearchQueryChanged(text: String) {
        val fixedText = text.trim().replace("\\s+".toRegex(), " ")
        val recentCalls = allRecentCalls
            .filter {
                it is CallLogItem.Date || it is RecentCall && (it.name.contains(fixedText, true) || it.doesContainPhoneNumber(fixedText))
            }.sortedByDescending {
                it is CallLogItem.Date || it is RecentCall && it.name.startsWith(fixedText, true)
            }

        showOrHidePlaceholder(recentCalls.isEmpty())
        recentsAdapter?.updateItems(recentCalls, fixedText)
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        if (show && !binding.progressIndicator.isVisible()) {
            binding.recentsPlaceholder.beVisible()
        } else {
            binding.recentsPlaceholder.beGone()
        }
    }
}

// hide private contacts from recent calls
private fun List<CallLogItem>.hidePrivateContacts(privateContacts: List<Contact>, shouldHide: Boolean): List<CallLogItem> {
    return if (shouldHide) {
        val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
        filterNot { record ->
            when (record) {
                is CallLogItem.Date -> false
                is RecentCall -> record.phoneNumber in privateNumbers
            }
        }
    } else {
        this
    }
}

private fun List<CallLogItem>.setNamesIfEmpty(contacts: List<Contact>, privateContacts: List<Contact>): List<CallLogItem> {
    if (isEmpty()) return mutableListOf()

    val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
    val result = mutableListOf<CallLogItem>()

    forEach { record ->
        when (record) {
            is CallLogItem.Date -> result += record
            is RecentCall -> {
                result += if (record.phoneNumber == record.name) {
                    val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(record.phoneNumber) }
                    val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == record.phoneNumber }

                    when {
                        privateContact != null -> record.copyWithName(name = privateContact.getNameToDisplay())
                        contact != null -> record.copyWithName(name = contact.getNameToDisplay())
                        else -> record
                    }
                } else {
                    record
                }
            }
        }
    }

    return result
}

private fun RecentCall.copyWithName(name: String): RecentCall {
    return copy(
        name = name,
        groupedCalls = groupedCalls
            ?.map { it.copy(name = name) }
            ?.ifEmpty { null }
    )
}
