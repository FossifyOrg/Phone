package org.fossify.phone

import android.app.Application
import org.fossify.commons.extensions.checkUseEnglish

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
    }
}
