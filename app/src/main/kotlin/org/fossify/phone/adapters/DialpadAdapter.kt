package org.fossify.phone.adapters

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.provider.CallLog.Calls
import android.text.SpannableString
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
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.KeypadHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ItemDialpadHeaderBinding
import org.fossify.phone.databinding.ItemRecentCallBinding
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.config
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

    override fun getActionMenuId() = R.menu.cab_contacts

    override fun prepareActionMode(menu: Menu) {

    }

    override fun actionItemPressed(id: Int) {

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
