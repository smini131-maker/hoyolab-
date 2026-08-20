package com.smini131.hoyocheckin

import android.app.Application
import android.os.StrictMode
import com.smini131.hoyocheckin.util.NotificationHelper

class CheckInApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).createChannels()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
