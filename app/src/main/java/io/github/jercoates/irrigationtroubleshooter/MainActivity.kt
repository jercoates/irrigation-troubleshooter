package io.github.jercoates.irrigationtroubleshooter

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

/**
 * Hosts the bundled troubleshooting guide (assets/index.html) in a WebView served from a
 * proper https origin by WebViewAssetLoader. No network permission exists, and every
 * navigation away from the bundled origin is refused.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var store: StateStore
    private var pageReady = false
    private var onResult = false
    private var pendingPath: List<String>? = null
    private lateinit var backCallback: OnBackPressedCallback
    private var startDialog: AlertDialog? = null
    private val readyTimeout = Runnable { if (!pageReady) onStartFailed() }

    private val searchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val path = r.data?.getStringArrayListExtra(SearchActivity.EXTRA_PATH)
        Protocol.sanitizeStack(path)?.let {
            // After an activity recreation the page may not be ready yet; apply on "ready" instead.
            if (pageReady) jumpTo(it) else pendingPath = it
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = StateStore(this)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setTitle(R.string.app_name)

        val appBar = findViewById<View>(R.id.appbar)
        val content = findViewById<View>(R.id.content)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            appBar.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            content.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        applyKeepScreenOn()

        webView = findViewById(R.id.webview)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.webview_too_old)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .show()
            return
        }
        setUpWebView()

        // Enabled only while the page has somewhere to go back to (a previous step, or an open
        // disclaimer). Otherwise the system handles Back, which keeps Android's back-to-home animation.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("androidBack()", null)
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        webView.loadUrl(START_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        webView.visibility = View.INVISIBLE   // revealed once position + gate state are restored
        // Safety net: never leave the user on a blank screen if the page fails to start.
        webView.postDelayed(readyTimeout, READY_TIMEOUT_MS)
        webView.settings.apply {
            javaScriptEnabled = true          // the bundled guide is a script
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            builtInZoomControls = false
        }

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                // Serve only bundled assets; everything else gets an empty 403.
                loader.shouldInterceptRequest(request.url)
                    ?: WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Position is already persisted; restart cleanly and resume.
                // Count real crashes only; the system also reclaims renderers for memory.
                if (detail.didCrash() && ++renderCrashes > MAX_RENDER_CRASHES) finish() else recreate()
                return true
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, setOf(ORIGIN)) { _, message, sourceOrigin, isMainFrame, _ ->
                if (isMainFrame && sourceOrigin.toString() == ORIGIN) onMessage(message)
            }
        }
    }

    private fun onMessage(message: WebMessageCompat) {
        when (val m = Protocol.parse(message.data)) {
            BridgeMessage.Ready -> {
                val stack = Protocol.stackToJs(pendingPath ?: store.stack)
                pendingPath = null
                renderCrashes = 0
                startDialog?.dismiss()
                webView.evaluateJavascript("androidInit($stack, ${store.acknowledged})") {
                    pageReady = true
                    webView.visibility = View.VISIBLE
                }
            }
            BridgeMessage.Ack -> store.acknowledged = true
            is BridgeMessage.State -> {
                backCallback.isEnabled = m.canBack
                store.stack = m.stack
                supportActionBar?.subtitle = m.crumb.ifEmpty { null }
                if (onResult != m.isEnd) { onResult = m.isEnd; invalidateOptionsMenu() }
            }
            null -> Unit // malformed: ignore
        }
    }

    private fun js(code: String, callback: ((String?) -> Unit)? = null) {
        if (pageReady) webView.evaluateJavascript(code, callback)
    }

    private fun jumpTo(path: List<String>) {
        js("androidGoto(${Protocol.stackToJs(path)})") {
            if (it != "true") Toast.makeText(this, R.string.jump_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onStartFailed() {
        if (isFinishing || isDestroyed) return
        startDialog = AlertDialog.Builder(this)
            .setMessage(R.string.start_failed)
            .setCancelable(false)
            .setPositiveButton(R.string.retry) { _, _ -> recreate() }
            .setNegativeButton(R.string.close) { _, _ -> finish() }
            .show()
    }

    private fun applyKeepScreenOn() {
        if (store.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_share)?.isVisible = onResult
        menu.findItem(R.id.action_keep_on)?.isChecked = store.keepScreenOn
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_search -> { searchLauncher.launch(Intent(this, SearchActivity::class.java)); true }
        R.id.action_share -> { shareResult(); true }
        R.id.action_start_over -> { js("androidStartOver()"); true }
        R.id.action_meter -> { startActivity(Intent(this, HelpActivity::class.java)); true }
        R.id.action_disclaimer -> { js("androidShowDisclaimer()"); true }
        R.id.action_keep_on -> {
            store.keepScreenOn = !store.keepScreenOn
            item.isChecked = store.keepScreenOn
            applyKeepScreenOn()
            true
        }
        R.id.action_about -> { showAbout(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun shareResult() {
        js("androidSummary()") { raw ->
            val text = Protocol.decodeJsString(raw) ?: return@js
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_chooser)))
        }
    }

    private fun showAbout() {
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.about_body, version))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onResume() { super.onResume(); if (::webView.isInitialized) webView.onResume() }
    override fun onPause() { if (::webView.isInitialized) webView.onPause(); super.onPause() }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.removeCallbacks(readyTimeout)
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private companion object {
        const val READY_TIMEOUT_MS = 8000L
        const val MAX_RENDER_CRASHES = 2
        var renderCrashes = 0
        const val BRIDGE_NAME = "androidBridge"
        const val ORIGIN = "https://appassets.androidplatform.net"
        const val START_URL = "$ORIGIN/assets/index.html"
    }
}
