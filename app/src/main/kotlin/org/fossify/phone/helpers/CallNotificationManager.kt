package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.app.Person
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.telecom.Call
import android.widget.RemoteViews
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.setText
import org.fossify.commons.extensions.setVisibleIf
import org.fossify.phone.R
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.receivers.CallActionReceiver

class CallNotificationManager(private val context: Context) {
    companion object {
        private const val CALL_NOTIFICATION_ID = 42
        private const val ACCEPT_CALL_CODE = 0
        private const val DECLINE_CALL_CODE = 1
        private const val DISMISSED_CODE = 2
    }

    private val notificationManager = context.notificationManager
    private val callContactAvatarHelper = CallContactAvatarHelper(context)

    @SuppressLint("NewApi")
    fun setupNotification(lowPriority: Boolean = false) {
        getCallContact(context.applicationContext, CallManager.getPrimaryCall()) { callContact ->
            val callContactAvatar = callContactAvatarHelper.getCallContactAvatar(callContact)
            val callState = CallManager.getState()
            val isHighPriority = callState == Call.STATE_RINGING && !lowPriority
            val channelId =
                if (isHighPriority) "simple_dialer_call_high_priority" else "simple_dialer_call"
            createNotificationChannel(isHighPriority, channelId)

            val openAppIntent = CallActivity.getStartIntent(context)
            val openAppPendingIntent =
                PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_MUTABLE)

            val acceptCallIntent = Intent(context, CallActionReceiver::class.java)
            acceptCallIntent.action = ACCEPT_CALL
            val acceptPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    ACCEPT_CALL_CODE,
                    acceptCallIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                )

            val declineCallIntent = Intent(context, CallActionReceiver::class.java)
            declineCallIntent.action = DECLINE_CALL
            val declinePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    DECLINE_CALL_CODE,
                    declineCallIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                )

            var callerName = callContact.name.ifEmpty { context.getString(R.string.unknown_caller) }
            if (callContact.numberLabel.isNotEmpty()) {
                callerName += " - ${callContact.numberLabel}"
            }

            val contentTextId = when (callState) {
                Call.STATE_RINGING -> R.string.is_calling
                Call.STATE_DIALING -> R.string.dialing
                Call.STATE_DISCONNECTED -> R.string.call_ended
                Call.STATE_DISCONNECTING -> R.string.call_ending
                else -> R.string.ongoing_call
            }

            val collapsedView = RemoteViews(context.packageName, R.layout.call_notification).apply {
                setText(R.id.notification_caller_name, callerName)
                setText(R.id.notification_call_status, context.getString(contentTextId))
                setVisibleIf(R.id.notification_accept_call_holder, callState == Call.STATE_RINGING)

                setOnClickPendingIntent(R.id.notification_decline_call, declinePendingIntent)
                setOnClickPendingIntent(R.id.notification_accept_call, acceptPendingIntent)

                if (callContactAvatar != null) {
                    setImageViewBitmap(
                        R.id.notification_thumbnail,
                        callContactAvatarHelper.getCircularBitmap(callContactAvatar)
                    )
                }
            }

            // Attaching the caller lets Do Not Disturb / Bedtime modes match the call against
            // "starred contacts", "contacts" and "repeat callers" rules. Without it the system can
            // hide the incoming call notification even though the phone rings.
            val caller = Caller(
                name = callContact.name.ifEmpty {
                    callContact.number.ifEmpty { context.getString(R.string.unknown_caller) }
                },
                uri = getCallerUri(),
                avatar = callContactAvatar
            )
            val dismissedPendingIntent = getDismissedPendingIntent()

            val legacyBuilder = Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_phone_vector)
                .setContentIntent(openAppPendingIntent)
                .setDeleteIntent(dismissedPendingIntent)
                .setCategory(Notification.CATEGORY_CALL)
                .setCustomContentView(collapsedView)
                .setOngoing(true)
                .setUsesChronometer(callState == Call.STATE_ACTIVE)
                .setChannelId(channelId)
                .setStyle(Notification.DecoratedCustomViewStyle())

            if (isHighPriority) {
                legacyBuilder.setFullScreenIntent(openAppPendingIntent, true)
            }
            addCaller(legacyBuilder, caller)

            val incomingCallStyle = if (canUseIncomingCallStyle(isHighPriority)) {
                buildIncomingCallStyle(
                    channelId = channelId,
                    caller = caller,
                    openAppPendingIntent = openAppPendingIntent,
                    dismissedPendingIntent = dismissedPendingIntent,
                    declinePendingIntent = declinePendingIntent,
                    acceptPendingIntent = acceptPendingIntent
                )
            } else {
                null
            }

            // it's rare but possible for the call state to change by now
            if (CallManager.getState() == callState) {
                notify(incomingCallStyle, legacyBuilder)
            }
        }
    }

    private class Caller(val name: String, val uri: String?, val avatar: Bitmap?)

    private fun notify(incomingCallStyle: Notification.Builder?, legacyBuilder: Notification.Builder) {
        if (incomingCallStyle == null) {
            notificationManager.notify(CALL_NOTIFICATION_ID, legacyBuilder.build())
            return
        }

        try {
            notificationManager.notify(CALL_NOTIFICATION_ID, incomingCallStyle.build())
        } catch (_: IllegalArgumentException) {
            // Some OEM builds reject CallStyle under conditions we can't check upfront.
            // Never lose the call notification because of that — fall back to the classic one.
            notificationManager.notify(CALL_NOTIFICATION_ID, legacyBuilder.build())
        }
    }

    // On Android 12+ a ringing call uses the system CallStyle: it can't be swiped away on
    // Android 14+ and has large answer/decline buttons. CallStyle requires a full-screen
    // intent, so it is only used when that permission is actually granted.
    private fun canUseIncomingCallStyle(isHighPriority: Boolean): Boolean {
        return isHighPriority &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.canUseFullScreenIntent()
    }

    @SuppressLint("NewApi")
    private fun buildIncomingCallStyle(
        channelId: String,
        caller: Caller,
        openAppPendingIntent: PendingIntent,
        dismissedPendingIntent: PendingIntent,
        declinePendingIntent: PendingIntent,
        acceptPendingIntent: PendingIntent,
    ): Notification.Builder {
        return Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(dismissedPendingIntent)
            .setFullScreenIntent(openAppPendingIntent, true)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setChannelId(channelId)
            .setStyle(
                Notification.CallStyle.forIncomingCall(
                    buildPerson(caller),
                    declinePendingIntent,
                    acceptPendingIntent
                )
            )
    }

    private fun getDismissedPendingIntent(): PendingIntent {
        val dismissedIntent = Intent(context, CallActionReceiver::class.java)
        dismissedIntent.action = CALL_NOTIFICATION_DISMISSED
        return PendingIntent.getBroadcast(
            context,
            DISMISSED_CODE,
            dismissedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getCallerUri(): String? {
        val handle = try {
            CallManager.getPrimaryCall()?.details?.handle
        } catch (_: Exception) {
            null
        }
        return handle?.takeIf { it.scheme == "tel" && !it.schemeSpecificPart.isNullOrEmpty() }?.toString()
    }

    @SuppressLint("NewApi")
    private fun buildPerson(caller: Caller): Person {
        return Person.Builder()
            .setName(caller.name)
            .setUri(caller.uri)
            .setImportant(true)
            .apply {
                if (caller.avatar != null) {
                    setIcon(Icon.createWithBitmap(callContactAvatarHelper.getCircularBitmap(caller.avatar)))
                }
            }
            .build()
    }

    @Suppress("DEPRECATION")
    private fun addCaller(builder: Notification.Builder, caller: Caller) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.addPerson(buildPerson(caller))
        } else if (caller.uri != null) {
            builder.addPerson(caller.uri)
        }
    }

    fun createNotificationChannel(isHighPriority: Boolean, channelId: String) {
        val name = if (isHighPriority) {
            context.getString(R.string.call_notification_channel_high_priority)
        } else {
            context.getString(R.string.call_notification_channel)
        }

        val importance = if (isHighPriority) IMPORTANCE_HIGH else IMPORTANCE_DEFAULT
        NotificationChannel(channelId, name, importance).apply {
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }
}
