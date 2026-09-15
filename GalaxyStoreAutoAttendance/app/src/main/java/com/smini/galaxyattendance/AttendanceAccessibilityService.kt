package com.smini.galaxyattendance

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class AttendanceAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var runStartedAt = 0L
    private var lastActionAt = 0L
    private var stage = Stage.ENTRY
    private var helperClicked = false

    override fun onServiceConnected() {
        instance = this
        AppLog.write(this, "접근성 서비스 연결됨")
        if (AppPrefs.prefs(this).getBoolean(AppPrefs.KEY_PENDING_RUN, false)) beginRun()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() { }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != AttendanceRunnerService.STORE_PACKAGE) return
        if (!AppPrefs.prefs(this).getBoolean(AppPrefs.KEY_PENDING_RUN, false)) return
        if (runStartedAt == 0L) beginRun()
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, 350)
    }

    private val scanRunnable = Runnable { scan() }

    private fun beginRun() {
        runStartedAt = System.currentTimeMillis()
        lastActionAt = 0L
        stage = Stage.ENTRY
        helperClicked = false
        AppLog.write(this, "자동 탐색 세션 시작")
        handler.postDelayed(scanRunnable, 800)
    }

    private fun scan() {
        val root = rootInActiveWindow ?: return retry("활성 창 없음")
        if (System.currentTimeMillis() - runStartedAt > 35_000L) {
            val prefs = AppPrefs.prefs(this)
            if (prefs.getBoolean(AppPrefs.KEY_COORDINATE, false)) {
                val x = prefs.getInt(AppPrefs.KEY_X, -1)
                val y = prefs.getInt(AppPrefs.KEY_Y, -1)
                if (x >= 0 && y >= 0) {
                    AppLog.write(this, "UI 탐색 시간 초과; 사용자 지정 좌표 클릭 시도 ($x,$y)")
                    gestureClick(x.toFloat(), y.toFloat())
                    finish(false, "좌표 클릭 수행 - 결과 확인 필요")
                    return
                }
            }
            finish(false, "35초 동안 출석 UI를 찾지 못함")
            return
        }

        dumpCompact(root)
        when (stage) {
            Stage.ENTRY -> {
                if (findAndClick(root, listOf("출석체크", "출석 체크", "매일 출석", "출석 이벤트", "attendance", "check-in"), true)) {
                    stage = Stage.CHECKIN
                    helperClicked = true
                    return scheduleScan(1200)
                }
                if (!helperClicked && findAndClick(root, listOf("이벤트", "혜택", "쿠폰"), true)) {
                    helperClicked = true
                    return scheduleScan(1400)
                }
                if (findAndClick(root, listOf("오늘 출석", "출석하기", "출석하기 버튼", "출석 체크하기", "체크인"), true)) {
                    stage = Stage.VERIFY
                    return scheduleScan(1200)
                }
                retry("출석 진입 UI 탐색 중")
            }
            Stage.CHECKIN -> {
                if (hasAny(root, SUCCESS_WORDS)) return finish(true, "이미 오늘 출석 완료 상태")
                if (findAndClick(root, listOf("오늘 출석", "출석하기", "출석 체크하기", "체크인", "출석 버튼"), true)) {
                    stage = Stage.VERIFY
                    return scheduleScan(1400)
                }
                retry("출석 실행 버튼 탐색 중")
            }
            Stage.VERIFY -> {
                if (hasAny(root, SUCCESS_WORDS)) return finish(true, "출석 완료 문구 확인")
                retry("출석 완료 여부 확인 중")
            }
        }
    }

    private fun retry(reason: String) {
        if (System.currentTimeMillis() - lastActionAt > 3000) AppLog.write(this, reason)
        scheduleScan(850)
    }

    private fun scheduleScan(delay: Long) {
        lastActionAt = System.currentTimeMillis()
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, delay)
    }

    private fun findAndClick(root: AccessibilityNodeInfo, needles: List<String>, partial: Boolean): Boolean {
        val byId = allNodes(root).firstOrNull { n ->
            val id = n.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty()
            id.contains("attendance") || id.contains("checkin") || id.contains("check_in")
        }
        if (byId != null && clickNodeOrParent(byId, "viewId=${byId.viewIdResourceName}")) return true

        val nodes = allNodes(root)
        for (needle in needles) {
            val exact = nodes.firstOrNull { normalize(it.text) == normalize(needle) }
            if (exact != null && clickNodeOrParent(exact, "정확 텍스트=${exact.text}")) return true
        }
        if (partial) {
            for (needle in needles) {
                val part = nodes.firstOrNull { normalize(it.text).contains(normalize(needle)) }
                if (part != null && clickNodeOrParent(part, "부분 텍스트=${part.text}")) return true
            }
        }

        for (needle in needles) {
            val desc = nodes.firstOrNull {
                val d = normalize(it.contentDescription)
                d == normalize(needle) || (partial && d.contains(normalize(needle)))
            }
            if (desc != null && clickNodeOrParent(desc, "contentDescription=${desc.contentDescription}")) return true
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo, why: String): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(6) {
            if (n?.isClickable == true && n?.isEnabled == true) {
                val ok = n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                AppLog.write(this, "클릭 ${if (ok) "성공" else "실패"}: $why")
                return ok
            }
            n = n?.parent
        }
        return false
    }

    private fun hasAny(root: AccessibilityNodeInfo, needles: List<String>): Boolean {
        return allNodes(root).any { n ->
            val t = normalize(n.text) + " " + normalize(n.contentDescription)
            needles.any { t.contains(normalize(it)) }
        }
    }

    private fun allNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(128)
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 18 || out.size > 500) return
            out.add(n)
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return out
    }

    private fun normalize(cs: CharSequence?): String = cs?.toString()?.trim()?.lowercase(Locale.KOREA).orEmpty()

    private fun dumpCompact(root: AccessibilityNodeInfo) {
        if (System.currentTimeMillis() - lastDumpAt < 2500) return
        lastDumpAt = System.currentTimeMillis()
        val interesting = allNodes(root).mapNotNull { n ->
            val t = n.text?.toString()?.trim().orEmpty()
            val d = n.contentDescription?.toString()?.trim().orEmpty()
            if (t.isBlank() && d.isBlank()) null else "[${n.viewIdResourceName ?: "-"}] t='$t' d='$d' click=${n.isClickable}"
        }.take(25)
        if (interesting.isNotEmpty()) AppLog.write(this, "화면 노드: ${interesting.joinToString(" | ")}")
    }

    private fun gestureClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build(), null, null)
    }

    private fun finish(success: Boolean, message: String) {
        AppLog.write(this, "${if (success) "성공" else "종료"}: $message")
        AppPrefs.prefs(this).edit().putBoolean(AppPrefs.KEY_PENDING_RUN, false).apply()
        handler.removeCallbacks(scanRunnable)
        runStartedAt = 0L
    }

    enum class Stage { ENTRY, CHECKIN, VERIFY }

    companion object {
        @Volatile private var instance: AttendanceAccessibilityService? = null
        @Volatile private var lastDumpAt: Long = 0L
        private val SUCCESS_WORDS = listOf("출석 완료", "오늘 출석 완료", "출석했습니다", "출석 성공", "내일 또", "already checked")

        fun prepareRun() { instance?.beginRun() }

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AttendanceAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
