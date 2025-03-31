package org.fossify.phone.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.CallLog.Calls
import android.text.SpannableString
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.databinding.ItemContactWithoutNumberBinding
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FeatureLockedDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ItemDialpadHeaderBinding
import org.fossify.phone.databinding.ItemRecentCallBinding
import org.fossify.phone.dialogs.ShowGroupedCallsDialog
import org.fossify.phone.extensions.*
import org.fossify.phone.models.DialpadItem
import org.fossify.phone.models.RecentCall
import java.util.Locale

class DialpadAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    highlightText: String = "",
    itemClick: (Any) -> Unit,
    val profileIconClick: ((Any) -> Unit)? = null
) : MyRecyclerViewListAdapter<DialpadItem>(activity, recyclerView, DialpadItemsDiffCallback(), itemClick) {

    private lateinit var outgoingCallIcon: Drawable
    private lateinit var incomingCallIcon: Drawable
    private lateinit var incomingMissedCallIcon: Drawable
    private var textToHighlight = highlightText
    var fontSize: Float = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private var missedCallColor = resources.getColor(R.color.color_missed_call)
    private var secondaryTextColor = textColor.adjustAlpha(0.6f)
    private var noOverflowIconPadding = resources.getDimension(R.dimen.normal_margin).toInt()
    private var phoneNumberUtilInstance: PhoneNumberUtil = PhoneNumberUtil.getInstance()
    private var phoneNumberOfflineGeocoderInstance: PhoneNumberOfflineGeocoder = PhoneNumberOfflineGeocoder.getInstance()

    init {
        initDrawables()
        setupDragListener(true)
        recyclerView.itemAnimator?.changeDuration = 0
    }

    override fun getActionMenuId() = R.menu.cab_dialpad

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val selectedItems = getSelectedItems()
        val isOneItemSelected = isOneItemSelected()
        val selectedNumber = "tel:${getSelectedPhoneNumber()}"

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && (activity.config.getCustomSIM(selectedNumber) ?: "") != ""

            findItem(R.id.cab_delete).isVisible = selectedItems.all { it.isContact() }
            findItem(R.id.cab_block_number).title = activity.addLockedLabelIfNeeded(R.string.block_number)
            findItem(R.id.cab_block_number).isVisible = isNougatPlus() && selectedItems.all { it.isRecentCall() }
            findItem(R.id.cab_add_number).isVisible = isOneItemSelected && selectedItems.first().isRecentCall()
            findItem(R.id.cab_copy_number).isVisible = isOneItemSelected && selectedItems.first().isRecentCall()
            findItem(R.id.cab_show_call_details).isVisible = isOneItemSelected && selectedItems.first().isRecentCall()
            findItem(R.id.cab_view_details).isVisible = isOneItemSelected && selectedItems.first().isContact()
            findItem(R.id.cab_create_shortcut).title = activity.addLockedLabelIfNeeded(R.string.create_shortcut)
            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected && isOreoPlus() && selectedItems.first().isContact()
            findItem(R.id.cab_block_unblock_contact).isVisible = isOneItemSelected && isNougatPlus() && selectedItems.first().isContact()
            getCabBlockContactTitle { title ->
                findItem(R.id.cab_block_unblock_contact).title = title
            }
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_block_unblock_contact -> tryBlockingUnblocking()
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_block_number -> tryBlocking()
            R.id.cab_add_number -> addNumberToContact()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_show_call_details -> showCallDetails()
            R.id.cab_copy_number -> copyNumber()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_create_shortcut -> tryCreateShortcut()
            R.id.cab_select_all -> selectAll()
            R.id.cab_view_details -> viewContactDetails()
        }
    }

    override fun getSelectableItemCount() = currentList.count { !it.isHeader() }

    override fun getIsItemSelectable(position: Int) = !currentList[position].isHeader()

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.getItemId()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.getItemId() == key }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val currentItem = currentList[position]
        return when {
            currentItem.isHeader() -> VIEW_TYPE_HEADER
            currentItem.isContact() -> VIEW_TYPE_CONTACT
            currentItem.isRecentCall() -> VIEW_TYPE_CALL
            else -> VIEW_TYPE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                ItemDialpadHeaderBinding.inflate(layoutInflater, parent, false)
            )

            VIEW_TYPE_CONTACT -> ContactViewHolder(
                ContactBinding.ItemContact.inflate(layoutInflater, parent, false)
            )

            VIEW_TYPE_CALL -> RecentCallViewHolder(
                ItemRecentCallBinding.inflate(layoutInflater, parent, false)
            )

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }

        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dialpadItem = currentList[position]
        when (holder) {
            is HeaderViewHolder -> dialpadItem.header?.let { holder.bind(it) }
            is ContactViewHolder -> holder.bind(dialpadItem)
            is RecentCallViewHolder -> holder.bind(dialpadItem)
        }

        bindViewHolder(holder)
    }

    private fun getCabBlockContactTitle(callback: (String) -> Unit) {
        val contact = getSelectedItems().firstOrNull()?.contact ?: return callback("")

        activity.isContactBlocked(contact) { blocked ->
            val cabItemTitleRes = if (blocked) {
                R.string.unblock_contact
            } else {
                R.string.block_contact
            }

            callback(activity.addLockedLabelIfNeeded(cabItemTitleRes))
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            if (holder is RecentCallViewHolder) {
                Glide.with(activity).clear(holder.binding.itemRecentsImage)
            } else if (holder is ContactViewBinding) {
                ContactBinding.ItemContact.bind(holder.itemView).apply {
                    Glide.with(activity).clear(itemContactImage)
                }
            }
        }
    }

    override fun submitList(list: List<DialpadItem>?) {
        val layoutManager = recyclerView.layoutManager!!
        val recyclerViewState = layoutManager.onSaveInstanceState()
        super.submitList(list) {
            layoutManager.onRestoreInstanceState(recyclerViewState)
        }
    }

    fun initDrawables() {
        val theme = activity.theme
        missedCallColor = resources.getColor(R.color.color_missed_call, theme)
        secondaryTextColor = textColor.adjustAlpha(0.6f)

        val outgoingCallColor = resources.getColor(R.color.color_outgoing_call, theme)
        val incomingCallColor = resources.getColor(R.color.color_incoming_call, theme)
        outgoingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_call_made_vector, outgoingCallColor)
        incomingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_call_received_vector, incomingCallColor)
        incomingMissedCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_call_missed_vector, missedCallColor)
    }

    private fun callContact(useSimOne: Boolean) {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        val selectedItem = getSelectedItems().first()

        if (selectedItem.isContact()) {
            activity.callContactWithSim(phoneNumber, useSimOne)
        } else if (selectedItem.isRecentCall()) {
            val name = getSelectedName() ?: return

            activity.callContactWithSimWithConfirmationCheck(phoneNumber, name, useSimOne)
        }
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.config.removeCustomSIM("tel:$phoneNumber")
        finishActMode()
    }

    private fun tryBlocking() {
        if (activity.isOrWasThankYouInstalled()) {
            askConfirmBlock()
        } else {
            FeatureLockedDialog(activity) { }
        }
    }

    private fun askConfirmBlock() {
        val callsToBlock = getSelectedItems()

        if (callsToBlock.isEmpty() || callsToBlock.any { !it.isRecentCall() }) {
            return
        }

        val numbers = TextUtils.join(", ", callsToBlock.distinctBy { it.recentCall!!.phoneNumber }.map { it.recentCall!!.phoneNumber })
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), numbers)

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        val callsToBlock = getSelectedItems()

        if (callsToBlock.isEmpty() || callsToBlock.any { !it.isRecentCall() }) {
            return
        }

        ensureBackgroundThread {
            callsToBlock.map { it.recentCall!!.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            val newItems = currentList.toMutableList().also { it.removeAll(callsToBlock) }
            activity.runOnUiThread {
                submitList(newItems)
                finishActMode()
            }
        }
    }

    private fun addNumberToContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            activity.launchActivityIntent(this)
        }
    }

    private fun sendSMS() {
        val numbers = ArrayList<String>()

        for (selectedItem in getSelectedItems()) {
            if (selectedItem.isContact()) {
                val contactNumbers = selectedItem.contact!!.phoneNumbers
                val primaryNumber = contactNumbers.firstOrNull { it.isPrimary }
                val normalizedNumber = primaryNumber?.normalizedNumber ?: contactNumbers.firstOrNull()?.normalizedNumber

                if (normalizedNumber != null) {
                    numbers.add(normalizedNumber)
                }
            } else if (selectedItem.isRecentCall()) {
                numbers.add(selectedItem.recentCall!!.phoneNumber)
            }
        }

        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntent(recipient)
    }

    private fun showCallDetails() {
        val recentCall = getSelectedItems().firstOrNull()?.recentCall ?: return
        val recentCalls = recentCall.groupedCalls ?: listOf(recentCall)
        ShowGroupedCallsDialog(activity, recentCalls)
    }

    private fun copyNumber() {
        val recentCall = getSelectedItems().firstOrNull()?.recentCall ?: return
        activity.copyToClipboard(recentCall.phoneNumber)
        finishActMode()
    }

    private fun askConfirmDelete() {
        val selectedItems = getSelectedItems().filter { it.isContact() }

        if (selectedItems.isEmpty()) {
            return
        }

        val itemsCnt = selectedItems.size
        val firstItem = selectedItems.first().contact!!
        val items = if (itemsCnt == 1) {
            "\"${firstItem.getNameToDisplay()}\""
        } else {
            resources.getQuantityString(R.plurals.delete_contacts, itemsCnt, itemsCnt)
        }

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            activity.handlePermission(PERMISSION_WRITE_CONTACTS) {
                deleteContacts()
            }
        }
    }

    private fun deleteContacts() {
        val contactsToRemove = getSelectedItems()

        if (contactsToRemove.isEmpty() || contactsToRemove.any { !it.isContact() }) {
            return
        }

        val newItems = currentList.toMutableList().also { it.removeAll(contactsToRemove) }
        val contactIdsToRemove = contactsToRemove.map { it.contact!!.rawId }.toMutableList() as ArrayList<Int>

        SimpleContactsHelper(activity).deleteContactRawIDs(contactIdsToRemove) {
            activity.runOnUiThread {
                submitList(newItems)
                finishActMode()
            }
        }
    }

    private fun tryCreateShortcut() {
        if (activity.isOrWasThankYouInstalled()) {
            createShortcut()
        } else {
            FeatureLockedDialog(activity) { }
        }
    }

    private fun viewContactDetails() {
        val contact = getSelectedItems().firstOrNull()?.contact ?: return
        activity.startContactDetailsIntent(contact)
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val contact = getSelectedItems().firstOrNull()?.contact ?: return
        val manager = activity.shortcutManager
        if (manager.isRequestPinShortcutSupported) {
            SimpleContactsHelper(activity).getShortcutImage(contact.photoUri, contact.getNameToDisplay()) { image ->
                activity.runOnUiThread {
                    activity.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                        val intent = Intent(action).apply {
                            data = Uri.fromParts("tel", getSelectedPhoneNumber(), null)
                        }

                        val shortcut = ShortcutInfo.Builder(activity, contact.hashCode().toString())
                            .setShortLabel(contact.getNameToDisplay())
                            .setIcon(Icon.createWithBitmap(image))
                            .setIntent(intent)
                            .build()

                        manager.requestPinShortcut(shortcut, null)
                    }
                }
            }
        }
    }

    private fun tryBlockingUnblocking() {
        val contact = getSelectedItems().firstOrNull()?.contact ?: return

        if (activity.isOrWasThankYouInstalled()) {
            activity.isContactBlocked(contact) { blocked ->
                if (blocked) {
                    tryUnblocking(contact)
                } else {
                    tryBlocking(contact)
                }
            }
        } else {
            FeatureLockedDialog(activity) { }
        }
    }

    private fun tryBlocking(contact: Contact) {
        askConfirmBlock(contact) { contactBlocked ->
            val resultMsg = if (contactBlocked) {
                R.string.block_contact_success
            } else {
                R.string.block_contact_fail
            }

            activity.toast(resultMsg)
            finishActMode()
        }
    }

    private fun tryUnblocking(contact: Contact) {
        val contactUnblocked = activity.unblockContact(contact)
        val resultMsg = if (contactUnblocked) {
            R.string.unblock_contact_success
        } else {
            R.string.unblock_contact_fail
        }

        activity.toast(resultMsg)
        finishActMode()
    }

    private fun askConfirmBlock(contact: Contact, callback: (Boolean) -> Unit) {
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), contact.name)

        ConfirmationDialog(activity, question) {
            val contactBlocked = activity.blockContact(contact)
            callback(contactBlocked)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<DialpadItem>, highlightText: String = "") {
        if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            submitList(newItems)
            notifyDataSetChanged()
            finishActMode()
        } else {
            submitList(newItems)
        }
    }

    private fun getSelectedItems() = currentList.filter { selectedKeys.contains(it.getItemId()) }

    private fun getSelectedPhoneNumber(): String? {
        val firstSelectedItem = getSelectedItems().firstOrNull()

        return when (firstSelectedItem?.itemType) {
            DialpadItem.DialpadItemType.HEADER -> null
            DialpadItem.DialpadItemType.CONTACT -> firstSelectedItem.contact!!.getPrimaryNumber()
            DialpadItem.DialpadItemType.RECENTCALL -> firstSelectedItem.recentCall!!.phoneNumber
            null -> null
        }
    }

    private fun getSelectedName() = getSelectedItems().firstOrNull()?.recentCall?.name

    private inner class HeaderViewHolder(val binding: ItemDialpadHeaderBinding) : ViewHolder(binding.root) {
        fun bind(header: String) {
            binding.headerTextView.apply {
                setTextColor(secondaryTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.76f)
                text = header
            }
        }
    }

    private inner class ContactViewHolder(val binding: ContactViewBinding) : ViewHolder(binding.root) {
        fun bind(item: DialpadItem) = bindView(
            item = item,
            allowSingleClick = true,
            allowLongClick = true
        ) { _, _ ->
            binding.apply {
                root.setupViewBackground(activity)

                val contact: Contact = item.contact!!

                itemContactFrame.isSelected = selectedKeys.contains(contact.rawId)

                itemContactImage.apply {
                    if (profileIconClick != null) {
                        setBackgroundResource(R.drawable.selector_clickable_circle)

                        setOnClickListener {
                            if (!actModeCallback.isSelectable) {
                                profileIconClick.invoke(item)
                            } else {
                                viewClicked(item)
                            }
                        }
                        setOnLongClickListener {
                            viewLongClicked()
                            true
                        }
                    }
                }

                itemContactName.apply {
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                    val name = contact.getNameToDisplay()
                    text = if (textToHighlight.isEmpty()) {
                        name
                    } else {
                        if (name.contains(textToHighlight, true)) {
                            name.highlightTextPart(textToHighlight, properPrimaryColor)
                        } else {
                            var spacedTextToHighlight = textToHighlight
                            val strippedName = name.filterNot { it.isWhitespace() }
                            val strippedDigits = KeypadHelper.convertKeypadLettersToDigits(strippedName)
                            val startIndex = strippedDigits.indexOf(textToHighlight)

                            if (strippedDigits.contains(textToHighlight)) {
                                for (i in spacedTextToHighlight.indices) {
                                    if (startIndex + i < name.length && name[startIndex + i].isWhitespace()) {
                                        spacedTextToHighlight = spacedTextToHighlight.replaceRange(i, i, " ")
                                    }
                                }
                            }

                            name.highlightTextFromNumbers(spacedTextToHighlight, properPrimaryColor)
                        }
                    }
                }

                dragHandleIcon.apply {
                    beGone()
                    setOnTouchListener(null)
                }

                if (!activity.isDestroyed) {
                    SimpleContactsHelper(root.context).loadContactImage(contact.photoUri, itemContactImage, contact.getNameToDisplay())
                }
            }
        }
    }

    private sealed interface ContactBinding {
        fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ContactViewBinding

        fun bind(view: View): ContactViewBinding

        data object ItemContact : ContactBinding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ContactViewBinding {
                return ContactBindingAdapter(ItemContactWithoutNumberBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ContactViewBinding {
                return ContactBindingAdapter(ItemContactWithoutNumberBinding.bind(view))
            }
        }
    }

    private interface ContactViewBinding : ViewBinding {
        val itemContactName: TextView
        val itemContactImage: ImageView
        val itemContactFrame: ConstraintLayout
        val dragHandleIcon: ImageView
    }

    private class ContactBindingAdapter(val binding: ItemContactWithoutNumberBinding) : ContactViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon

        override fun getRoot(): View = binding.root
    }

    private inner class RecentCallViewHolder(val binding: ItemRecentCallBinding) : ViewHolder(binding.root) {
        fun bind(item: DialpadItem) = bindView(
            item = item,
            allowSingleClick = !item.recentCall!!.isUnknownNumber,
            allowLongClick = !item.recentCall.isUnknownNumber
        ) { _, _ ->
            binding.apply {
                root.setupViewBackground(activity)

                val recentCall: RecentCall = item.recentCall

                val currentFontSize = fontSize
                itemRecentsHolder.isSelected = selectedKeys.contains(recentCall.id)
                val name = /*findContactByCall(recentCall)?.getNameToDisplay() ?:*/ recentCall.name
                val formatPhoneNumbers = activity.config.formatPhoneNumbers
                var nameToShow = if (name == recentCall.phoneNumber && formatPhoneNumbers) {
                    SpannableString(name.formatPhoneNumber())
                } else {
                    SpannableString(name)
                }
                val shouldShowDuration = recentCall.type != Calls.MISSED_TYPE && recentCall.type != Calls.REJECTED_TYPE && recentCall.duration > 0

                if (recentCall.specificType.isNotEmpty()) {
                    nameToShow = SpannableString("$name - ${recentCall.specificType}")

                    nameToShow = if (formatPhoneNumbers) {
                        SpannableString("$name - ${recentCall.specificType}, ${recentCall.specificNumber.formatPhoneNumber()}")
                    } else {
                        SpannableString("$name - ${recentCall.specificType}, ${recentCall.specificNumber}")
                    }
                }

                if (recentCall.groupedCalls != null) {
                    nameToShow = SpannableString("$nameToShow (${recentCall.groupedCalls.size})")
                }

                if (textToHighlight.isNotEmpty() && nameToShow.contains(textToHighlight, true)) {
                    nameToShow = SpannableString(nameToShow.toString().highlightTextPart(textToHighlight, properPrimaryColor))
                }

                itemRecentsName.apply {
                    text = nameToShow
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize)
                }

                itemRecentsDateTime.apply {
                    text = recentCall.startTS.formatDateOrTime(context, hideTimeOnOtherDays = false, showCurrentYear = false, hideTodaysDate = false)

                    setTextColor(if (recentCall.type == Calls.MISSED_TYPE) missedCallColor else secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                }

                itemRecentsDateTimeDurationSeparator.apply {
                    text = "•"
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                    setTextColor(textColor)
                    beVisibleIf(shouldShowDuration)
                }

                itemRecentsDuration.apply {
                    text = context.formatSecondsToShortTimeString(recentCall.duration)
                    setTextColor(textColor)
                    beVisibleIf(shouldShowDuration)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                }

                itemRecentsLocation.apply {
                    val locale = Locale.getDefault()
                    val defaultCountryCode = locale.country
                    val phoneNumber = try {
                        phoneNumberUtilInstance
                            .parse(recentCall.phoneNumber, defaultCountryCode)
                    } catch (_: NumberParseException) {
                        null
                    }

                    val location = if (phoneNumber != null) {
                        phoneNumberOfflineGeocoderInstance
                            .getDescriptionForNumber(phoneNumber, locale, defaultCountryCode)
                    } else {
                        null
                    }

                    text = location
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                    beVisibleIf(
                        phoneNumber != null
                            && phoneNumber.countryCodeSource != Phonenumber.PhoneNumber.CountryCodeSource.FROM_DEFAULT_COUNTRY
                    )
                    setPadding(0, 0, noOverflowIconPadding, 0)
                }

                itemRecentsSimImage.beVisibleIf(areMultipleSIMsAvailable && recentCall.simID != -1)
                itemRecentsSimId.beVisibleIf(areMultipleSIMsAvailable && recentCall.simID != -1)
                if (areMultipleSIMsAvailable && recentCall.simID != -1) {
                    itemRecentsSimImage.applyColorFilter(textColor)
                    itemRecentsSimId.setTextColor(textColor.getContrastColor())
                    itemRecentsSimId.text = recentCall.simID.toString()
                }

                SimpleContactsHelper(root.context).loadContactImage(recentCall.photoUri, itemRecentsImage, recentCall.name)

                itemRecentsImage.apply {
                    if (profileIconClick != null) {
                        setBackgroundResource(R.drawable.selector_clickable_circle)

                        setOnClickListener {
                            if (!actModeCallback.isSelectable) {
                                profileIconClick.invoke(item)
                            } else {
                                viewClicked(item)
                            }
                        }
                        setOnLongClickListener {
                            viewLongClicked()
                            true
                        }
                    }
                }

                val drawable = when (recentCall.type) {
                    Calls.OUTGOING_TYPE -> outgoingCallIcon
                    Calls.MISSED_TYPE -> incomingMissedCallIcon
                    else -> incomingCallIcon
                }

                itemRecentsType.setImageDrawable(drawable)

                overflowMenuIcon.beGone()
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONTACT = 1
        private const val VIEW_TYPE_CALL = 2
    }
}

class DialpadItemsDiffCallback : DiffUtil.ItemCallback<DialpadItem>() {

    override fun areItemsTheSame(oldItem: DialpadItem, newItem: DialpadItem): Boolean {
        if (oldItem.isHeader() != newItem.isHeader() || oldItem.isContact() != newItem.isContact() || oldItem.isRecentCall() != newItem.isRecentCall()) {
            return false
        }

        return when (oldItem.itemType) {
            DialpadItem.DialpadItemType.HEADER -> oldItem.header == newItem.header
            DialpadItem.DialpadItemType.CONTACT -> oldItem.contact == newItem.contact
            DialpadItem.DialpadItemType.RECENTCALL -> oldItem.getItemId() == newItem.getItemId()
        }
    }

    override fun areContentsTheSame(oldItem: DialpadItem, newItem: DialpadItem): Boolean {
        if (oldItem.isHeader() != newItem.isHeader() || oldItem.isContact() != newItem.isContact() || oldItem.isRecentCall() != newItem.isRecentCall()) {
            return false
        }

        return when (oldItem.itemType) {
            DialpadItem.DialpadItemType.HEADER -> oldItem.header == newItem.header
            DialpadItem.DialpadItemType.CONTACT -> {
                oldItem.contact?.prefix == newItem.contact?.prefix &&
                    oldItem.contact?.firstName == newItem.contact?.firstName &&
                    oldItem.contact?.middleName == newItem.contact?.middleName &&
                    oldItem.contact?.surname == newItem.contact?.surname &&
                    oldItem.contact?.suffix == newItem.contact?.suffix &&
                    oldItem.contact?.nickname == newItem.contact?.nickname &&
                    oldItem.contact?.photoUri == newItem.contact?.photoUri &&
                    oldItem.contact?.phoneNumbers == newItem.contact?.phoneNumbers &&
                    oldItem.contact?.emails == newItem.contact?.emails &&
                    oldItem.contact?.addresses == newItem.contact?.addresses &&
                    oldItem.contact?.events == newItem.contact?.events &&
                    oldItem.contact?.source == newItem.contact?.source &&
                    oldItem.contact?.starred == newItem.contact?.starred &&
                    oldItem.contact?.contactId == newItem.contact?.contactId &&
                    oldItem.contact?.thumbnailUri == newItem.contact?.thumbnailUri &&
                    (oldItem.contact?.photo?.sameAs(newItem.contact?.photo) ?: true) &&
                    oldItem.contact?.notes == newItem.contact?.notes &&
                    oldItem.contact?.groups == newItem.contact?.groups &&
                    oldItem.contact?.organization == newItem.contact?.organization &&
                    oldItem.contact?.websites == newItem.contact?.websites &&
                    oldItem.contact?.IMs == newItem.contact?.IMs &&
                    oldItem.contact?.mimetype == newItem.contact?.mimetype &&
                    oldItem.contact?.ringtone == newItem.contact?.ringtone
            }

            DialpadItem.DialpadItemType.RECENTCALL -> {
                oldItem.recentCall?.phoneNumber == newItem.recentCall?.phoneNumber &&
                    oldItem.recentCall?.name == newItem.recentCall?.name &&
                    oldItem.recentCall?.photoUri == newItem.recentCall?.photoUri &&
                    oldItem.recentCall?.startTS == newItem.recentCall?.startTS &&
                    oldItem.recentCall?.duration == newItem.recentCall?.duration &&
                    oldItem.recentCall?.type == newItem.recentCall?.type &&
                    oldItem.recentCall?.simID == newItem.recentCall?.simID &&
                    oldItem.recentCall?.specificNumber == newItem.recentCall?.specificNumber &&
                    oldItem.recentCall?.specificType == newItem.recentCall?.specificType &&
                    oldItem.recentCall?.isUnknownNumber == newItem.recentCall?.isUnknownNumber &&
                    oldItem.recentCall?.groupedCalls?.size == newItem.recentCall?.groupedCalls?.size
            }
        }
    }
}
