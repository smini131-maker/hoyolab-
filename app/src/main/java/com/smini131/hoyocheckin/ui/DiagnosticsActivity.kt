package com.smini131.hoyocheckin.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.smini131.hoyocheckin.BuildConfig
import com.smini131.hoyocheckin.R
import com.smini131.hoyocheckin.api.ApiContract
import com.smini131.hoyocheckin.data.AttendanceStateStore
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiagnosticsActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        findViewById<Button>(R.id.refreshDiagnosticsButton).setOnClickListener { refresh() }
        findViewById<Button>(R.id.closeDiagnosticsButton).setOnClickListener { finish() }
        refresh()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun refresh() {
        findViewById<TextView>(R.id.diagnosticsText).text = "예약 상태를 확인하는 중입니다."
        executor.execute {
            val state = AttendanceStateStore(this)
            val morningState = workState(state.morningWorkId)
            val eveningState = workState(state.eveningWorkId)
            val text = buildString {
                appendLine("자동 출석: ${if (state.autoEnabled) "켜짐" else "꺼짐"}")
                appendLine("재로그인 필요: ${if (state.needsLogin) "예" else "아니요"}")
                appendLine()
                appendLine("1차 Worker: $morningState")
                appendLine("1차 예정: ${format(state.nextMorningMillis)}")
                appendLine("1차 작업 ID: ${state.morningWorkId ?: "없음"}")
                appendLine()
                appendLine("2차 Worker: $eveningState")
                appendLine("2차 예정: ${format(state.nextEveningMillis)}")
                appendLine("2차 작업 ID: ${state.eveningWorkId ?: "없음"}")
                appendLine()
                appendLine("최근 결과: ${state.lastResultKind}")
                appendLine("설명: ${state.lastResultMessage}")
                appendLine("안전한 HTTP 상태: ${state.lastHttpStatus.takeIf { it >= 0 } ?: "없음"}")
                appendLine("최근 요청 횟수: ${state.lastRequestCount}")
                appendLine()
                appendLine("앱 버전: ${BuildConfig.VERSION_NAME}")
                appendLine("API 어댑터: ${ApiContract.ADAPTER_VERSION}")
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) findViewById<TextView>(R.id.diagnosticsText).text = text
            }
        }
    }

    private fun workState(id: String?): String {
        if (id == null) return "미예약"
        return try {
            val info = WorkManager.getInstance(this)
                .getWorkInfoById(UUID.fromString(id))
                .get(5, TimeUnit.SECONDS)
            when (info?.state) {
                WorkInfo.State.ENQUEUED -> "예약됨"
                WorkInfo.State.RUNNING -> "실행 중"
                WorkInfo.State.SUCCEEDED -> "최근 실행 완료"
                WorkInfo.State.FAILED -> "실패"
                WorkInfo.State.BLOCKED -> "대기 중"
                WorkInfo.State.CANCELLED -> "취소됨"
                null -> "정보 없음"
            }
        } catch (_: Exception) {
            "확인 실패"
        }
    }

    private fun format(value: Long): String = if (value <= 0L) "없음" else
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
}
