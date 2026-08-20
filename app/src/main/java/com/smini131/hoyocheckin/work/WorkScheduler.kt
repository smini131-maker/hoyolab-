package com.smini131.hoyocheckin.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.smini131.hoyocheckin.data.AttendanceStateStore
import java.util.UUID
import java.util.concurrent.TimeUnit

class WorkScheduler(
    context: Context,
    private val state: AttendanceStateStore = AttendanceStateStore(context),
    private val timeProvider: TimeProvider = SystemTimeProvider
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun scheduleAll() {
        workManager.cancelAllWorkByTag(TAG_AUTOMATIC)
        if (!state.autoEnabled) {
            clearScheduleState()
            return
        }
        scheduleSlot(ScheduleSlot.MORNING)
        scheduleSlot(ScheduleSlot.EVENING)
    }

    fun scheduleNext(slot: ScheduleSlot) {
        if (!state.autoEnabled) return
        scheduleSlot(slot)
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG_AUTOMATIC)
        clearScheduleState()
    }

    private fun scheduleSlot(slot: ScheduleSlot) {
        val now = timeProvider.nowMillis()
        val (hour, minute) = when (slot) {
            ScheduleSlot.MORNING -> state.morningHour to state.morningMinute
            ScheduleSlot.EVENING -> state.eveningHour to state.eveningMinute
        }
        val target = WorkTimeCalculator.nextRun(now, timeProvider.deviceZone(), hour, minute)
        val request = OneTimeWorkRequest.Builder(CheckInWorker::class.java)
            .setInitialDelay((target - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (state.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putString(CheckInWorker.INPUT_SLOT, slot.name).build())
            .addTag(TAG_AUTOMATIC)
            .build()

        workManager.enqueueUniqueWork(
            WorkNameFactory.name(slot, target),
            ExistingWorkPolicy.KEEP,
            request
        )
        storeSchedule(slot, target, request.id)
    }

    private fun storeSchedule(slot: ScheduleSlot, target: Long, id: UUID) {
        when (slot) {
            ScheduleSlot.MORNING -> {
                state.nextMorningMillis = target
                state.morningWorkId = id.toString()
            }
            ScheduleSlot.EVENING -> {
                state.nextEveningMillis = target
                state.eveningWorkId = id.toString()
            }
        }
    }

    private fun clearScheduleState() {
        state.nextMorningMillis = -1L
        state.nextEveningMillis = -1L
        state.morningWorkId = null
        state.eveningWorkId = null
    }

    companion object {
        const val TAG_AUTOMATIC = "automatic-genshin-checkin"
    }
}
