package com.smini131.hoyocheckin.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.smini131.hoyocheckin.R
import com.smini131.hoyocheckin.api.HoyolabCheckInClient
import com.smini131.hoyocheckin.data.AttendanceStateStore
import com.smini131.hoyocheckin.security.SecureCookieStore
import com.smini131.hoyocheckin.work.AttendanceRunner
import com.smini131.hoyocheckin.work.ServerDayPolicy
import com.smini131.hoyocheckin.work.Trigger
import com.smini131.hoyocheckin.work.WorkScheduler
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var state: AttendanceStateStore
    private lateinit var cookieStore: SecureCookieStore
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var autoSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        state = AttendanceStateStore(this)
        cookieStore = SecureCookieStore(this)
        bindButtons()
        requestNotificationPermissionIfNeeded()

        if (state.autoEnabled && (state.morningWorkId == null || state.eveningWorkId == null)) {
            WorkScheduler(this, state).scheduleAll()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun bindButtons() {
        autoSwitch = findViewById(R.id.autoSwitch)
        autoSwitch.setOnCheckedChangeListener { _, enabled ->
            if (state.autoEnabled == enabled) return@setOnCheckedChangeListener
            state.autoEnabled = enabled
            if (enabled) WorkScheduler(this, state).scheduleAll()
            else WorkScheduler(this, state).cancelAll()
            refreshStatus()
        }
        findViewById<Button>(R.id.loginButton).setOnClickListener { openLogin() }
        findViewById<Button>(R.id.refreshLoginButton).setOnClickListener { openLogin() }
        findViewById<Button>(R.id.checkNowButton).setOnClickListener { runManualCheck() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.diagnosticsButton).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { confirmLogout() }
    }

    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
    }

    private fun runManualCheck() {
        val button = findViewById<Button>(R.id.checkNowButton)
        button.isEnabled = false
        button.text = "출석 상태 확인 중…"
        executor.execute {
            val outcome = AttendanceRunner(
                HoyolabCheckInClient(),
                cookieStore,
                state
            ).run(Trigger.MANUAL)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                button.isEnabled = true
                button.text = "지금 출석 확인"
                Toast.makeText(this, outcome.userMessage, Toast.LENGTH_LONG).show()
                refreshStatus()
            }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("로그아웃")
            .setMessage("암호화된 HoYoLAB 인증정보와 출석 상태를 이 기기에서 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                executor.execute {
                    cookieStore.clear()
                    state.autoEnabled = false
                    WorkScheduler(this, state).cancelAll()
                    state.clearAttendanceAndSchedules()
                    runOnUiThread {
                        Toast.makeText(this, "인증정보를 삭제했습니다.", Toast.LENGTH_SHORT).show()
                        refreshStatus()
                    }
                }
            }
            .show()
    }

    private fun refreshStatus() {
        val loggedIn = cookieStore.exists() && !state.needsLogin
        findViewById<TextView>(R.id.loginStatus).text = when {
            state.needsLogin -> "로그인 상태: 만료됨 · 갱신 필요"
            loggedIn -> "로그인 상태: 기기에 암호화되어 저장됨"
            else -> "로그인 상태: 로그인 필요"
        }
        val today = ServerDayPolicy.dayKey(System.currentTimeMillis())
        findViewById<TextView>(R.id.todayStatus).text =
            "오늘 출석: ${if (state.successDay == today) "완료" else "확인 전"}"
        findViewById<TextView>(R.id.lastAttempt).text = "마지막 시도: ${formatTime(state.lastAttemptMillis)}"
        findViewById<TextView>(R.id.lastSuccess).text = "마지막 성공: ${formatTime(state.lastSuccessMillis)}"
        findViewById<TextView>(R.id.lastResult).text = "실행 결과: ${state.lastResultMessage}"
        findViewById<TextView>(R.id.nextMorning).text = "다음 1차 실행: ${formatTime(state.nextMorningMillis)}"
        findViewById<TextView>(R.id.nextEvening).text = "다음 2차 실행: ${formatTime(state.nextEveningMillis)}"
        if (autoSwitch.isChecked != state.autoEnabled) autoSwitch.isChecked = state.autoEnabled
    }

    private fun formatTime(value: Long): String = if (value <= 0L) {
        "없음"
    } else {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 40
    }
}
