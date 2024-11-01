package org.fossify.phone.receivers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.telecom.TelecomManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.fossify.commons.extensions.getLaunchIntent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.phone.R
import org.fossify.phone.activities.MissedCallNotificationActivity
import org.fossify.phone.helpers.MISSED_CALLS
import org.fossify.phone.helpers.MISSED_CALL_BACK
import org.fossify.phone.helpers.MISSED_CALL_MESSAGE
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.O)
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val notificationManager = context.notificationManager

        when (intent.action) {
            TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION -> {
                val notificationCount = extras.getInt(TelecomManager.EXTRA_NOTIFICATION_COUNT)
                if (notificationCount != 0) {
                    val notificationId = Random.nextInt()
                    val phoneNumber = extras.getString(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
                    createNotificationChannel(context)
                    notificationManager.notify(MISSED_CALLS.hashCode(), getNotificationGroup(context))
                    notificationManager.notify(notificationId, buildNotification(context, notificationId, phoneNumber ?: return))
                }
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        val notificationManager = context.notificationManager
        val channel = NotificationChannel(
            "missed_call_channel",
            context.getString(R.string.missed_call_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun launchIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context, 0, context.getLaunchIntent(), PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNotificationGroup(context: Context): Notification {
        return NotificationCompat.Builder(context, "missed_call_channel")
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setAutoCancel(true)
            .setGroupSummary(true)
            .setGroup(MISSED_CALLS)
            .setContentIntent(launchIntent(context))
            .build()
    }

    private fun buildNotification(context: Context, notificationId: Int, phoneNumber: String): Notification {
        val helper = SimpleContactsHelper(context)
        val name = helper.getNameFromPhoneNumber(phoneNumber)
        val photoUri = helper.getPhotoUriFromPhoneNumber(phoneNumber)

        val callBack = Intent(context, MissedCallNotificationActivity::class.java).apply {
            action = MISSED_CALL_BACK
            putExtra("notificationId", notificationId)
            putExtra("phoneNumber", phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val callBackIntent = PendingIntent.getActivity(
            context, notificationId, callBack, PendingIntent.FLAG_IMMUTABLE
        )

        val smsIntent = Intent(context, MissedCallNotificationActivity::class.java).apply {
            action = MISSED_CALL_MESSAGE
            putExtra("notificationId", notificationId)
            putExtra("phoneNumber", phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val messageIntent = PendingIntent.getActivity(
            context, notificationId, smsIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, "missed_call_channel")
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(context.resources.getQuantityString(R.plurals.missed_calls, 1, 1))
            .setContentText(context.getString(R.string.missed_call_from, name))
            .setLargeIcon(Icon.createWithContentUri(photoUri))
            .setAutoCancel(true)
            .setGroup(MISSED_CALLS)
            .setContentIntent(launchIntent(context))
            .addAction(android.R.drawable.sym_action_call, context.getString(R.string.call_back), callBackIntent)
            .addAction(android.R.drawable.sym_action_chat, context.getString(R.string.message), messageIntent)
            .build()
    }
}
