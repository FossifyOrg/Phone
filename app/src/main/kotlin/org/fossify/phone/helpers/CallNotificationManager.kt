package org.fossify.phone.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.telecom.Call
import android.widget.RemoteViews
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.setText
import org.fossify.commons.extensions.setVisibleIf
import org.fossify.phone.R
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.extensions.getStateCompat
import org.fossify.phone.receivers.CallActionReceiver

class CallNotificationManager(private val context: Context) {
    companion object {
        const val CALL_NOTIFICATION_ID = 42
        private const val ACCEPT_CALL_CODE = 0
        private const val DECLINE_CALL_CODE = 1
        private const val CHANNEL_ID = "simple_dialer_call"
        private const val CHANNEL_ID_HIGH_PRIORITY = "simple_dialer_call_high_priority"
    }

    private val notificationManager = context.notificationManager
    private val callContactAvatarHelper = CallContactAvatarHelper(context)

    /**
     * Builds the notification synchronously, so it can be handed to
     * [android.app.Service.startForeground] from within onCallAdded. Resolving the caller's contact
     * hits the contacts provider and cannot be done here without blocking the InCallService
     * callback, so the raw number is shown until [setupNotification] replaces this notification
     * with the enriched one under the same id.
     *
     * [lowPriority] must be the same value that is later passed to [setupNotification], otherwise
     * this notification would attach a full screen intent the caller decided not to use.
     */
    fun buildForegroundNotification(call: Call, lowPriority: Boolean): Notification {
        val number = try {
            call.details.handle?.schemeSpecificPart
        } catch (_: Exception) {
            // details can throw once the call is already being torn down
            null
        }

        return buildNotification(
            callerName = number?.ifEmpty { null } ?: context.getString(R.string.unknown_caller),
            callState = call.getStateCompat(),
            lowPriority = lowPriority,
            avatar = null,
        )
    }

    fun setupNotification(lowPriority: Boolean = false) {
        getCallContact(context.applicationContext, CallManager.getPrimaryCall()) { callContact ->
            val callState = CallManager.getState() ?: return@getCallContact
            var callerName = callContact.name.ifEmpty { context.getString(R.string.unknown_caller) }
            if (callContact.numberLabel.isNotEmpty()) {
                callerName += " - ${callContact.numberLabel}"
            }

            val notification = buildNotification(
                callerName = callerName,
                callState = callState,
                lowPriority = lowPriority,
                // already circular, getCallContactAvatar crops it
                avatar = callContactAvatarHelper.getCallContactAvatar(callContact),
            )

            // it's rare but possible for the call state to change by now
            if (CallManager.getState() == callState) {
                notificationManager.notify(CALL_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(
        callerName: String,
        callState: Int,
        lowPriority: Boolean,
        avatar: Bitmap?,
    ): Notification {
        val isHighPriority = callState == Call.STATE_RINGING && !lowPriority
        val channelId = if (isHighPriority) CHANNEL_ID_HIGH_PRIORITY else CHANNEL_ID
        createNotificationChannel(isHighPriority, channelId)

        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, CallActivity.getStartIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptPendingIntent = PendingIntent.getBroadcast(
            context, ACCEPT_CALL_CODE,
            Intent(context, CallActionReceiver::class.java).apply { action = ACCEPT_CALL },
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declinePendingIntent = PendingIntent.getBroadcast(
            context, DECLINE_CALL_CODE,
            Intent(context, CallActionReceiver::class.java).apply { action = DECLINE_CALL },
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            setVisibleIf(R.id.notification_accept_call, callState == Call.STATE_RINGING)

            setOnClickPendingIntent(R.id.notification_decline_call, declinePendingIntent)
            setOnClickPendingIntent(R.id.notification_accept_call, acceptPendingIntent)

            if (avatar != null) {
                setImageViewBitmap(R.id.notification_thumbnail, avatar)
            }
        }

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentIntent(openAppPendingIntent)
            .setCategory(Notification.CATEGORY_CALL)
            .setCustomContentView(collapsedView)
            .setOngoing(true)
            .setUsesChronometer(callState == Call.STATE_ACTIVE)
            .setChannelId(channelId)
            .setStyle(Notification.DecoratedCustomViewStyle())

        if (isHighPriority) {
            builder.setFullScreenIntent(openAppPendingIntent, true)
        }

        return builder.build()
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
