package org.fossify.phone.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.fossify.commons.extensions.getLaunchIntent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.phone.R
import org.fossify.phone.helpers.MISSED_CALLS
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.O)
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) {
            val extras = intent.extras!!
            val notificationCount = extras.getInt(TelecomManager.EXTRA_NOTIFICATION_COUNT)
            if (notificationCount != 0) {
                val phoneNumber = extras.getString(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)!!
                val helper = SimpleContactsHelper(context)
                val name = helper.getNameFromPhoneNumber(phoneNumber)
                val photoUri = helper.getPhotoUriFromPhoneNumber(phoneNumber)

                val notificationManager = context.notificationManager
                val channel = NotificationChannel(
                    "missed_call_channel",
                    context.getString(R.string.missed_call_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)

                val pendingIntent = PendingIntent.getActivity(
                    context, 0, context.getLaunchIntent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val callBack = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.fromParts("tel", phoneNumber, null)
                }
                val callBackIntent = PendingIntent.getActivity(
                    context, 0, callBack, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.fromParts("sms", phoneNumber, null)
                }
                val messageIntent = PendingIntent.getActivity(
                    context, 0, smsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val cancel = Intent("android.intent.action.CANCEL_MISSED_CALLS_NOTIFICATION")
                val cancelIntent = PendingIntent.getActivity(
                    context, 0, cancel, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, "missed_call_channel")
                    .setSmallIcon(android.R.drawable.sym_call_missed)
                    .setContentTitle(context.getString(R.string.missed_call))
                    .setContentText(context.getString(R.string.missed_call_from, name))
                    .setLargeIcon(Icon.createWithContentUri(photoUri))
                    .setAutoCancel(true)
                    .setGroup(MISSED_CALLS)
                    .setContentIntent(pendingIntent)
                    .addAction(0, context.getString(R.string.call_back), callBackIntent)
                    .addAction(0, context.getString(R.string.message), messageIntent)
                    .setDeleteIntent(cancelIntent)
                    .build()
                notificationManager.notify(Random.nextInt(), notification)
            }
        }
    }
}
