package org.fossify.phone.extensions

import android.graphics.Color
import org.fossify.commons.extensions.lightenColor

fun Int.adjustSimColorForBackground(bg: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(bg, hsv)
    if (hsv[2] < 0.5) {
        return this.lightenColor(24)
    }
    return this
}
