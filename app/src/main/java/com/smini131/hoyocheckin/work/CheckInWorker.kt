package com.smini131.hoyocheckin.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.smini131.hoyocheckin.api.HoyolabCheckInClient
import com.smini131.hoyocheckin.api.OutcomeKind
import com.smini131.hoyocheckin.data.AttendanceStateStore
import com.smini131.hoyocheckin.security.SecureCookieStore
import com.smini131.hoyocheckin.util.NotificationHelper

class CheckInWorker(
    appContext: Context,
    parameters: WorkerParameters
) : Worker(appContext, parameters) {

    override fun doWork(): Result {
        val state = AttendanceStateStore(applicationContext)
        val slot = inputData.getString(INPUT_SLOT)
            ?.let { runCatching { ScheduleSlot.valueOf(it) }.getOrNull() }

        if (!state.autoEnabled) return Result.success()

        return try {
            val outcome = AttendanceRunner(
                HoyolabCheckInClient(),
                SecureCookieStore(applicationContext),
                state
            ).run(Trigger.AUTOMATIC)

            NotificationHelper(applicationContext).notifyFor(outcome, state)
            if (outcome.shouldRetry && runAttemptCount < MAX_RETRY_COUNT) Result.retry()
            else Result.success()
        } finally {
            if (slot != null) WorkScheduler(applicationContext, state).scheduleNext(slot)
        }
    }

    companion object {
        const val INPUT_SLOT = "schedule_slot"
        private const val MAX_RETRY_COUNT = 2
    }
}
