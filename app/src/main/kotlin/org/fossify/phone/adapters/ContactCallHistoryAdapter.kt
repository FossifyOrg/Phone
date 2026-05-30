package org.fossify.phone.adapters

import android.graphics.drawable.Drawable
import android.provider.CallLog.Calls
import android.util.TypedValue
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.adjustForContrast
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.formatSecondsToShortTimeString
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ItemRecentCallBinding
import org.fossify.phone.databinding.ItemRecentsDateBinding
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.getDayCode
import org.fossify.phone.models.CallLogItem
import org.fossify.phone.models.RecentCall
import org.joda.time.DateTime

class ContactCallHistoryAdapter(
    private val activity: SimpleActivity,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<CallLogItem>()
    private val layoutInflater = activity.layoutInflater
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private val fontSize = activity.getTextSize()
    private val backgroundColor = activity.getProperBackgroundColor()
    private val textColor = activity.getProperTextColor()
    private val secondaryTextColor = textColor.adjustAlpha(0.6f)
    private val cachedSimColors = HashMap<Pair<Int, Int>, Int>()

    private val outgoingCallIcon: Drawable
    private val incomingCallIcon: Drawable
    private val missedCallIcon: Drawable
    private val missedCallColor: Int

    init {
        setHasStableIds(true)
        val theme = activity.theme
        missedCallColor = activity.resources.getColor(R.color.color_missed_call, theme)
        val outgoingCallColor = activity.resources.getColor(R.color.color_outgoing_call, theme)
        val incomingCallColor = activity.resources.getColor(R.color.color_incoming_call, theme)
        outgoingCallIcon = activity.resources.getColoredDrawableWithColor(R.drawable.ic_call_made_vector, outgoingCallColor)
        incomingCallIcon = activity.resources.getColoredDrawableWithColor(R.drawable.ic_call_received_vector, incomingCallColor)
        missedCallIcon = activity.resources.getColoredDrawableWithColor(R.drawable.ic_call_missed_vector, missedCallColor)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is CallLogItem.Date -> VIEW_TYPE_DATE
            is RecentCall -> VIEW_TYPE_CALL
        }
    }

    override fun getItemId(position: Int) = items[position].getItemId().toLong()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_DATE -> DateViewHolder(ItemRecentsDateBinding.inflate(layoutInflater, parent, false))
            VIEW_TYPE_CALL -> CallViewHolder(ItemRecentCallBinding.inflate(layoutInflater, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DateViewHolder -> holder.bind(items[position] as CallLogItem.Date)
            is CallViewHolder -> holder.bind(items[position] as RecentCall)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing && holder is CallViewHolder) {
            Glide.with(activity).clear(holder.binding.itemRecentsImage)
        }
    }

    fun submitItems(newItems: List<CallLogItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private inner class CallViewHolder(val binding: ItemRecentCallBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(call: RecentCall) {
            binding.apply {
                root.setupViewBackground(activity)
                root.isClickable = false
                root.isFocusable = false
                itemRecentsHolder.isSelected = false

                itemRecentsName.apply {
                    text = call.name
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                    isSelected = true
                }

                itemRecentsDateTime.apply {
                    text = call.startTS.formatDateOrTime(
                        context = activity,
                        hideTimeOnOtherDays = false,
                        showCurrentYear = false,
                        hideTodaysDate = false
                    )
                    setTextColor(if (call.type == Calls.MISSED_TYPE) missedCallColor else secondaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
                }

                val shouldShowDuration = call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0
                itemRecentsDateTimeDurationSeparator.apply {
                    text = "•"
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
                    beVisibleIf(shouldShowDuration)
                }

                itemRecentsDuration.apply {
                    text = activity.formatSecondsToShortTimeString(call.duration)
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
                    beVisibleIf(shouldShowDuration)
                }

                itemRecentsLocation.beGone()
                overflowMenuIcon.beGone()
                overflowMenuAnchor.beGone()

                itemRecentsSimImage.beVisibleIf(areMultipleSIMsAvailable && call.simID != -1)
                itemRecentsSimId.beVisibleIf(areMultipleSIMsAvailable && call.simID != -1)
                if (areMultipleSIMsAvailable && call.simID != -1) {
                    val simColor = getAdjustedSimColor(call.simColor)
                    itemRecentsSimImage.applyColorFilter(simColor)
                    itemRecentsSimId.setTextColor(simColor.getContrastColor())
                    itemRecentsSimId.text = call.simID.toString()
                }

                SimpleContactsHelper(root.context).loadContactImage(call.photoUri, itemRecentsImage, call.name)

                val drawable = when (call.type) {
                    Calls.OUTGOING_TYPE -> outgoingCallIcon
                    Calls.MISSED_TYPE -> missedCallIcon
                    else -> incomingCallIcon
                }
                itemRecentsType.setImageDrawable(drawable)
            }
        }
    }

    private inner class DateViewHolder(private val binding: ItemRecentsDateBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(date: CallLogItem.Date) {
            binding.dateTextView.apply {
                setTextColor(secondaryTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.76f)
                val now = DateTime.now()
                text = when (date.dayCode) {
                    now.millis.getDayCode() -> activity.getString(R.string.today)
                    now.minusDays(1).millis.getDayCode() -> activity.getString(R.string.yesterday)
                    else -> date.timestamp.formatDateOrTime(
                        context = activity,
                        hideTimeOnOtherDays = true,
                        showCurrentYear = false
                    )
                }
            }
        }
    }

    private fun getAdjustedSimColor(simColor: Int): Int {
        return cachedSimColors.getOrPut(simColor to backgroundColor) {
            simColor.adjustForContrast(backgroundColor)
        }
    }

    companion object {
        private const val VIEW_TYPE_DATE = 0
        private const val VIEW_TYPE_CALL = 1
    }
}
