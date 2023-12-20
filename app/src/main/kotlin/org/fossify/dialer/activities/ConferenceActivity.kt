package org.fossify.dialer.activities

import android.os.Bundle
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.dialer.adapters.ConferenceCallsAdapter
import org.fossify.dialer.databinding.ActivityConferenceBinding
import org.fossify.dialer.helpers.CallManager

class ConferenceActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityConferenceBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            updateMaterialActivityViews(conferenceCoordinator, conferenceList, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(conferenceList, conferenceToolbar)
            conferenceList.adapter = ConferenceCallsAdapter(this@ConferenceActivity, conferenceList, ArrayList(CallManager.getConferenceCalls())) {}
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.conferenceToolbar, NavigationIcon.Arrow)
    }
}
