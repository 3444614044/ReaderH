package com.readerh.app.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import com.readerh.app.Prefs
import com.readerh.app.R
import com.readerh.app.library.LibraryActivity
import com.readerh.app.reader.ReaderActivity
import com.readerh.app.settings.SettingsActivity
import com.readerh.app.util.ExtractResult
import com.readerh.app.util.Readability
import com.readerh.app.util.SearchEngines
import com.readerh.app.util.toast

/**
 * 内置浏览器：加载网页并在识别到正文后提供「阅读模式」入口。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var btnEngine: TextView
    private lateinit var progress: ProgressBar
    private lateinit var fabRead: View

    private var lastResult: ExtractResult? = null
    private var lastUrl: String = ""

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
        private const val MOBILE_UA_SUFFIX = " ReaderH/1.0"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        etUrl = findViewById(R.id.etUrl)
        btnEngine = findViewById(R.id.btnEngine)
        progress = findViewById(R.id.progress)
        fabRead = findViewById(R.id.fabRead)

        setupWebView()
        setupBars()

        val fromView = intent?.dataString
        load(if (fromView.isNullOrBlank()) getString(R.string.home_url) else fromView)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val u = intent.dataString
        if (!u.isNullOrBlank()) load(u)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = Prefs.javascript
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        // 2 = WebSettings.MIXED_CONTENT_COMPATIBILITY（部分 SDK stub 未导出该常量，直接用字面量）
        s.mixedContentMode = 2
        if (Prefs.desktopUa) s.userAgentString = DESKTOP_UA
        else s.userAgentString = s.userAgentString + MOBILE_UA_SUFFIX

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                lastUrl = url
                etUrl.setText(url)
                progress.visibility = View.GONE
                detectArticle(view)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
    }

    private fun setupBars() {
        updateEngineBadge()
        btnEngine.setOnClickListener { showEngineMenu(it) }
        findViewById<ImageButton>(R.id.btnGo).setOnClickListener {
            load(etUrl.text.toString())
        }
        etUrl.setOnEditorActionListener { _, _, _ ->
            load(etUrl.text.toString())
            true
        }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<ImageButton>(R.id.btnHome).setOnClickListener {
            load(getString(R.string.home_url))
        }
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btnReadMode).setOnClickListener { openReader() }
        findViewById<ImageButton>(R.id.btnLibrary).setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        fabRead.setOnClickListener { openReader() }
    }

    private fun detectArticle(view: WebView) {
        if (!Prefs.javascript) {
            fabRead.visibility = View.GONE
            return
        }
        view.evaluateJavascript(Readability.script(this)) { raw ->
            val r = Readability.parse(raw)
            lastResult = if (r.isEmpty) null else r
            fabRead.visibility = if (r.isEmpty) View.GONE else View.VISIBLE
        }
    }

    private fun openReader() {
        val r = lastResult
        if (r == null) {
            toast(getString(R.string.toast_no_content))
            return
        }
        startActivity(Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_URL, lastUrl)
            putExtra(ReaderActivity.EXTRA_TITLE, r.title.ifBlank { webView.title ?: "" })
            putExtra(ReaderActivity.EXTRA_HTML, r.content)
            putExtra(ReaderActivity.EXTRA_NEXT, r.nextUrl)
        })
    }

    private fun load(input: String) {
        lastResult = null
        fabRead.visibility = View.GONE
        webView.loadUrl(normalize(input))
    }

    private fun normalize(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return getString(R.string.home_url)
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        if (s.startsWith("about:") || s.startsWith("file:")) return s
        // 形如 example.com / example.com:8080/path 视为网址
        if (s.matches(Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$"))) return "https://$s"
        // 其余一律交给当前搜索引擎
        return SearchEngines.of(Prefs.searchEngine).urlFor(s)
    }

    /** 地址栏左侧的引擎按钮，点一下弹出全部引擎 */
    private fun showEngineMenu(anchor: View) {
        val pm = PopupMenu(this, anchor, Gravity.START)
        SearchEngines.all.forEachIndexed { i, e ->
            pm.menu.add(0, i, i, e.name)
        }
        pm.setOnMenuItemClickListener { item ->
            Prefs.searchEngine = item.itemId
            updateEngineBadge()
            toast(getString(R.string.toast_engine_switched, SearchEngines.of(item.itemId).name))
            // 地址栏里已经是关键词时，立刻用新引擎重新搜一次
            val q = etUrl.text.toString().trim()
            if (q.isNotEmpty() && !q.startsWith("http")) load(q)
            true
        }
        pm.show()
    }

    private fun updateEngineBadge() {
        btnEngine.text = SearchEngines.of(Prefs.searchEngine).badge
    }

    override fun onResume() {
        super.onResume()
        webView.settings.javaScriptEnabled = Prefs.javascript
        webView.settings.userAgentString =
            if (Prefs.desktopUa) DESKTOP_UA
            else WebSettings.getDefaultUserAgent(this) + MOBILE_UA_SUFFIX
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
