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
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewAdapter
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
    private var recentCalls: MutableList<CallLogItem>,
    recyclerView: MyRecyclerView,
    private val refreshItemsListener: RefreshItemsListener?,
    private val showOverflowMenu: Boolean,
    private val itemDelete: (List<RecentCall>) -> Unit = {},
    itemClick: (Any) -> Unit,
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private lateinit var outgoingCallIcon: Drawable
    private lateinit var incomingCallIcon: Drawable
    private lateinit var incomingMissedCallIcon: Drawable
    var fontSize: Float = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private val redColor = resources.getColor(R.color.md_red_700)
    private var textToHighlight = ""
    private var durationPadding = resources.getDimension(R.dimen.normal_margin).toInt()

    init {
        initDrawables()
        setupDragListener(true)
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

    override fun getSelectableItemCount() = recentCalls.filterIsInstance<RecentCall>().size

    override fun getIsItemSelectable(position: Int) = recentCalls[position] is RecentCall

    override fun getItemSelectionKey(position: Int) = recentCalls.getOrNull(position)?.getItemId()

    override fun getItemKeyPosition(key: Int) = recentCalls.indexOfFirst { it.getItemId() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun getItemViewType(position: Int): Int {
        return when (recentCalls[position]) {
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
        val callRecord = recentCalls[position]
        when (holder) {
            is RecentCallDateViewHolder -> holder.bind(callRecord as CallLogItem.Date)
            is RecentCallViewHolder -> holder.bind(callRecord as RecentCall)
        }

        bindViewHolder(holder)
    }

    override fun getItemCount() = recentCalls.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            if (holder is RecentCallViewHolder) {
                Glide.with(activity).clear(holder.binding.itemRecentsImage)
            }
        }
    }

    fun initDrawables() {
        outgoingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_outgoing_call_vector, activity.getProperTextColor())
        incomingCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_incoming_call_vector, activity.getProperTextColor())
        incomingMissedCallIcon = resources.getColoredDrawableWithColor(R.drawable.ic_incoming_call_vector, redColor)
    }

    private fun callContact(useSimOne: Boolean) {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.callContactWithSim(phoneNumber, useSimOne)
    }

    private fun callContact() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        (activity as SimpleActivity).startCallIntent(phoneNumber)
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
        val positions = getSelectedItemPositions()
        recentCalls.removeAll(callsToBlock)

        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            activity.runOnUiThread {
                removeSelectedItems(positions)
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
        val positions = getSelectedItemPositions()
        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.groupedCalls?.mapTo(idsToRemove) { call -> call.id }
        }

        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
            itemDelete(callsToRemove)
            recentCalls.removeAll(callsToRemove)
            activity.runOnUiThread {
                refreshItemsListener?.refreshItems()
                if (recentCalls.isEmpty()) {
                    finishActMode()
                } else {
                    removeSelectedItems(positions)
                }
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
        textColor = activity.getProperTextColor()
        if (newItems.hashCode() != recentCalls.hashCode()) {
            recentCalls = newItems.toMutableList()
            textToHighlight = highlightText
            recyclerView.resetItemCount()
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun getSelectedItems() = recentCalls.filterIsInstance<RecentCall>()
        .filter { selectedKeys.contains(it.getItemId()) }

    private fun getSelectedPhoneNumber() = getSelectedItems().firstOrNull()?.phoneNumber

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
            any = call,
            allowSingleClick = refreshItemsListener != null && !call.isUnknownNumber,
            allowLongClick = refreshItemsListener != null && !call.isUnknownNumber
        ) { _, _ ->
            binding.apply {
                val currentFontSize = fontSize
                itemRecentsHolder.isSelected = selectedKeys.contains(call.id)
                val name = findContactByCall(call)?.getNameToDisplay() ?: call.name
                var nameToShow = if (name == call.phoneNumber) {
                    SpannableString(name.formatPhoneNumber())
                } else {
                    SpannableString(name)
                }

                if (call.specificType.isNotEmpty()) {
                    nameToShow = SpannableString("$name - ${call.specificType}")

                    // show specific number at "Show call details" dialog too
                    if (refreshItemsListener == null) {
                        nameToShow = SpannableString("$name - ${call.specificType}, ${call.specificNumber.formatPhoneNumber()}")
                    }
                }

                if (call.groupedCalls != null) {
                    nameToShow = SpannableString("$nameToShow (${call.groupedCalls.size})")
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
                    text = if (refreshItemsListener == null) {
                        call.startTS.formatDateOrTime(context, hideTimeOnOtherDays = false, showCurrentYear = false, hideTodaysDate = false)
                    } else {
                        call.startTS.formatTime(activity)
                    }

                    setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
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
                setTextColor(textColor.adjustAlpha(0.6f))
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
