package org.fossify.phone.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.CallLog.Calls
import android.text.SpannableString
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FeatureLockedDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ItemRecentCallBinding
import org.fossify.phone.databinding.ItemRecentsDateBinding
import org.fossify.phone.dialogs.ShowGroupedCallsDialog
import org.fossify.phone.extensions.*
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.interfaces.RefreshItemsListener
import org.fossify.phone.models.CallLogItem
import org.fossify.phone.models.RecentCall
import org.joda.time.DateTime

class RecentCallsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    private val refreshItemsListener: RefreshItemsListener?,
    private val showOverflowMenu: Boolean,
    private val itemDelete: (List<RecentCall>) -> Unit = {},
    itemClick: (Any) -> Unit,
) : MyRecyclerViewListAdapter<CallLogItem>(activity, recyclerView, RecentCallsDiffCallback(), itemClick) {

    private lateinit var outgoingCallIcon: Drawable
    private lateinit var incomingCallIcon: Drawable
    private lateinit var incomingMissedCallIcon: Drawable
    var fontSize: Float = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private var missedCallColor = resources.getColor(R.color.color_missed_call)
    private var secondaryTextColor = textColor.adjustAlpha(0.6f)
    private var textToHighlight = ""
    private var durationPadding = resources.getDimension(R.dimen.normal_margin).toInt()

    init {
        initDrawables()
        setupDragListener(true)
        setHasStableIds(true)
        recyclerView.itemAnimator?.changeDuration = 0
    }

    override fun getActionMenuId() = R.menu.cab_recent_calls

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val selectedItems = getSelectedItems()
        val isOneItemSelected = selectedItems.size == 1
        val selectedNumber = "tel:${getSelectedPhoneNumber()}"

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && (activity.config.getCustomSIM(selectedNumber) ?: "") != ""

            findItem(R.id.cab_block_number).title = activity.addLockedLabelIfNeeded(R.string.block_number)
            findItem(R.id.cab_block_number).isVisible = isNougatPlus()
            findItem(R.id.cab_add_number).isVisible = isOneItemSelected
            findItem(R.id.cab_copy_number).isVisible = isOneItemSelected
            findItem(R.id.cab_show_call_details).isVisible = isOneItemSelected
            findItem(R.id.cab_view_details).isVisible = isOneItemSelected && findContactByCall(selectedItems.first()) != null
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_block_number -> tryBlocking()
            R.id.cab_add_number -> addNumberToContact()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_show_call_details -> showCallDetails()
            R.id.cab_copy_number -> copyNumber()
            R.id.cab_remove -> askConfirmRemove()
            R.id.cab_select_all -> selectAll()
            R.id.cab_view_details -> launchContactDetailsIntent(findContactByCall(getSelectedItems().first()))
        }
    }

    override fun getItemId(position: Int) = currentList[position].getItemId().toLong()

    override fun getSelectableItemCount() = currentList.filterIsInstance<RecentCall>().size

    override fun getIsItemSelectable(position: Int) = currentList[position] is RecentCall

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.getItemId()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.getItemId() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun getItemViewType(position: Int): Int {
        return when (currentList[position]) {
            is CallLogItem.Date -> VIEW_TYPE_DATE
            is RecentCall -> VIEW_TYPE_CALL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = when (viewType) {
            VIEW_TYPE_DATE -> RecentCallDateViewHolder(
                ItemRecentsDateBinding.inflate(layoutInflater, parent, false)
            )

            VIEW_TYPE_CALL -> RecentCallViewHolder(
                ItemRecentCallBinding.inflate(layoutInflater, parent, false)
            )

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }

        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val callRecord = currentList[position]
        when (holder) {
            is RecentCallDateViewHolder -> holder.bind(callRecord as CallLogItem.Date)
            is RecentCallViewHolder -> holder.bind(callRecord as RecentCall)
        }

        bindViewHolder(holder)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            if (holder is RecentCallViewHolder) {
                Glide.with(activity).clear(holder.binding.itemRecentsImage)
            }
        }
    }

    override fun submitList(list: List<CallLogItem>?) {
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
        val name = getSelectedName() ?: return

        activity.callContactWithSimWithConfirmationCheck(phoneNumber, name, useSimOne)
    }

    private fun callContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        val name = getSelectedName() ?: return

        (activity as SimpleActivity).startCallWithConfirmationCheck(phoneNumber, name)
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
        val numbers = TextUtils.join(", ", getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber })
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), numbers)

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToBlock = getSelectedItems()
        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            val recentCalls = currentList.toMutableList().also { it.removeAll(callsToBlock) }
            activity.runOnUiThread {
                submitList(recentCalls)
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
        val numbers = getSelectedItems().map { it.phoneNumber }
        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntent(recipient)
    }

    private fun showCallDetails() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        val recentCalls = recentCall.groupedCalls ?: listOf(recentCall)
        ShowGroupedCallsDialog(activity, recentCalls)
    }

    private fun copyNumber() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(recentCall.phoneNumber)
        finishActMode()
    }

    private fun askConfirmRemove() {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecents()
            }
        }
    }

    private fun removeRecents() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToRemove = getSelectedItems()
        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.groupedCalls?.mapTo(idsToRemove) { call -> call.id }
        }

        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
            itemDelete(callsToRemove)
            val recentCalls = currentList.toMutableList().also { it.removeAll(callsToRemove) }
            activity.runOnUiThread {
                refreshItemsListener?.refreshItems()
                submitList(recentCalls)
                finishActMode()
            }
        }
    }

    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return (activity as MainActivity).cachedContacts.find { it.name == recentCall.name && it.doesHavePhoneNumber(recentCall.phoneNumber) }
    }

    private fun launchContactDetailsIntent(contact: Contact?) {
        if (contact != null) {
            activity.startContactDetailsIntent(contact)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<CallLogItem>, highlightText: String = "") {
        if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            submitList(newItems)
            notifyDataSetChanged()
            finishActMode()
        } else {
            submitList(newItems)
        }
    }

    private fun getSelectedItems() = currentList.filterIsInstance<RecentCall>()
        .filter { selectedKeys.contains(it.getItemId()) }

    private fun getSelectedPhoneNumber() = getSelectedItems().firstOrNull()?.phoneNumber

    private fun getSelectedName() = getSelectedItems().firstOrNull()?.name

    private fun showPopupMenu(view: View, call: RecentCall) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)
        val contact = findContactByCall(call)
        val selectedNumber = "tel:${call.phoneNumber}"

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(R.menu.menu_recent_item_options)
            menu.apply {
                val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
                findItem(R.id.cab_call).isVisible = !areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_call_sim_1).isVisible = areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_call_sim_2).isVisible = areMultipleSIMsAvailable && !call.isUnknownNumber
                findItem(R.id.cab_send_sms).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_view_details).isVisible = contact != null && !call.isUnknownNumber
                findItem(R.id.cab_add_number).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_copy_number).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_show_call_details).isVisible = !call.isUnknownNumber
                findItem(R.id.cab_block_number).title = activity.addLockedLabelIfNeeded(R.string.block_number)
                findItem(R.id.cab_block_number).isVisible = isNougatPlus() && !call.isUnknownNumber
                findItem(R.id.cab_remove_default_sim).isVisible = (activity.config.getCustomSIM(selectedNumber) ?: "") != "" && !call.isUnknownNumber
            }

            setOnMenuItemClickListener { item ->
                val callId = call.id
                when (item.itemId) {
                    R.id.cab_call -> {
                        executeItemMenuOperation(callId) {
                            callContact()
                        }
                    }

                    R.id.cab_call_sim_1 -> {
                        executeItemMenuOperation(callId) {
                            callContact(true)
                        }
                    }

                    R.id.cab_call_sim_2 -> {
                        executeItemMenuOperation(callId) {
                            callContact(false)
                        }
                    }

                    R.id.cab_send_sms -> {
                        executeItemMenuOperation(callId) {
                            sendSMS()
                        }
                    }

                    R.id.cab_view_details -> {
                        executeItemMenuOperation(callId) {
                            launchContactDetailsIntent(contact)
                        }
                    }

                    R.id.cab_add_number -> {
                        executeItemMenuOperation(callId) {
                            addNumberToContact()
                        }
                    }

                    R.id.cab_show_call_details -> {
                        executeItemMenuOperation(callId) {
                            showCallDetails()
                        }
                    }

                    R.id.cab_block_number -> {
                        selectedKeys.add(callId)
                        tryBlocking()
                    }

                    R.id.cab_remove -> {
                        selectedKeys.add(callId)
                        askConfirmRemove()
                    }

                    R.id.cab_copy_number -> {
                        executeItemMenuOperation(callId) {
                            copyNumber()
                        }
                    }

                    R.id.cab_remove_default_sim -> {
                        executeItemMenuOperation(callId) {
                            removeDefaultSIM()
                        }
                    }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(callId: Int, callback: () -> Unit) {
        selectedKeys.add(callId)
        callback()
        selectedKeys.remove(callId)
    }

    private inner class RecentCallViewHolder(val binding: ItemRecentCallBinding) : ViewHolder(binding.root) {
        fun bind(call: RecentCall) = bindView(
            item = call,
            allowSingleClick = refreshItemsListener != null && !call.isUnknownNumber,
            allowLongClick = refreshItemsListener != null && !call.isUnknownNumber
        ) { _, _ ->
            binding.apply {
                root.setupViewBackground(activity)

                val currentFontSize = fontSize
                itemRecentsHolder.isSelected = selectedKeys.contains(call.id)
                val name = findContactByCall(call)?.getNameToDisplay() ?: call.name
                val formatPhoneNumbers = activity.config.formatPhoneNumbers
                var nameToShow = if (name == call.phoneNumber && formatPhoneNumbers) {
                    SpannableString(name.formatPhoneNumber())
                } else {
                    SpannableString(name)
                }

                if (call.specificType.isNotEmpty()) {
                    nameToShow = SpannableString("$name - ${call.specificType}")

                    // show specific number at "Show call details" dialog too
                    if (refreshItemsListener == null) {
                        nameToShow = if (formatPhoneNumbers) {
                            SpannableString("$name - ${call.specificType}, ${call.specificNumber.formatPhoneNumber()}")
                        } else {
                            SpannableString("$name - ${call.specificType}, ${call.specificNumber}")
                        }
                    }
                }

                if (call.groupedCalls != null) {
                    nameToShow = SpannableString("$nameToShow (${call.groupedCalls.size})")
                }

                if (textToHighlight.isNotEmpty() && nameToShow.contains(textToHighlight, true)) {
                    nameToShow = SpannableString(nameToShow.toString().highlightTextPart(textToHighlight, properPrimaryColor))
                }

                itemRecentsImage.beVisibleIf(activity.config.showContactThumbnails)

                itemRecentsName.apply {
                    text = nameToShow
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize)
                }

                itemRecentsDateTime.apply {
                    text = if (refreshItemsListener == null) {
                        call.startTS.formatDateOrTime(context, hideTimeOnOtherDays = false, showCurrentYear = false, hideTodaysDate = false)
                    } else {
                        call.startTS.formatTime(activity)
                    }

                    setTextColor(if (call.type == Calls.MISSED_TYPE) missedCallColor else secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                }

                itemRecentsDuration.apply {
                    text = call.duration.getFormattedDuration()
                    setTextColor(textColor)
                    beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, currentFontSize * 0.8f)
                    if (!showOverflowMenu) {
                        itemRecentsDuration.setPadding(0, 0, durationPadding, 0)
                    }
                }

                itemRecentsSimImage.beVisibleIf(areMultipleSIMsAvailable && call.simID != -1)
                itemRecentsSimId.beVisibleIf(areMultipleSIMsAvailable && call.simID != -1)
                if (areMultipleSIMsAvailable && call.simID != -1) {
                    itemRecentsSimImage.applyColorFilter(textColor)
                    itemRecentsSimId.setTextColor(textColor.getContrastColor())
                    itemRecentsSimId.text = call.simID.toString()
                }

                SimpleContactsHelper(root.context).loadContactImage(call.photoUri, itemRecentsImage, call.name)

                val drawable = when (call.type) {
                    Calls.OUTGOING_TYPE -> outgoingCallIcon
                    Calls.MISSED_TYPE -> incomingMissedCallIcon
                    else -> incomingCallIcon
                }

                itemRecentsType.setImageDrawable(drawable)

                overflowMenuIcon.beVisibleIf(showOverflowMenu)
                overflowMenuIcon.drawable.apply {
                    mutate()
                    setTint(activity.getProperTextColor())
                }

                overflowMenuIcon.setOnClickListener {
                    showPopupMenu(overflowMenuAnchor, call)
                }
            }
        }
    }

    private inner class RecentCallDateViewHolder(val binding: ItemRecentsDateBinding) : ViewHolder(binding.root) {
        fun bind(date: CallLogItem.Date) {
            binding.dateTextView.apply {
                setTextColor(secondaryTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.76f)

                val now = DateTime.now()
                text = when (date.dayCode) {
                    now.millis.toDayCode() -> activity.getString(R.string.today)
                    now.minusDays(1).millis.toDayCode() -> activity.getString(R.string.yesterday)
                    else -> date.timestamp.formatDateOrTime(activity, hideTimeOnOtherDays = true, showCurrentYear = false)
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_DATE = 0
        private const val VIEW_TYPE_CALL = 1
    }
}

class RecentCallsDiffCallback : DiffUtil.ItemCallback<CallLogItem>() {

    override fun areItemsTheSame(oldItem: CallLogItem, newItem: CallLogItem) = oldItem.getItemId() == newItem.getItemId()

    override fun areContentsTheSame(oldItem: CallLogItem, newItem: CallLogItem): Boolean {
        return when {
            oldItem is CallLogItem.Date && newItem is CallLogItem.Date -> oldItem.timestamp == newItem.timestamp && oldItem.dayCode == newItem.dayCode
            oldItem is RecentCall && newItem is RecentCall -> {
                oldItem.phoneNumber == newItem.phoneNumber &&
                    oldItem.name == newItem.name &&
                    oldItem.photoUri == newItem.photoUri &&
                    oldItem.startTS == newItem.startTS &&
                    oldItem.duration == newItem.duration &&
                    oldItem.type == newItem.type &&
                    oldItem.simID == newItem.simID &&
                    oldItem.specificNumber == newItem.specificNumber &&
                    oldItem.specificType == newItem.specificType &&
                    oldItem.isUnknownNumber == newItem.isUnknownNumber &&
                    oldItem.groupedCalls?.size == newItem.groupedCalls?.size
            }

            else -> false
        }
    }
}
