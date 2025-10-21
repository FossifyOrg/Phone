package org.fossify.phone.activities

import android.os.Bundle
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.phone.adapters.ConferenceCallsAdapter
import org.fossify.phone.databinding.ActivityConferenceBinding
import org.fossify.phone.helpers.CallManager

class ConferenceActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityConferenceBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(conferenceList))
            setupMaterialScrollListener(binding.conferenceList, binding.conferenceAppbar)
            conferenceList.adapter = ConferenceCallsAdapter(this@ConferenceActivity, conferenceList, ArrayList(CallManager.getConferenceCalls())) {}
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.conferenceAppbar, NavigationIcon.Arrow)
    }
}
