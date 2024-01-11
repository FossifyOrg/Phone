package org.fossify.phone.extensions

import android.graphics.Rect
import android.view.View

val View.boundingBox
    get() = Rect().also { getGlobalVisibleRect(it) }
