package org.fossify.dialer.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding
import org.fossify.dialer.activities.SimpleActivity
import org.fossify.dialer.adapters.RecentCallsAdapter
import org.fossify.dialer.databinding.DialogShowGroupedCallsBinding
import org.fossify.dialer.helpers.RecentsHelper
import org.fossify.dialer.models.RecentCall

class ShowGroupedCallsDialog(val activity: BaseSimpleActivity, callIds: ArrayList<Int>) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogShowGroupedCallsBinding::inflate)

    init {
        RecentsHelper(activity).getRecentCalls(false) { allRecents ->
            val recents = allRecents.filter { callIds.contains(it.id) }.toMutableList() as ArrayList<RecentCall>
            activity.runOnUiThread {
                RecentCallsAdapter(activity as SimpleActivity, recents, binding.selectGroupedCallsList, null, false) {
                }.apply {
                    binding.selectGroupedCallsList.adapter = this
                }
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}
