package org.fossify.phone.services

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.isOutgoing
import org.fossify.phone.extensions.keyguardManager
import org.fossify.phone.extensions.powerManager
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.CallNotificationManager
import org.fossify.phone.helpers.NoCall
import org.fossify.phone.helpers.clearCallContactCache
import org.fossify.phone.models.Events
import org.greenrobot.eventbus.EventBus

class CallService : InCallService() {
    companion object {
        private const val TAG = "CallService"
    }

    private val callNotificationManager by lazy { CallNotificationManager(this) }
    private var isForegroundStarted = false

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            } else {
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)

        // Incoming/Outgoing (locked): high priority (FSI)
        // Incoming (unlocked): if user opted in, low priority ➜ manual activity start, otherwise high priority (FSI)
        // Outgoing (unlocked): low priority ➜ manual activity start
        val isIncoming = !call.isOutgoing()
        val isDeviceLocked = !powerManager.isInteractive || keyguardManager.isDeviceLocked
        val lowPriority = when {
            isIncoming && isDeviceLocked -> false
            !isIncoming && isDeviceLocked -> false
            isIncoming && !isDeviceLocked -> config.alwaysShowFullscreen
            else -> true
        }

        // Promoting to a phoneCall foreground service keeps the process out of the cached/frozen
        // state for the duration of the call and makes the ongoing-call notification
        // non-dismissible. The priority must match the one setupNotification uses below, otherwise
        // this notification would attach a full screen intent the user opted out of.
        startForegroundIfNeeded(call, lowPriority)

        callNotificationManager.setupNotification(lowPriority)
        if (
            lowPriority
            || !hasPermission(PERMISSION_POST_NOTIFICATIONS)
            || !canUseFullScreenIntent()
        ) {
            try {
                startActivity(CallActivity.getStartIntent(this))
            } catch (_: Exception) {
                // seems like startActivity can throw AndroidRuntimeException and
                // ActivityNotFoundException, not yet sure when and why, lets show a notification
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        val wasPrimaryCall = call == CallManager.getPrimaryCall()
        CallManager.onCallRemoved(call)
        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
            stopForegroundIfNeeded()
            callNotificationManager.cancelNotification()
            clearCallContactCache()
        } else {
            callNotificationManager.setupNotification()
            if (wasPrimaryCall) {
                try {
                    startActivity(CallActivity.getStartIntent(this))
                } catch (_: Exception) {
                    callNotificationManager.setupNotification()
                }
            }
        }

        EventBus.getDefault().post(Events.RefreshCallLog)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        try {
            startActivity(CallActivity.getStartIntent(this))
        } catch (_: Exception) {
            callNotificationManager.setupNotification()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundIfNeeded()
        callNotificationManager.cancelNotification()
    }

    private fun startForegroundIfNeeded(call: Call, lowPriority: Boolean) {
        if (isForegroundStarted) {
            return
        }

        try {
            startForeground(
                CallNotificationManager.CALL_NOTIFICATION_ID,
                callNotificationManager.buildForegroundNotification(call, lowPriority)
            )
            isForegroundStarted = true
        } catch (e: SecurityException) {
            // thrown when the phoneCall foreground service type is unavailable to us, e.g. missing
            // FOREGROUND_SERVICE_PHONE_CALL or not the default dialer
            Log.w(TAG, "Not allowed to start a phoneCall foreground service", e)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException and InvalidForegroundServiceTypeException
            Log.w(TAG, "Could not start the foreground service for the ongoing call", e)
        }
    }

    private fun stopForegroundIfNeeded() {
        if (!isForegroundStarted) {
            return
        }

        // has to happen before cancelNotification, a foreground service's notification cannot be
        // dismissed while the service is still attached to it
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundStarted = false
    }
}
