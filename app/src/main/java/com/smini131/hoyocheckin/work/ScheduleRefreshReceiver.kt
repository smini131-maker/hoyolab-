package com.smini131.hoyocheckin.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smini131.hoyocheckin.data.AttendanceStateStore
import java.util.concurrent.Executors

class ScheduleRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val state = AttendanceStateStore(context)
                if (intent?.action == Intent.ACTION_TIME_CHANGED ||
                    intent?.action == Intent.ACTION_TIMEZONE_CHANGED ||
                    intent?.action == Intent.ACTION_BOOT_COMPLETED
                ) {
                    state.markClockChanged()
                }
                WorkScheduler(context, state).scheduleAll()
            } finally {
                pending.finish()
                executor.shutdown()
            }
        }
    }
}
