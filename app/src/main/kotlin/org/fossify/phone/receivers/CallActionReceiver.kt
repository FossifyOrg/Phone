package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.CallAudioState
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.helpers.ACCEPT_CALL
import org.fossify.phone.helpers.ANSWER_SPEAKER_CALL
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.DECLINE_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> {
                context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
            }

            ANSWER_SPEAKER_CALL -> {
                CallManager.accept()
                CallManager.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }

            DECLINE_CALL -> CallManager.reject()
        }
    }
}
