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
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.models.PhoneNumber
import org.fossify.phone.R
import org.fossify.phone.activities.MissedCallNotificationActivity
import org.fossify.phone.helpers.MISSED_CALLS
import org.fossify.phone.helpers.MISSED_CALL_BACK
import org.fossify.phone.helpers.MISSED_CALL_CANCEL
import org.fossify.phone.helpers.MISSED_CALL_MESSAGE
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.O)
class MissedCallReceiver : BroadcastReceiver() {
    companion object {
        private var notifications = 0
    }

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
                    notifications++
                    notificationManager.notify(MISSED_CALLS.hashCode(), getNotificationGroup(context))
                    notifyMissedCall(context, notificationId, phoneNumber ?: return)
                }
            }

            MISSED_CALL_CANCEL -> {
                val notificationId = intent.extras?.getInt("notificationId", -1) ?: return
                notificationManager.cancel(notificationId)
                notifications--
                if (notifications <= 0) {
                    notificationManager.cancel(MISSED_CALLS.hashCode())
                    context.telecomManager.cancelMissedCallsNotification()
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

    private fun notifyMissedCall(context: Context, notificationId: Int, phoneNumber: String) {
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contactList ->
            val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
            contactList.addAll(privateContacts)
            contactList.sort()
            var phone: PhoneNumber? = null
            val contact = contactList.firstOrNull {
                it.phoneNumbers.any {
                    if (it.value.normalizePhoneNumber() == phoneNumber.normalizePhoneNumber()) {
                        phone = it
                        return@any true
                    }
                    false
                }
            }

            val name = contact?.name ?: phoneNumber
            val photoUri = contact?.photoUri
            var numberLabel = if (contact != null && phone != null && contact.phoneNumbers.size > 1) {
                context.getPhoneNumberTypeText(phone!!.type, phone!!.label)
            } else ""
            if (numberLabel.isNotEmpty()) {
                numberLabel = " - $numberLabel"
            }

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

            val cancelIntent = Intent(context, MissedCallReceiver::class.java).apply {
                action = MISSED_CALL_CANCEL
                putExtra("notificationId", notificationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context, notificationId, cancelIntent, PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, "missed_call_channel")
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentTitle(context.resources.getString(R.string.missed_calls))
                .setContentText(context.getString(R.string.missed_call_from, name) + numberLabel)
                .setAutoCancel(true)
                .setGroup(MISSED_CALLS)
                .setContentIntent(launchIntent(context))
                .addAction(android.R.drawable.sym_action_call, context.getString(R.string.call_back), callBackIntent)
                .addAction(android.R.drawable.sym_action_chat, context.getString(R.string.message), messageIntent)
                .setDeleteIntent(cancelPendingIntent)

            if (!photoUri.isNullOrBlank()) {
                notification.setLargeIcon(Icon.createWithContentUri(photoUri))
            }

            context.notificationManager.notify(notificationId, notification.build())
        }
    }
}
