package org.fossify.phone.dialogs

import android.provider.CallLog
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.RecentCallsAdapter
import org.fossify.phone.databinding.DialogShowGroupedCallsBinding
import org.fossify.phone.models.RecentCall

class ShowGroupedCallsDialog(
    val activity: BaseSimpleActivity,
    recentCalls: List<RecentCall>
) {

    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogShowGroupedCallsBinding::inflate)

    init {

        if (recentCalls.isNotEmpty()) {

            val firstCall = recentCalls.first()

            binding.contactName.text = firstCall.name
            binding.contactNumber.text = firstCall.phoneNumber

            SimpleContactsHelper(activity).loadContactImage(
                firstCall.photoUri,
                binding.contactImage,
                firstCall.name
            )

            val totalCalls = recentCalls.size

            val incomingCalls =
                recentCalls.count {
                    it.type == CallLog.Calls.INCOMING_TYPE
                }

            val outgoingCalls =
                recentCalls.count {
                    it.type == CallLog.Calls.OUTGOING_TYPE
                }

            val missedCalls =
                recentCalls.count {
                    it.type == CallLog.Calls.MISSED_TYPE
                }

            binding.totalCalls.text =
                "Total\n$totalCalls"

            binding.incomingCalls.text =
                "Incoming\n$incomingCalls"

            binding.outgoingCalls.text =
                "Outgoing\n$outgoingCalls"

            binding.missedCalls.text =
                "Missed\n$missedCalls"
        }

        activity.runOnUiThread {

            RecentCallsAdapter(
                activity = activity as SimpleActivity,
                recyclerView = binding.selectGroupedCallsList,
                refreshItemsListener = null,
                showOverflowMenu = false,
                itemClick = {}
            ).apply {

                binding.selectGroupedCallsList.adapter = this
                updateItems(recentCalls)
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(
                    binding.root,
                    this
                ) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}