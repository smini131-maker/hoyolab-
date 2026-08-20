package com.smini131.hoyocheckin.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.smini131.hoyocheckin.R
import com.smini131.hoyocheckin.data.AttendanceStateStore
import com.smini131.hoyocheckin.work.WorkScheduler

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val state = AttendanceStateStore(this)
        val morning = findViewById<TimePicker>(R.id.morningPicker)
        val evening = findViewById<TimePicker>(R.id.eveningPicker)
        morning.setIs24HourView(true)
        evening.setIs24HourView(true)
        morning.hour = state.morningHour
        morning.minute = state.morningMinute
        evening.hour = state.eveningHour
        evening.minute = state.eveningMinute

        val success = findViewById<SwitchCompat>(R.id.successNotificationSwitch)
        val failure = findViewById<SwitchCompat>(R.id.failureNotificationSwitch)
        val wifi = findViewById<SwitchCompat>(R.id.wifiOnlySwitch)
        success.isChecked = state.successNotifications
        failure.isChecked = state.failureNotifications
        wifi.isChecked = state.wifiOnly

        findViewById<Button>(R.id.saveSettingsButton).setOnClickListener {
            state.morningHour = morning.hour
            state.morningMinute = morning.minute
            state.eveningHour = evening.hour
            state.eveningMinute = evening.minute
            state.successNotifications = success.isChecked
            state.failureNotifications = failure.isChecked
            state.wifiOnly = wifi.isChecked
            if (state.autoEnabled) WorkScheduler(this, state).scheduleAll()
            Toast.makeText(this, "설정을 저장했습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<Button>(R.id.cancelSettingsButton).setOnClickListener { finish() }
    }
}
