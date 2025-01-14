package org.fossify.phone.fragments

import android.content.Context
import android.util.AttributeSet
import com.google.gson.Gson
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyLinearLayoutManager
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.databinding.FragmentFavoritesBinding
import org.fossify.phone.databinding.FragmentLettersLayoutBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.setupWithContacts
import org.fossify.phone.interfaces.RefreshItemsListener

class FavoritesFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LettersInnerBinding>(context, attributeSet),
    RefreshItemsListener {
    private lateinit var binding: FragmentLettersLayoutBinding
    private var allContacts = ArrayList<Contact>()

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentLettersLayoutBinding.bind(FragmentFavoritesBinding.bind(this).favoritesFragment)
        innerBinding = LettersInnerBinding(binding)
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        binding.fragmentPlaceholder.text = context.getString(placeholderResId)
        binding.fragmentPlaceholder2.beGone()
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.apply {
            fragmentPlaceholder.setTextColor(textColor)
            (fragmentList.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)

            letterFastscroller.textColor = textColor.getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts = contacts

            if (SMT_PRIVATE !in context.baseConfig.ignoredContactSources) {
                val privateCursor = context?.getMyContactsCursor(favoritesOnly = true, withPhoneNumbersOnly = true)
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor).map {
                    it.copy(starred = 1)
                }
                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }
            }
            val favorites = contacts.filter { it.starred == 1 } as ArrayList<Contact>

            allContacts = if (activity!!.config.isCustomOrderSelected) {
                sortByCustomOrder(favorites)
            } else {
                favorites
            }

            activity?.runOnUiThread {
                gotContacts(allContacts)
                callback?.invoke()
            }
        }
    }

    private fun gotContacts(contacts: ArrayList<Contact>) {
        setupLetterFastScroller(contacts)
        binding.apply {
            if (contacts.isEmpty()) {
                fragmentPlaceholder.beVisible()
                fragmentList.beGone()
            } else {
                fragmentPlaceholder.beGone()
                fragmentList.beVisible()

                updateListAdapter()
            }
        }
    }

    private fun updateListAdapter() {
        val viewType = context.config.viewType
        setViewType(viewType)

        val currAdapter = binding.fragmentList.adapter as ContactsAdapter?
        if (currAdapter == null) {
            ContactsAdapter(
                activity = activity as SimpleActivity,
                contacts = allContacts,
                recyclerView = binding.fragmentList,
                refreshItemsListener = this,
                viewType = viewType,
                showDeleteButton = false,
                enableDrag = true,
            ) { contact ->
                if (context.config.showCallConfirmation) {
                    CallConfirmationDialog(activity as SimpleActivity, (contact as Contact).getNameToDisplay()) {
                        activity?.apply {
                            initiateCall(contact) { launchCallIntent(it) }
                        }
                    }
                } else {
                    activity?.apply {
                        initiateCall(contact as Contact) { launchCallIntent(it) }
                    }
                }
            }.apply {
                binding.fragmentList.adapter = this

                onDragEndListener = {
                    val adapter = binding.fragmentList.adapter
                    if (adapter is ContactsAdapter) {
                        val items = adapter.contacts
                        saveCustomOrderToPrefs(items)
                        setupLetterFastScroller(items)
                    }
                }

                onSpanCountListener = { newSpanCount ->
                    context.config.contactsGridColumnCount = newSpanCount
                }
            }

            if (context.areSystemAnimationsEnabled) {
                binding.fragmentList.scheduleLayoutAnimation()
            }
        } else {
            currAdapter.viewType = viewType
            currAdapter.updateItems(allContacts)
        }
    }

    fun columnCountChanged() {
        (binding.fragmentList.layoutManager as? MyGridLayoutManager)?.spanCount = 
          context!!.config.contactsGridColumnCount
        binding.fragmentList.adapter?.apply {
            notifyItemRangeChanged(0, allContacts.size)
        }
    }

    private fun sortByCustomOrder(favorites: List<Contact>): ArrayList<Contact> {
        val favoritesOrder = activity!!.config.favoritesContactsOrder

        if (favoritesOrder.isEmpty()) {
            return ArrayList(favorites)
        }

        val orderList = Converters().jsonToStringList(favoritesOrder)
        val map = orderList.withIndex().associate { it.value to it.index }
        val sorted = favorites.sortedBy { map[it.contactId.toString()] }

        return ArrayList(sorted)
    }

    private fun saveCustomOrderToPrefs(items: List<Contact>) {
        activity?.apply {
            val orderIds = items.map { it.contactId }
            val orderGsonString = Gson().toJson(orderIds)
            config.favoritesContactsOrder = orderGsonString
        }
    }

    private fun setupLetterFastScroller(contacts: List<Contact>) {
        binding.letterFastscroller.setupWithContacts(binding.fragmentList, contacts)
    }

    override fun onSearchClosed() {
        binding.fragmentPlaceholder.beVisibleIf(allContacts.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(allContacts)
        setupLetterFastScroller(allContacts)
    }

    override fun onSearchQueryChanged(text: String) {
        val fixedText = text.trim().replace("\\s+".toRegex(), " ")
        val contacts = allContacts.filter {
            it.name.contains(fixedText, true) || (text.toIntOrNull() != null && it.doesContainPhoneNumber(fixedText))
        }.sortedByDescending {
            it.name.startsWith(fixedText, true)
        }.toMutableList() as ArrayList<Contact>

        binding.fragmentPlaceholder.beVisibleIf(contacts.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(contacts, fixedText)
        setupLetterFastScroller(contacts)
    }

    private fun setViewType(viewType: Int) {
        val spanCount = context.config.contactsGridColumnCount

        val layoutManager = if (viewType == VIEW_TYPE_GRID) {
            binding.letterFastscroller.beGone()
            MyGridLayoutManager(context, spanCount)
        } else {
            binding.letterFastscroller.beVisible()
            MyLinearLayoutManager(context)
        }
        binding.fragmentList.layoutManager = layoutManager
    }
}
