package com.smini131.hoyocheckin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.smini131.hoyocheckin.data.AttendanceStateStore
import com.smini131.hoyocheckin.work.WorkScheduler
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkSchedulerInstrumentedTest {
    @Test
    fun schedulesTwoUniqueOneTimeWorkersWithoutDuplicateRegistration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.ERROR)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        val state = AttendanceStateStore(context).apply { autoEnabled = true }

        WorkScheduler(context, state).scheduleAll()
        WorkScheduler(context, state).scheduleAll()

        assertNotNull(state.morningWorkId)
        assertNotNull(state.eveningWorkId)
        WorkManager.getInstance(context).cancelAllWork()
    }
}
