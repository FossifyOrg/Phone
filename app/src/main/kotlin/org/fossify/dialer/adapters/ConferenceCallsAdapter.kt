package org.fossify.dialer.adapters

import android.telecom.Call
import android.view.Menu
import android.view.ViewGroup
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.views.MyRecyclerView
import org.fossify.dialer.R
import org.fossify.dialer.activities.SimpleActivity
import org.fossify.dialer.databinding.ItemConferenceCallBinding
import org.fossify.dialer.extensions.hasCapability
import org.fossify.dialer.helpers.getCallContact

class ConferenceCallsAdapter(
    activity: SimpleActivity, recyclerView: MyRecyclerView, val data: ArrayList<Call>, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    override fun actionItemPressed(id: Int) {}

    override fun getActionMenuId(): Int = 0

    override fun getIsItemSelectable(position: Int): Boolean = false

    override fun getItemCount(): Int = data.size

    override fun getItemKeyPosition(key: Int): Int = -1

    override fun getItemSelectionKey(position: Int): Int? = null

    override fun getSelectableItemCount(): Int = data.size

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun prepareActionMode(menu: Menu) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemConferenceCallBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val call = data[position]
        holder.bindView(call, allowSingleClick = false, allowLongClick = false) { itemView, _ ->
            ItemConferenceCallBinding.bind(itemView).apply {
                getCallContact(itemView.context, call) { callContact ->
                    root.post {
                        itemConferenceCallName.text = callContact.name.ifEmpty { itemView.context.getString(R.string.unknown_caller) }
                        SimpleContactsHelper(activity).loadContactImage(
                            callContact.photoUri,
                            itemConferenceCallImage,
                            callContact.name,
                            activity.getDrawable(R.drawable.ic_person_vector)
                        )
                    }
                }

                val canSeparate = call.hasCapability(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE)
                val canDisconnect = call.hasCapability(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)
                itemConferenceCallSplit.isEnabled = canSeparate
                itemConferenceCallSplit.alpha = if (canSeparate) 1.0f else LOWER_ALPHA
                itemConferenceCallSplit.setOnClickListener {
                    call.splitFromConference()
                    data.removeAt(position)
                    notifyItemRemoved(position)
                    if (data.size == 1) {
                        activity.finish()
                    }
                }

                itemConferenceCallSplit.setOnLongClickListener {
                    if (!it.contentDescription.isNullOrEmpty()) {
                        root.context.toast(it.contentDescription.toString())
                    }
                    true
                }

                itemConferenceCallEnd.isEnabled = canDisconnect
                itemConferenceCallEnd.alpha = if (canDisconnect) 1.0f else LOWER_ALPHA
                itemConferenceCallEnd.setOnClickListener {
                    call.disconnect()
                    data.removeAt(position)
                    notifyItemRemoved(position)
                    if (data.size == 1) {
                        activity.finish()
                    }
                }

                itemConferenceCallEnd.setOnLongClickListener {
                    if (!it.contentDescription.isNullOrEmpty()) {
                        root.context.toast(it.contentDescription.toString())
                    }
                    true
                }
            }
        }
        bindViewHolder(holder)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            ItemConferenceCallBinding.bind(holder.itemView).apply {
                Glide.with(activity).clear(itemConferenceCallImage)
            }
        }
    }
}
