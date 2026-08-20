package com.smini131.hoyocheckin.data

import android.content.Context
import com.smini131.hoyocheckin.api.CheckInOutcome

class AttendanceStateStore(context: Context) : AttendanceRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateIfNeeded()
    }

    override fun isSuccessfulFor(dayKey: String): Boolean = successDay == dayKey

    override fun canUseLocalFastPath(): Boolean = !preferences.getBoolean(KEY_CLOCK_CHANGED, false)

    override fun markClockChanged() {
        preferences.edit().putBoolean(KEY_CLOCK_CHANGED, true).apply()
    }

    override fun clearClockChanged() {
        preferences.edit().putBoolean(KEY_CLOCK_CHANGED, false).apply()
    }

    override fun recordAttempt(outcome: CheckInOutcome, attemptedAtMillis: Long) {
        preferences.edit()
            .putLong(KEY_LAST_ATTEMPT, attemptedAtMillis)
            .putString(KEY_LAST_RESULT_KIND, outcome.kind.name)
            .putString(KEY_LAST_RESULT_MESSAGE, outcome.userMessage)
            .putInt(KEY_LAST_HTTP_STATUS, outcome.httpStatus ?: -1)
            .putInt(KEY_LAST_REQUEST_COUNT, outcome.networkRequestCount)
            .apply()
    }

    override fun markSuccess(
        dayKey: String,
        successAtMillis: Long,
        serverEpochMillis: Long?
    ): Boolean = preferences.edit()
        .putString(KEY_SUCCESS_DAY, dayKey)
        .putLong(KEY_LAST_SUCCESS, successAtMillis)
        .putLong(KEY_LAST_SERVER_TIME, serverEpochMillis ?: -1L)
        .putBoolean(KEY_NEEDS_LOGIN, false)
        .putBoolean(KEY_CLOCK_CHANGED, false)
        .commit()

    override fun setNeedsLogin(value: Boolean) {
        preferences.edit().putBoolean(KEY_NEEDS_LOGIN, value).apply()
    }

    var autoEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTO_ENABLED, false)
        set(value) { preferences.edit().putBoolean(KEY_AUTO_ENABLED, value).apply() }

    var morningHour: Int
        get() = preferences.getInt(KEY_MORNING_HOUR, 8)
        set(value) { preferences.edit().putInt(KEY_MORNING_HOUR, value).apply() }
    var morningMinute: Int
        get() = preferences.getInt(KEY_MORNING_MINUTE, 0)
        set(value) { preferences.edit().putInt(KEY_MORNING_MINUTE, value).apply() }
    var eveningHour: Int
        get() = preferences.getInt(KEY_EVENING_HOUR, 20)
        set(value) { preferences.edit().putInt(KEY_EVENING_HOUR, value).apply() }
    var eveningMinute: Int
        get() = preferences.getInt(KEY_EVENING_MINUTE, 0)
        set(value) { preferences.edit().putInt(KEY_EVENING_MINUTE, value).apply() }
    var successNotifications: Boolean
        get() = preferences.getBoolean(KEY_SUCCESS_NOTIFICATIONS, true)
        set(value) { preferences.edit().putBoolean(KEY_SUCCESS_NOTIFICATIONS, value).apply() }
    var failureNotifications: Boolean
        get() = preferences.getBoolean(KEY_FAILURE_NOTIFICATIONS, true)
        set(value) { preferences.edit().putBoolean(KEY_FAILURE_NOTIFICATIONS, value).apply() }
    var wifiOnly: Boolean
        get() = preferences.getBoolean(KEY_WIFI_ONLY, false)
        set(value) { preferences.edit().putBoolean(KEY_WIFI_ONLY, value).apply() }

    var nextMorningMillis: Long
        get() = preferences.getLong(KEY_NEXT_MORNING, -1L)
        set(value) { preferences.edit().putLong(KEY_NEXT_MORNING, value).apply() }
    var nextEveningMillis: Long
        get() = preferences.getLong(KEY_NEXT_EVENING, -1L)
        set(value) { preferences.edit().putLong(KEY_NEXT_EVENING, value).apply() }
    var morningWorkId: String?
        get() = preferences.getString(KEY_MORNING_WORK_ID, null)
        set(value) { preferences.edit().putString(KEY_MORNING_WORK_ID, value).apply() }
    var eveningWorkId: String?
        get() = preferences.getString(KEY_EVENING_WORK_ID, null)
        set(value) { preferences.edit().putString(KEY_EVENING_WORK_ID, value).apply() }

    val successDay: String? get() = preferences.getString(KEY_SUCCESS_DAY, null)
    val lastAttemptMillis: Long get() = preferences.getLong(KEY_LAST_ATTEMPT, -1L)
    val lastSuccessMillis: Long get() = preferences.getLong(KEY_LAST_SUCCESS, -1L)
    val lastResultKind: String get() = preferences.getString(KEY_LAST_RESULT_KIND, "NONE") ?: "NONE"
    val lastResultMessage: String get() =
        preferences.getString(KEY_LAST_RESULT_MESSAGE, "아직 실행 기록이 없습니다.") ?: "아직 실행 기록이 없습니다."
    val lastHttpStatus: Int get() = preferences.getInt(KEY_LAST_HTTP_STATUS, -1)
    val lastRequestCount: Int get() = preferences.getInt(KEY_LAST_REQUEST_COUNT, 0)
    val needsLogin: Boolean get() = preferences.getBoolean(KEY_NEEDS_LOGIN, false)

    fun clearAttendanceAndSchedules() {
        val settings = mapOf(
            KEY_MORNING_HOUR to morningHour,
            KEY_MORNING_MINUTE to morningMinute,
            KEY_EVENING_HOUR to eveningHour,
            KEY_EVENING_MINUTE to eveningMinute,
            KEY_SUCCESS_NOTIFICATIONS to successNotifications,
            KEY_FAILURE_NOTIFICATIONS to failureNotifications,
            KEY_WIFI_ONLY to wifiOnly
        )
        preferences.edit().clear().commit()
        val editor = preferences.edit().putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
        settings.forEach { (key, value) ->
            when (value) {
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
            }
        }
        editor.apply()
    }

    private fun migrateIfNeeded() {
        val version = preferences.getInt(KEY_SCHEMA_VERSION, 0)
        if (version < 1) {
            val oldSuccess = preferences.getString("last_success_date", null)
            val editor = preferences.edit()
            if (oldSuccess != null && successDay == null) editor.putString(KEY_SUCCESS_DAY, oldSuccess)
            editor.remove("last_success_date")
                .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                .commit()
        }
    }

    companion object {
        private const val PREFS_NAME = "attendance_state"
        private const val CURRENT_SCHEMA_VERSION = 1
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_AUTO_ENABLED = "auto_enabled"
        private const val KEY_MORNING_HOUR = "morning_hour"
        private const val KEY_MORNING_MINUTE = "morning_minute"
        private const val KEY_EVENING_HOUR = "evening_hour"
        private const val KEY_EVENING_MINUTE = "evening_minute"
        private const val KEY_SUCCESS_NOTIFICATIONS = "success_notifications"
        private const val KEY_FAILURE_NOTIFICATIONS = "failure_notifications"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_SUCCESS_DAY = "success_day"
        private const val KEY_LAST_ATTEMPT = "last_attempt"
        private const val KEY_LAST_SUCCESS = "last_success"
        private const val KEY_LAST_SERVER_TIME = "last_server_time"
        private const val KEY_LAST_RESULT_KIND = "last_result_kind"
        private const val KEY_LAST_RESULT_MESSAGE = "last_result_message"
        private const val KEY_LAST_HTTP_STATUS = "last_http_status"
        private const val KEY_LAST_REQUEST_COUNT = "last_request_count"
        private const val KEY_NEEDS_LOGIN = "needs_login"
        private const val KEY_CLOCK_CHANGED = "clock_changed"
        private const val KEY_NEXT_MORNING = "next_morning"
        private const val KEY_NEXT_EVENING = "next_evening"
        private const val KEY_MORNING_WORK_ID = "morning_work_id"
        private const val KEY_EVENING_WORK_ID = "evening_work_id"
    }
}
