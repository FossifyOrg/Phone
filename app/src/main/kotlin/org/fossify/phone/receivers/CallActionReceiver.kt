package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.helpers.ACCEPT_CALL
import org.fossify.phone.helpers.CALL_NOTIFICATION_DISMISSED
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.CallNotificationManager
import org.fossify.phone.helpers.DECLINE_CALL
import org.fossify.phone.helpers.NoCall

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> {
                context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
            }

            DECLINE_CALL -> CallManager.reject()

            // Since Android 14 users can swipe away "ongoing" notifications. Without it there is
            // no way back to a running call (or to answer a ringing one), so put it right back.
            CALL_NOTIFICATION_DISMISSED -> {
                if (CallManager.getPhoneState() != NoCall) {
                    CallNotificationManager(context).setupNotification()
                }
            }
        }
    }
}
