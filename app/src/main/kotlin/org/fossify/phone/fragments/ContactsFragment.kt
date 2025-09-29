package org.fossify.phone.fragments

import android.content.Context
import android.util.AttributeSet
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColorStateList
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.getProperText
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.databinding.FragmentContactsBinding
import org.fossify.phone.databinding.FragmentLettersLayoutBinding
import org.fossify.phone.extensions.launchCreateNewContactIntent
import org.fossify.phone.extensions.setupWithContacts
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.interfaces.RefreshItemsListener

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LettersInnerBinding>(context, attributeSet),
    RefreshItemsListener {
    private lateinit var binding: FragmentLettersLayoutBinding
    private var allContacts = ArrayList<Contact>()

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentLettersLayoutBinding.bind(FragmentContactsBinding.bind(this).contactsFragment)
        innerBinding = LettersInnerBinding(binding)
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        binding.fragmentPlaceholder.text = context.getString(placeholderResId)

        val placeholderActionResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.create_new_contact
        } else {
            R.string.request_access
        }

        binding.fragmentPlaceholder2.apply {
            text = context.getString(placeholderActionResId)
            underlineText()
            setOnClickListener {
                if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
                    activity?.launchCreateNewContactIntent()
                } else {
                    requestReadContactsPermission()
                }
            }
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.apply {
            (fragmentList.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)
            fragmentPlaceholder.setTextColor(textColor)
            fragmentPlaceholder2.setTextColor(properPrimaryColor)

            letterFastscroller.textColor = textColor.getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        val privateCursor = context?.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts = contacts

            if (SMT_PRIVATE !in context.baseConfig.ignoredContactSources) {
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }
            }
            (activity as MainActivity).cacheContacts()

            activity?.runOnUiThread {
                gotContacts(contacts)
                callback?.invoke()
            }
        }
    }

    private fun gotContacts(contacts: ArrayList<Contact>) {
        setupLetterFastScroller(contacts)
        if (contacts.isEmpty()) {
            binding.apply {
                fragmentPlaceholder.beVisible()
                fragmentPlaceholder2.beVisible()
                fragmentList.beGone()
            }
        } else {
            binding.apply {
                fragmentPlaceholder.beGone()
                fragmentPlaceholder2.beGone()
                fragmentList.beVisible()
            }

            if (binding.fragmentList.adapter == null) {
                ContactsAdapter(
                    activity = activity as SimpleActivity,
                    contacts = contacts,
                    recyclerView = binding.fragmentList,
                    refreshItemsListener = this,
                    itemClick = {
                        activity?.startCallWithConfirmationCheck(it as Contact)
                    },
                    profileIconClick = {
                        activity?.startContactDetailsIntent(it as Contact)
                    }
                ).apply {
                    binding.fragmentList.adapter = this
                }

                if (context.areSystemAnimationsEnabled) {
                    binding.fragmentList.scheduleLayoutAnimation()
                }
            } else {
                (binding.fragmentList.adapter as ContactsAdapter).updateItems(contacts)
            }
        }
    }

    private fun setupLetterFastScroller(contacts: ArrayList<Contact>) {
        binding.letterFastscroller.setupWithContacts(binding.fragmentList, contacts)
    }

    override fun onSearchClosed() {
        binding.fragmentPlaceholder.beVisibleIf(allContacts.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(allContacts)
        setupLetterFastScroller(allContacts)
    }

    override fun onSearchQueryChanged(text: String) {
        val fixedText = text.trim().replace("\\s+".toRegex(), " ")
        val shouldNormalize = fixedText.normalizeString() == fixedText
        val filtered = allContacts.filter { contact ->
            getProperText(contact.getNameToDisplay(), shouldNormalize).contains(fixedText, true) ||
                getProperText(contact.nickname, shouldNormalize).contains(fixedText, true) ||
                (fixedText.toLongOrNull() != null && contact.doesContainPhoneNumber(fixedText, true)) ||
                contact.emails.any { it.value.contains(fixedText, true) } ||
                contact.addresses.any { getProperText(it.value, shouldNormalize).contains(fixedText, true) } ||
                contact.IMs.any { it.value.contains(fixedText, true) } ||
                getProperText(contact.notes, shouldNormalize).contains(fixedText, true) ||
                getProperText(contact.organization.company, shouldNormalize).contains(fixedText, true) ||
                getProperText(contact.organization.jobPosition, shouldNormalize).contains(fixedText, true) ||
                contact.websites.any { it.contains(fixedText, true) }
        } as ArrayList

        filtered.sortBy {
            val nameToDisplay = it.getNameToDisplay()
            !getProperText(nameToDisplay, shouldNormalize).startsWith(fixedText, true) && !nameToDisplay.contains(fixedText, true)
        }

        binding.fragmentPlaceholder.beVisibleIf(filtered.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(filtered, fixedText)
        setupLetterFastScroller(filtered)
    }

    private fun requestReadContactsPermission() {
        activity?.handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                binding.fragmentPlaceholder.text = context.getString(R.string.no_contacts_found)
                binding.fragmentPlaceholder2.text = context.getString(R.string.create_new_contact)
                ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                    activity?.runOnUiThread {
                        gotContacts(contacts)
                    }
                }
            }
        }
    }
}
