package com.callscreener

import android.app.Application
import com.callscreener.service.ScreeningNotificationManager

/**
 * Application subclass — creates the notification channel at startup so it's
 * available before the first call is ever screened.
 */
class CallScreenerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ScreeningNotificationManager.createChannel(this)
    }
}
