package com.smini131.hoyocheckin.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.smini131.hoyocheckin.R
import com.smini131.hoyocheckin.api.CheckInOutcome
import com.smini131.hoyocheckin.api.OutcomeKind
import com.smini131.hoyocheckin.data.AttendanceStateStore
import com.smini131.hoyocheckin.ui.LoginActivity
import com.smini131.hoyocheckin.ui.MainActivity

class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "출석 결과", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "출석 성공과 이미 완료 결과"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTION, "확인 필요", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "로그인 만료와 자동 출석 최종 실패"
            }
        )
    }

    fun notifyFor(outcome: CheckInOutcome, state: AttendanceStateStore) {
        when {
            outcome.isSuccess && state.successNotifications -> show(
                id = NOTIFICATION_SUCCESS,
                channel = CHANNEL_RESULT,
                title = if (outcome.kind == OutcomeKind.ALREADY_SIGNED ||
                    outcome.kind == OutcomeKind.LOCAL_ALREADY_SUCCESS
                ) "오늘 출석 완료" else "원신 출석 성공",
                text = outcome.userMessage,
                openLogin = false
            )
            outcome.needsLogin && state.failureNotifications -> show(
                id = NOTIFICATION_LOGIN,
                channel = CHANNEL_ACTION,
                title = "HoYoLAB 로그인이 만료되었습니다",
                text = outcome.userMessage,
                openLogin = true
            )
            !outcome.isSuccess && !outcome.shouldRetry && state.failureNotifications -> show(
                id = NOTIFICATION_FAILURE,
                channel = CHANNEL_ACTION,
                title = "자동 출석을 확인해 주세요",
                text = outcome.userMessage,
                openLogin = false
            )
        }
    }

    private fun show(id: Int, channel: String, title: String, text: String, openLogin: Boolean) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val target = if (openLogin) LoginActivity::class.java else MainActivity::class.java
        val intent = Intent(context, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        manager.notify(id, notification)
    }

    companion object {
        private const val CHANNEL_RESULT = "attendance_result"
        private const val CHANNEL_ACTION = "attendance_action"
        private const val NOTIFICATION_SUCCESS = 1001
        private const val NOTIFICATION_LOGIN = 1002
        private const val NOTIFICATION_FAILURE = 1003
    }
}
