package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.powerManager

/**
 * Should the screen be turned off by the proximity sensor right now?
 * Only while the phone is "off hook" — at least one call is being placed, in progress or on hold —
 * and audio is not on the speaker. A ringing call alone never turns it on, so it can be answered.
 *
 * All calls are considered, not just the primary one: with call waiting the new ringing call
 * becomes primary while the phone is still at the ear, and the screen must stay off.
 */
fun shouldUseProximitySensor(disabledInSettings: Boolean, callStates: Collection<Int>, audioRoute: Int?): Boolean {
    if (disabledInSettings) {
        return false
    }

    val isOffHook = callStates.any { state ->
        state == Call.STATE_ACTIVE ||
            state == Call.STATE_DIALING ||
            state == Call.STATE_CONNECTING ||
            state == Call.STATE_HOLDING
    }
    return isOffHook && audioRoute != CallAudioState.ROUTE_SPEAKER
}

/**
 * Owns the proximity wake lock for the whole call, independently of the call screen.
 *
 * It used to live in CallActivity, so it depended on that screen being alive and was acquired
 * with a fixed 60 minute timeout — on longer calls the screen stopped turning off near the ear.
 * Now it follows the call state from [org.fossify.phone.services.CallService] and is released
 * as soon as the call ends or audio moves to the speaker.
 */
object ProximitySensorHelper {
    private var wakeLock: PowerManager.WakeLock? = null

    fun update(context: Context) {
        val route = CallManager.inCallService?.callAudioState?.route
        if (shouldUseProximitySensor(context.config.disableProximitySensor, CallManager.getCallStates(), route)) {
            acquire(context)
        } else {
            release()
        }
    }

    @SuppressLint("WakelockTimeout") // released deterministically when the call ends or the service dies
    private fun acquire(context: Context) {
        val powerManager = context.applicationContext.powerManager
        if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            return
        }

        val lock = wakeLock ?: powerManager
            .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "org.fossify.phone:wake_lock")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }

        if (!lock.isHeld) {
            lock.acquire()
        }
    }

    fun release() {
        wakeLock?.let {
            if (it.isHeld) {
                // let the screen come back on only once the phone is moved away from the face
                it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            }
        }
    }
}
