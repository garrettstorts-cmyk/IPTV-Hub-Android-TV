package com.garrettstorts.iptvhub

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var loadError: View
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var mainFrameLoadFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        webView = findViewById(R.id.web_view)
        loadError = findViewById(R.id.load_error)

        window.statusBarColor = Color.rgb(11, 16, 32)
        window.navigationBarColor = Color.rgb(11, 16, 32)
        hideSystemChrome()

        findViewById<View>(R.id.retry_button).setOnClickListener {
            mainFrameLoadFailed = false
            loadError.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.requestFocus()
            webView.loadUrl(HOME_URL)
        }
        findViewById<View>(R.id.exit_button).setOnClickListener { finish() }

        webView.setBackgroundColor(Color.rgb(11, 16, 32))
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                routeUrl(request.url)

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                routeUrl(Uri.parse(url))

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                mainFrameLoadFailed = false
                loadError.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (!mainFrameLoadFailed) {
                    loadError.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    showLoadError("WebView error ${error.errorCode}: ${error.description}")
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                response: android.webkit.WebResourceResponse
            ) {
                if (request.isForMainFrame && response.statusCode >= 400) {
                    showLoadError("HTTP ${response.statusCode} while loading ${request.url}")
                }
            }
        }

        // Keep playback in WebView; native Media3/ExoPlayer is a future fallback if device HLS support is insufficient.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }

                customView = view
                customViewCallback = callback
                webView.visibility = View.INVISIBLE
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                hideSystemChrome()
            }

            override fun onHideCustomView() {
                leaveFullscreen()
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d(TAG, "Web console ${message.messageLevel()}: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }

        webView.loadUrl(HOME_URL)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (customView != null) {
            leaveFullscreen()
            return
        }

        webView.evaluateJavascript(BACK_SCRIPT) { result ->
            when (result?.trim('"')) {
                "handled" -> Unit
                "root" -> finish()
                "history" -> if (webView.canGoBack()) webView.goBack() else finish()
                else -> {
                    Log.w(TAG, "Web page did not report Back handling; leaving the Activity.")
                    finish()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemChrome()
    }

    override fun onDestroy() {
        if (customView != null) leaveFullscreen()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun routeUrl(uri: Uri): Boolean {
        if (isIptvHubUrl(uri)) return false

        val scheme = uri.scheme?.lowercase()
        if (scheme == "https" || scheme == "http") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (error: ActivityNotFoundException) {
                Log.w(TAG, "No browser can open external URL: $uri", error)
            }
        } else {
            Log.w(TAG, "Blocked unsupported navigation scheme: $scheme")
        }
        return true
    }

    private fun isIptvHubUrl(uri: Uri): Boolean {
        if (!uri.scheme.equals("https", ignoreCase = true) || !uri.host.equals(HOST, ignoreCase = true)) return false
        if ((uri.port != -1 && uri.port != 443) || uri.userInfo != null) return false
        val path = uri.path ?: return false
        return path == APP_PATH || path.startsWith("$APP_PATH/")
    }

    private fun showLoadError(details: String) {
        mainFrameLoadFailed = true
        Log.e(TAG, "Unable to load IPTV Hub: $details")
        webView.visibility = View.GONE
        loadError.visibility = View.VISIBLE
        findViewById<View>(R.id.retry_button).requestFocus()
    }

    private fun leaveFullscreen() {
        val view = customView ?: return
        val callback = customViewCallback
        customView = null
        customViewCallback = null
        root.removeView(view)
        webView.visibility = View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView.requestFocus()
        hideSystemChrome()
        callback?.onCustomViewHidden()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemChrome() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private companion object {
        const val TAG = "IPTVHubTV"
        const val HOST = "garrettstorts-cmyk.github.io"
        const val APP_PATH = "/iptv-hub"
        const val HOME_URL = "https://garrettstorts-cmyk.github.io/iptv-hub/"
        const val BACK_SCRIPT = """
            (function() {
                var event = new KeyboardEvent('keydown', {
                    key: 'Escape',
                    code: 'Escape',
                    keyCode: 27,
                    which: 27,
                    bubbles: true,
                    cancelable: true
                });
                (document.activeElement || document.body).dispatchEvent(event);
                if (event.defaultPrevented) return 'handled';
                var state = window.IPTV_APP_STATE;
                return !state || state.view === 'home' ? 'root' : 'history';
            })();
        """
    }
}
