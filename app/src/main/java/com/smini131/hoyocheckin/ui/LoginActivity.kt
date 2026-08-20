package com.smini131.hoyocheckin.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SafeBrowsingResponse
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smini131.hoyocheckin.BuildConfig
import com.smini131.hoyocheckin.R
import com.smini131.hoyocheckin.api.ApiContract
import com.smini131.hoyocheckin.data.AttendanceStateStore
import com.smini131.hoyocheckin.security.CookieFilter
import com.smini131.hoyocheckin.security.SecureCookieStore
import com.smini131.hoyocheckin.work.WorkScheduler
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val executor = Executors.newSingleThreadExecutor()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_login)
        webView = findViewById(R.id.loginWebView)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = SecureLoginWebViewClient()
        webView.loadUrl(ApiContract.LOGIN_URL)

        findViewById<Button>(R.id.confirmLoginButton).setOnClickListener { confirmLogin() }
        findViewById<Button>(R.id.cancelLoginButton).setOnClickListener { finish() }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        destroyWebView()
        super.onDestroy()
    }

    private fun confirmLogin() {
        val manager = CookieManager.getInstance()
        manager.flush()
        val filtered = CookieFilter.extract(
            manager.getCookie(ApiContract.API_ORIGIN),
            manager.getCookie("https://www.hoyolab.com"),
            manager.getCookie("https://act.hoyolab.com"),
            manager.getCookie("https://account.hoyoverse.com")
        )
        if (filtered == null) {
            findViewById<TextView>(R.id.loginHint).text =
                "로그인 쿠키를 확인하지 못했습니다. 공식 페이지에서 로그인을 완료한 뒤 다시 눌러 주세요."
            Toast.makeText(this, "아직 로그인 완료를 확인하지 못했습니다.", Toast.LENGTH_LONG).show()
            return
        }

        findViewById<Button>(R.id.confirmLoginButton).isEnabled = false
        executor.execute {
            val saved = runCatching { SecureCookieStore(this).save(filtered) }.isSuccess
            if (saved) {
                val state = AttendanceStateStore(this)
                state.setNeedsLogin(false)
                if (state.autoEnabled) WorkScheduler(this, state).scheduleAll()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (saved) {
                    setResult(RESULT_OK)
                    Toast.makeText(this, "로그인 정보를 안전하게 저장했습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    findViewById<Button>(R.id.confirmLoginButton).isEnabled = true
                    Toast.makeText(this, "기기 보안 저장소에 저장하지 못했습니다.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun destroyWebView() {
        if (!::webView.isInitialized) return
        CookieManager.getInstance().apply {
            setAcceptThirdPartyCookies(webView, false)
            removeAllCookies(null)
            flush()
        }
        webView.stopLoading()
        webView.clearHistory()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.removeAllViews()
        webView.destroy()
    }

    private inner class SecureLoginWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == "https" && isAllowedHost(uri.host)) return false
            if (uri.scheme == "http" || uri.scheme == "https") {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }
            return true
        }

        override fun onSafeBrowsingHit(
            view: WebView?,
            request: WebResourceRequest?,
            threatType: Int,
            callback: SafeBrowsingResponse
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                callback.backToSafety(true)
            } else {
                view?.stopLoading()
            }
            Toast.makeText(this@LoginActivity, "안전하지 않은 페이지 이동을 차단했습니다.", Toast.LENGTH_LONG).show()
        }

        private fun isAllowedHost(host: String?): Boolean {
            val value = host?.lowercase() ?: return false
            return ALLOWED_BASE_DOMAINS.any { value == it || value.endsWith(".$it") }
        }
    }

    companion object {
        private val ALLOWED_BASE_DOMAINS = setOf("hoyolab.com", "hoyoverse.com")
    }
}
