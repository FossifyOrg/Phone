package org.fossify.phone.adapters

import android.annotation.SuppressLint
import android.text.SpannableString
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.databinding.ItemContactWithoutNumberBinding
import org.fossify.commons.databinding.ItemContactWithoutNumberGridBinding
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.KeypadHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ItemDialpadHeaderBinding
import org.fossify.phone.databinding.ItemRecentCallBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.interfaces.RefreshItemsListener
import org.fossify.phone.models.RecentCall
import org.fossify.phone.models.DialpadItem

class DialpadAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    highlightText: String = "",
    private val refreshItemsListener: RefreshItemsListener? = null,
    itemClick: (Any) -> Unit
) : MyRecyclerViewListAdapter<DialpadItem>(activity, recyclerView, DialpadItemsDiffCallback(), itemClick) {

    private var textToHighlight = highlightText
    var fontSize: Float = activity.getTextSize()
    private var secondaryTextColor = textColor.adjustAlpha(0.6f)

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

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

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

            VIEW_TYPE_CONTACT -> createViewHolder(ContactBinding.getByItemViewType(viewType).inflate(layoutInflater, parent, false).root)

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
            is RecentCallViewHolder -> holder.bind(dialpadItem)
        }

        bindViewHolder(holder)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            if (holder is RecentCallViewHolder) {
                Glide.with(activity).clear(holder.binding.itemRecentsImage)
            } else if(holder is ContactViewBinding) {
                ContactBinding.getByItemViewType(holder.itemViewType).bind(holder.itemView).apply {
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
        secondaryTextColor = textColor.adjustAlpha(0.6f)
    }

    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return (activity as MainActivity).cachedContacts.find { it.name == recentCall.name && it.doesHavePhoneNumber(recentCall.phoneNumber) }
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(binding: ContactViewBinding, contact: Contact, holder: ViewHolder) {
        binding.apply {
            root.setupViewBackground(activity)
            itemContactFrame.isSelected = selectedKeys.contains(contact.rawId)
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

    private inner class HeaderViewHolder(val binding: ItemDialpadHeaderBinding) : ViewHolder(binding.root) {
        fun bind(header: String) {
            binding.headerTextView.apply {
                setTextColor(secondaryTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.76f)
                text = header
            }
        }
    }

    private sealed interface ContactBinding {
        companion object {
            fun getByItemViewType(viewType: Int): ContactBinding {
                return ItemContact
            }
        }

        fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ContactViewBinding

        fun bind(view: View): ContactViewBinding

        data object ItemContactGrid : ContactBinding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ContactViewBinding {
                return ContactGridBindingAdapter(ItemContactWithoutNumberGridBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ContactViewBinding {
                return ContactGridBindingAdapter(ItemContactWithoutNumberGridBinding.bind(view))
            }
        }

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

    private class ContactGridBindingAdapter(val binding: ItemContactWithoutNumberGridBinding) : ContactViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon

        override fun getRoot(): View = binding.root
    }

    private class ContactBindingAdapter(val binding: ItemContactWithoutNumberBinding) : ContactViewBinding {
        override val itemContactName = binding.itemContactName
        override val itemContactImage = binding.itemContactImage
        override val itemContactFrame = binding.itemContactFrame
        override val dragHandleIcon = binding.dragHandleIcon

        override fun getRoot(): View = binding.root
    }

    private inner class RecentCallViewHolder(val binding: ItemRecentCallBinding) : ViewHolder(binding.root) {
        fun bind(call: DialpadItem) = bindView(
            item = call,
            allowSingleClick = refreshItemsListener != null && !call.recentCall!!.isUnknownNumber,
            allowLongClick = refreshItemsListener != null && !call.recentCall!!.isUnknownNumber
        ) { _, _ ->
            binding.apply {
                root.setupViewBackground(activity)

                val recentCall: RecentCall = call.recentCall!!

                val currentFontSize = fontSize
                itemRecentsHolder.isSelected = selectedKeys.contains(recentCall.id)
                val name = /*findContactByCall(recentCall)?.getNameToDisplay() ?:*/ recentCall.name
                val formatPhoneNumbers = activity.config.formatPhoneNumbers
                var nameToShow = if (name == recentCall.phoneNumber && formatPhoneNumbers) {
                    SpannableString(name.formatPhoneNumber())
                } else {
                    SpannableString(name)
                }

                if (recentCall.specificType.isNotEmpty()) {
                    nameToShow = SpannableString("$name - ${recentCall.specificType}")

                    // show specific number at "Show call details" dialog too
                    if (refreshItemsListener == null) {
                        nameToShow = if (formatPhoneNumbers) {
                            SpannableString("$name - ${recentCall.specificType}, ${recentCall.specificNumber.formatPhoneNumber()}")
                        } else {
                            SpannableString("$name - ${recentCall.specificType}, ${recentCall.specificNumber}")
                        }
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

                SimpleContactsHelper(root.context).loadContactImage(recentCall.photoUri, itemRecentsImage, recentCall.name)
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
        if(oldItem.isHeader() != newItem.isHeader() || oldItem.isContact() != newItem.isContact() || oldItem.isRecentCall() != newItem.isRecentCall()) {
            return false
        }

        return when (oldItem.itemType) {
            DialpadItem.DialpadItemType.HEADER -> oldItem.header == newItem.header
            DialpadItem.DialpadItemType.CONTACT -> oldItem.contact == newItem.contact
            DialpadItem.DialpadItemType.RECENTCALL -> oldItem.getItemId() == newItem.getItemId()
        }
    }

    override fun areContentsTheSame(oldItem: DialpadItem, newItem: DialpadItem): Boolean {
        if(oldItem.isHeader() != newItem.isHeader() || oldItem.isContact() != newItem.isContact() || oldItem.isRecentCall() != newItem.isRecentCall()) {
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
