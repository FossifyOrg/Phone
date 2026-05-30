package org.fossify.phone.activities

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.telecom.VideoProfile
import android.provider.CallLog.Calls
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.formatPhoneNumber
import org.fossify.commons.extensions.formatSecondsToShortTimeString
import org.fossify.commons.extensions.launchSendSMSIntent
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.phone.R
import org.fossify.phone.adapters.ContactCallHistoryAdapter
import org.fossify.phone.databinding.ActivityContactCallHistoryBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.models.CallLogItem
import org.fossify.phone.models.RecentCall

class ContactCallHistoryActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityContactCallHistoryBinding::inflate)
    private lateinit var adapter: ContactCallHistoryAdapter
    private lateinit var seedCall: RecentCall

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(contactCallHistoryList))
            setupMaterialScrollListener(contactCallHistoryList, contactCallHistoryAppbar)
        }

        seedCall = getSeedCall() ?: run {
            finish()
            return
        }

        adapter = ContactCallHistoryAdapter(this)
        binding.contactCallHistoryList.adapter = adapter

        bindHeader(seedCall, emptyList())
        setupActions()
        updateTextColors(binding.contactCallHistoryCoordinator)
        binding.contactCallHistoryCoordinator.setBackgroundColor(getNovaBackgroundColor())
        loadCallHistory()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.contactCallHistoryAppbar, NavigationIcon.Arrow)
    }

    private fun setupActions() {
        binding.callAction.setOnClickListener {
            startCallWithConfirmationCheck(seedCall.phoneNumber, seedCall.name)
        }

        binding.messageAction.setOnClickListener {
            launchSendSMSIntent(seedCall.phoneNumber)
        }

        binding.videoCallAction.setOnClickListener {
            launchVideoCall()
        }
    }

    private fun launchVideoCall() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.fromParts("tel", seedCall.phoneNumber, null)).apply {
            putExtra("android.telecom.extra.START_CALL_WITH_VIDEO_STATE", VideoProfile.STATE_BIDIRECTIONAL)
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            toast(R.string.no_video_call_app)
        }
    }

    private fun loadCallHistory() {
        if (!hasPermission(PERMISSION_READ_CALL_LOG)) {
            binding.progressIndicator.hide()
            binding.contactCallHistoryPlaceholder.beVisible()
            return
        }

        RecentsHelper(this).getRecentCallsForNumber(seedCall) { calls ->
            runOnUiThread {
                binding.progressIndicator.hide()
                val callsToShow = calls.ifEmpty { listOf(seedCall) }
                bindHeader(callsToShow.first(), callsToShow)
                adapter.submitItems(groupCallsByDate(callsToShow))
                binding.contactCallHistoryPlaceholder.beGone()
            }
        }
    }

    private fun bindHeader(call: RecentCall, calls: List<RecentCall>) {
        val displayNumber = if (config.formatPhoneNumbers) {
            call.phoneNumber.formatPhoneNumber()
        } else {
            call.phoneNumber
        }

        binding.apply {
            contactName.text = call.name
            contactNumber.text = displayNumber
            SimpleContactsHelper(this@ContactCallHistoryActivity).loadContactImage(call.photoUri, contactImage, call.name)

            totalCallsValue.text = "${getString(R.string.total)}\n${calls.size}"
            incomingCallsValue.text = "${getString(R.string.incoming)}\n${calls.count { it.type == Calls.INCOMING_TYPE }}"
            outgoingCallsValue.text = "${getString(R.string.outgoing)}\n${calls.count { it.type == Calls.OUTGOING_TYPE }}"
            missedCallsValue.text = "${getString(R.string.missed)}\n${calls.count { it.type == Calls.MISSED_TYPE }}"
            totalCallDurationValue.text = formatSecondsToShortTimeString(calls.sumOf { it.duration })
        }
    }

    private fun groupCallsByDate(recentCalls: List<RecentCall>): List<CallLogItem> {
        val callLog = mutableListOf<CallLogItem>()
        var lastDayCode = ""
        for (call in recentCalls) {
            val currentDayCode = call.dayCode
            if (currentDayCode != lastDayCode) {
                callLog += CallLogItem.Date(timestamp = call.startTS, dayCode = currentDayCode)
                lastDayCode = currentDayCode
            }
            callLog += call
        }
        return callLog
    }

    private fun getSeedCall(): RecentCall? {
        if (!intent.hasExtra(EXTRA_CALL_ID)) {
            return null
        }

        return RecentCall(
            id = intent.getIntExtra(EXTRA_CALL_ID, 0),
            phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty(),
            name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            photoUri = intent.getStringExtra(EXTRA_PHOTO_URI).orEmpty(),
            startTS = intent.getLongExtra(EXTRA_START_TS, 0L),
            duration = intent.getIntExtra(EXTRA_DURATION, 0),
            type = intent.getIntExtra(EXTRA_TYPE, Calls.INCOMING_TYPE),
            simID = intent.getIntExtra(EXTRA_SIM_ID, -1),
            simColor = intent.getIntExtra(EXTRA_SIM_COLOR, -1),
            specificNumber = intent.getStringExtra(EXTRA_SPECIFIC_NUMBER).orEmpty(),
            specificType = intent.getStringExtra(EXTRA_SPECIFIC_TYPE).orEmpty(),
            isUnknownNumber = intent.getBooleanExtra(EXTRA_IS_UNKNOWN_NUMBER, false),
        )
    }

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_NAME = "name"
        const val EXTRA_PHOTO_URI = "photo_uri"
        const val EXTRA_START_TS = "start_ts"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_TYPE = "type"
        const val EXTRA_SIM_ID = "sim_id"
        const val EXTRA_SIM_COLOR = "sim_color"
        const val EXTRA_SPECIFIC_NUMBER = "specific_number"
        const val EXTRA_SPECIFIC_TYPE = "specific_type"
        const val EXTRA_IS_UNKNOWN_NUMBER = "is_unknown_number"
    }
}
