package org.fossify.phone.extensions

import android.content.Context
import android.text.format.DateFormat
import org.fossify.commons.extensions.getTimeFormat
import java.util.Calendar
import java.util.Locale

fun Long.formatTime(context: Context): String {
    val cal = Calendar.getInstance(Locale.ENGLISH)
    cal.timeInMillis = this
    return DateFormat.format(context.getTimeFormat(), cal).toString()
}
