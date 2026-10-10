package com.readerh.app.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.readerh.app.Prefs
import com.readerh.app.R
import com.readerh.app.data.ArticleStore
import com.readerh.app.model.Article
import com.readerh.app.settings.SettingsActivity
import com.readerh.app.util.Readability
import com.readerh.app.util.dp
import com.readerh.app.util.hostOf
import com.readerh.app.util.htmlToText
import com.readerh.app.util.toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 沉浸式阅读页：原生分页 + 仿真翻页，支持自动加载下一章、目录、字号/行距/主题/亮度调节。
 */
class ReaderActivity : AppCompatActivity() {

    private data class Chapter(
        val title: String,
        val html: String,
        val url: String,
        var startPage: Int = 0
    )

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_HTML = "extra_html"
        const val EXTRA_NEXT = "extra_next"

        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
    }

    private lateinit var readerRoot: FrameLayout
    private lateinit var pageTurnView: PageTurnView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var catalogPanel: LinearLayout
    private lateinit var loading: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvBattery: TextView
    private lateinit var seekFont: SeekBar
    private lateinit var seekLine: SeekBar
    private lateinit var seekPara: SeekBar
    private lateinit var seekLight: SeekBar
    private lateinit var tvFont: TextView
    private lateinit var tvLine: TextView
    private lateinit var tvPara: TextView
    private lateinit var tvLight: TextView
    private lateinit var themeWrap: LinearLayout
    private lateinit var cbFollowSystem: CheckBox
    private lateinit var rvChapters: RecyclerView

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private val chapters = mutableListOf<Chapter>()
    private val pages = mutableListOf<CharSequence>()
    private val spannedCache = HashMap<String, Spanned>()

    private var articleId: Long = -1L
    private var currentUrl: String = ""
    private var currentTitle: String = ""
    private var pendingNextUrl: String? = null
    private var startPage: Int = 0
    private var loadingNext = false
    private var barsVisible = true
    private var hiddenWebView: WebView? = null
    private var lastCfgKey = ""

    private val chapterAdapter = ChapterAdapter()
    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        bindViews()
        readIntent()
        setupPageTurn()
        setupBars()
        setupPanel()
        setupCatalog()
        applyTheme()
        applyBrightness()

        pageTurnView.doOnPreDraw { relayout(startPage) }
    }

    private fun bindViews() {
        readerRoot = findViewById(R.id.readerRoot)
        pageTurnView = findViewById(R.id.pageTurnView)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        panel = findViewById(R.id.panel)
        catalogPanel = findViewById(R.id.catalogPanel)
        loading = findViewById(R.id.loading)
        tvTitle = findViewById(R.id.tvTitle)
        tvProgress = findViewById(R.id.tvProgress)
        tvTime = findViewById(R.id.tvTime)
        tvBattery = findViewById(R.id.tvBattery)
        seekFont = findViewById(R.id.seekFont)
        seekLine = findViewById(R.id.seekLine)
        seekPara = findViewById(R.id.seekPara)
        seekLight = findViewById(R.id.seekLight)
        tvFont = findViewById(R.id.tvFont)
        tvLine = findViewById(R.id.tvLine)
        tvPara = findViewById(R.id.tvPara)
        tvLight = findViewById(R.id.tvLight)
        themeWrap = findViewById(R.id.themeWrap)
        cbFollowSystem = findViewById(R.id.cbFollowSystem)
        rvChapters = findViewById(R.id.rvChapters)
    }

    private fun readIntent() {
        articleId = intent.getLongExtra(EXTRA_ID, -1L)
        currentUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val html = intent.getStringExtra(EXTRA_HTML) ?: ""
        pendingNextUrl = intent.getStringExtra(EXTRA_NEXT)?.ifBlank { null }

        if (html.isNotBlank()) {
            chapters.add(Chapter(currentTitle, html, currentUrl))
            articleId.takeIf { it > 0 }?.let {
                startPage = ArticleStore.get().findById(it)?.readPage ?: 0
            }
        } else if (articleId > 0) {
            val a = ArticleStore.get().findById(articleId)
            if (a != null) {
                currentUrl = a.url
                currentTitle = a.title
                startPage = a.readPage
                chapters.add(Chapter(a.title, a.contentHtml, a.url))
            }
        }
        tvTitle.text = currentTitle.ifBlank { getString(R.string.app_name) }
    }

    private fun setupPageTurn() {
        pageTurnView.mode = when (Prefs.turnMode) {
            1 -> TurnMode.COVER
            2 -> TurnMode.SLIDE
            3 -> TurnMode.NONE
            else -> TurnMode.SIMULATION
        }
        pageTurnView.onCenterTap = { toggleMenus() }
        pageTurnView.onIndexChanged = { onPageChanged(it) }
        pageTurnView.onRequestNext = { requestNext() }
        pageTurnView.onRequestPrev = { toast(getString(R.string.toast_first_page)) }
    }

    private fun setupBars() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnMore).setOnClickListener { showMoreMenu(it) }
        findViewById<View>(R.id.btnCatalog).setOnClickListener { showCatalog() }
        findViewById<View>(R.id.btnTurn).setOnClickListener { showTurnDialog() }
        findViewById<View>(R.id.btnFont).setOnClickListener { showPanel(Panel.FONT) }
        findViewById<View>(R.id.btnTheme).setOnClickListener { showPanel(Panel.THEME) }
        findViewById<View>(R.id.btnLight).setOnClickListener { showPanel(Panel.LIGHT) }
        findViewById<View>(R.id.btnReaderSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private enum class Panel { FONT, THEME, LIGHT }

    private fun showPanel(p: Panel) {
        bottomBar.visibility = View.GONE
        panel.visibility = View.VISIBLE
        val fontRows = p == Panel.FONT
        findViewById<View>(R.id.rowFont).visibility = if (fontRows) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rowLine).visibility = if (fontRows) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rowPara).visibility = if (fontRows) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rowTheme).visibility = if (p == Panel.THEME) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rowLight).visibility = if (p == Panel.LIGHT) View.VISIBLE else View.GONE
        cbFollowSystem.visibility = if (p == Panel.LIGHT) View.VISIBLE else View.GONE
    }

    private fun hideOverlays() {
        panel.visibility = View.GONE
        catalogPanel.visibility = View.GONE
    }

    private fun toggleMenus() {
        if (panel.visibility == View.VISIBLE || catalogPanel.visibility == View.VISIBLE) {
            hideOverlays()
            bottomBar.visibility = if (barsVisible) View.VISIBLE else View.GONE
            return
        }
        barsVisible = !barsVisible
        applyBars()
    }

    private fun applyBars() {
        topBar.visibility = if (barsVisible) View.VISIBLE else View.GONE
        bottomBar.visibility = if (barsVisible) View.VISIBLE else View.GONE
        if (!barsVisible) panel.visibility = View.GONE
        if (Prefs.immersive) {
            val ctrl = WindowInsetsControllerCompat(window, window.decorView)
            if (barsVisible) ctrl.show(WindowInsetsCompat.Type.systemBars())
            else ctrl.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupPanel() {
        seekFont.progress = (Prefs.fontSp - 10).coerceIn(0, 30)
        seekLine.progress = ((Prefs.lineMult - 1f) * 10).toInt().coerceIn(0, 30)
        seekPara.progress = Prefs.paraDp.coerceIn(0, 24)
        val b = if (Prefs.brightness < 0) 50 else Prefs.brightness
        seekLight.progress = b.coerceIn(0, 100)
        cbFollowSystem.isChecked = Prefs.followSystemBrightness
        updateFontLabels()

        val relayout = { relayout(pageTurnView.index) }

        seekFont.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                tvFont.text = "${v + 10}"
            }

            override fun onStopTrackingTouch(s: SeekBar) {
                Prefs.fontSp = s.progress + 10
                relayout()
            }
        })
        seekLine.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                tvLine.text = String.format(Locale.getDefault(), "%.1f", 1f + v * 0.1f)
            }

            override fun onStopTrackingTouch(s: SeekBar) {
                Prefs.lineMult = 1f + s.progress * 0.1f
                pageTurnView.lineMult = Prefs.lineMult
                pageTurnView.invalidateLayouts()
            }
        })
        seekPara.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                tvPara.text = "$v"
            }

            override fun onStopTrackingTouch(s: SeekBar) {
                Prefs.paraDp = s.progress
                relayout()
            }
        })
        seekLight.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                tvLight.text = "$v"
            }

            override fun onStopTrackingTouch(s: SeekBar) {
                Prefs.brightness = s.progress
                Prefs.followSystemBrightness = false
                cbFollowSystem.isChecked = false
                applyBrightness()
            }
        })
        cbFollowSystem.setOnCheckedChangeListener { _, checked ->
            Prefs.followSystemBrightness = checked
            applyBrightness()
        }
        buildThemeSwatches()
    }

    private fun updateFontLabels() {
        tvFont.text = "${Prefs.fontSp}"
        tvLine.text = String.format(Locale.getDefault(), "%.1f", Prefs.lineMult)
        tvPara.text = "${Prefs.paraDp}"
        tvLight.text = "${if (Prefs.brightness < 0) 50 else Prefs.brightness}"
    }

    private fun buildThemeSwatches() {
        themeWrap.removeAllViews()
        val size = dp(30f).toInt()
        ReaderThemes.all.forEachIndexed { i, t ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(10f).toInt()
            }
            v.background = GradientDrawable().apply {
                setColor(t.bg)
                cornerRadius = dp(15f)
                setStroke(
                    if (i == Prefs.themeIndex) dp(2f).toInt() else 1,
                    if (i == Prefs.themeIndex) getColor(R.color.brand) else 0x33000000.toInt()
                )
            }
            v.setOnClickListener {
                Prefs.themeIndex = i
                applyTheme()
                buildThemeSwatches()
            }
            themeWrap.addView(v)
        }
    }

    private fun applyTheme() {
        val t = ReaderThemes.of(Prefs.themeIndex)
        pageTurnView.theme = t
        pageTurnView.lineMult = Prefs.lineMult
        pageTurnView.justify = Prefs.justify
        pageTurnView.invalidateLayouts()
        readerRoot.setBackgroundColor(t.bg)
        topBar.setBackgroundColor(t.bg)
        bottomBar.setBackgroundColor(t.bg)
        panel.setBackgroundColor(t.bg)
        catalogPanel.setBackgroundColor(t.bg)
        tvTitle.setTextColor(t.title)
        tintGroup(topBar, t.title)
        tintGroup(bottomBar, t.meta)
        tintGroup(panel, t.meta)
        tvProgress.setTextColor(t.meta)
        tvTime.setTextColor(t.meta)
        tvBattery.setTextColor(t.meta)
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !t.isDark
    }

    private fun tintGroup(group: View, color: Int) {
        if (group is ViewGroup) {
            for (i in 0 until group.childCount) tintGroup(group.getChildAt(i), color)
        } else when (group) {
            is ImageView -> group.setColorFilter(color)
            is TextView -> group.setTextColor(color)
        }
    }

    private fun applyBrightness() {
        if (Prefs.keepScreen) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val at = window.attributes
        at.screenBrightness = if (Prefs.followSystemBrightness) -1f
        else (Prefs.brightness.coerceAtLeast(10)) / 100f
        window.attributes = at
    }

    private fun setupCatalog() {
        rvChapters.layoutManager = LinearLayoutManager(this)
        rvChapters.adapter = chapterAdapter
        findViewById<TextView>(R.id.tvCatalogTitle).setOnClickListener {
            catalogPanel.visibility = View.GONE
        }
    }

    private fun showCatalog() {
        hideOverlays()
        bottomBar.visibility = View.GONE
        catalogPanel.visibility = View.VISIBLE
        chapterAdapter.notifyDataSetChanged()
    }

    private fun showTurnDialog() {
        val items = arrayOf(
            getString(R.string.turn_simulation),
            getString(R.string.turn_cover),
            getString(R.string.turn_slide),
            getString(R.string.turn_none)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.reader_turn)
            .setSingleChoiceItems(items, Prefs.turnMode.coerceIn(0, 3)) { d, which ->
                Prefs.turnMode = which
                pageTurnView.mode = when (which) {
                    1 -> TurnMode.COVER
                    2 -> TurnMode.SLIDE
                    3 -> TurnMode.NONE
                    else -> TurnMode.SIMULATION
                }
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMoreMenu(anchor: View) {
        val pm = PopupMenu(this, anchor, Gravity.END)
        pm.menu.add(0, 1, 0, R.string.menu_save)
        pm.menu.add(0, 2, 0, R.string.menu_share)
        pm.menu.add(0, 3, 0, R.string.menu_open_browser)
        pm.menu.add(0, 4, 0, R.string.menu_settings)
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> saveToLibrary()
                2 -> shareLink()
                3 -> openInBrowser()
                4 -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        pm.show()
    }

    private fun saveToLibrary(silent: Boolean = false) {
        val first = chapters.firstOrNull()
        if (first == null || currentUrl.isBlank()) return
        val a = Article(
            id = if (articleId > 0) articleId else 0,
            url = currentUrl,
            title = currentTitle.ifBlank { first.title },
            site = hostOf(currentUrl),
            contentHtml = first.html,
            text = htmlToText(first.html, 200),
            readPage = pageTurnView.index,
            totalPages = pages.size
        )
        articleId = ArticleStore.get().upsert(a)
        if (!silent) toast(getString(R.string.toast_saved))
    }

    private fun shareLink() {
        if (currentUrl.isBlank()) return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                    putExtra(Intent.EXTRA_SUBJECT, currentTitle)
                },
                getString(R.string.menu_share)
            )
        )
    }

    private fun openInBrowser() {
        if (currentUrl.isBlank()) return
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(currentUrl)))
    }

    // ---------- 分页 ----------

    private fun currentConfig(): PageConfig {
        val dm = resources.displayMetrics
        // 横屏时按设置的栏数均分页宽（竖屏始终单栏）
        val landscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val cols = if (landscape) Prefs.columns.coerceIn(1, 3) else 1
        val w = pageTurnView.contentWidth().takeIf { it > 0 } ?: dm.widthPixels
        val h = pageTurnView.contentHeight().takeIf { it > 0 } ?: (dm.heightPixels - 120)
        return PageConfig(
            width = (w / cols).coerceAtLeast(100),
            height = h.coerceAtLeast(100),
            fontSp = Prefs.fontSp.toFloat(),
            lineMult = Prefs.lineMult,
            paraDp = Prefs.paraDp.toFloat(),
            justify = Prefs.justify
        )
    }

    private fun cfgKey(cfg: PageConfig) =
        "${cfg.width}x${cfg.height}|${cfg.fontSp}|${cfg.lineMult}|${cfg.paraDp}|${cfg.justify}"

    private fun spannedOf(ch: Chapter, cfg: PageConfig, paint: android.text.TextPaint): Spanned {
        val key = "${ch.url}|${cfgKey(cfg)}"
        spannedCache[key]?.let { return it }
        val sp = ReaderEngine.buildSpanned(this, ch.html, ch.title, cfg, paint)
        if (spannedCache.size > 16) spannedCache.clear()
        spannedCache[key] = sp
        return sp
    }

    private fun relayout(keepIndex: Int?) {
        if (chapters.isEmpty()) return
        val cfg = currentConfig()
        lastCfgKey = cfgKey(cfg) + "|${Prefs.themeIndex}"
        loading.visibility = View.VISIBLE
        executor.execute {
            val paint = ReaderEngine.textPaint(
                this@ReaderActivity, cfg, ReaderThemes.of(Prefs.themeIndex).text
            )
            val rebuilt = mutableListOf<CharSequence>()
            for (ch in chapters) {
                ch.startPage = rebuilt.size
                rebuilt.addAll(ReaderEngine.paginate(spannedOf(ch, cfg, paint), cfg, paint))
            }
            handler.post {
                pages.clear()
                pages.addAll(rebuilt)
                loading.visibility = View.GONE
                val idx = (keepIndex ?: pageTurnView.index)
                    .coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                pageTurnView.setPages(pages, idx)
                updateProgress(idx)
                chapterAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun onPageChanged(i: Int) {
        updateProgress(i)
        scheduleSave()
        if (i >= pages.size - 2 && Prefs.autoNext && !loadingNext && !pendingNextUrl.isNullOrBlank()) {
            loadNextChapter()
        }
    }

    private fun updateProgress(i: Int) {
        if (pages.isEmpty()) {
            tvProgress.text = ""
            return
        }
        val pct = ((i + 1) * 100 / pages.size).coerceIn(0, 100)
        tvProgress.text = getString(R.string.reader_progress, i + 1, pages.size, pct)
    }

    private fun requestNext() {
        if (!Prefs.autoNext || pendingNextUrl.isNullOrBlank()) {
            toast(getString(R.string.toast_last_page))
            return
        }
        loadNextChapter(advance = true)
    }

    // ---------- 自动加载下一章 ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadNextChapter(advance: Boolean = false) {
        val url = pendingNextUrl
        if (url.isNullOrBlank() || loadingNext) return
        loadingNext = true
        loading.visibility = View.VISIBLE
        toast(getString(R.string.toast_loading_next))

        val w = hiddenWebView ?: WebView(this).also { w ->
            w.layoutParams = FrameLayout.LayoutParams(1, 1)
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.loadsImagesAutomatically = false
            w.settings.userAgentString =
                if (Prefs.desktopUa) DESKTOP_UA else w.settings.userAgentString
            (window.decorView as ViewGroup).addView(w)
            hiddenWebView = w
        }

        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(Readability.script(this@ReaderActivity)) { raw ->
                    val res = Readability.parse(raw)
                    loadingNext = false
                    loading.visibility = View.GONE
                    if (res.isEmpty) return@evaluateJavascript
                    appendChapter(
                        Chapter(
                            title = res.title.ifBlank { res.siteName.ifBlank { "下一章" } },
                            html = res.content,
                            url = url
                        ),
                        res.nextUrl,
                        advance
                    )
                }
            }
        }
        w.loadUrl(url)
    }

    private fun appendChapter(ch: Chapter, nextUrl: String, advance: Boolean) {
        if (chapters.any { it.url == ch.url }) return
        pendingNextUrl = nextUrl.ifBlank { null }
        val cfg = currentConfig()
        val startIndex = pages.size
        executor.execute {
            val paint = ReaderEngine.textPaint(
                this@ReaderActivity, cfg, ReaderThemes.of(Prefs.themeIndex).text
            )
            val added = ReaderEngine.paginate(spannedOf(ch, cfg, paint), cfg, paint)
            handler.post {
                ch.startPage = startIndex
                chapters.add(ch)
                pages.addAll(added)
                pageTurnView.setPages(pages, if (advance) startIndex else pageTurnView.index, startIndex)
                updateProgress(pageTurnView.index)
                chapterAdapter.notifyDataSetChanged()
            }
        }
    }

    // ---------- 进度保存 ----------

    private val saveRunnable = Runnable {
        if (articleId > 0) {
            ArticleStore.get().updateProgress(articleId, pageTurnView.index, pages.size)
        }
    }

    private fun scheduleSave() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 1200L)
    }

    private fun updateClock() {
        tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val it = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = it?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = it?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        tvBattery.text = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else ""
    }

    // ---------- 生命周期 ----------

    override fun onResume() {
        super.onResume()
        updateClock()
        handler.postDelayed(ticker, 30_000L)
        val key = cfgKey(currentConfig()) + "|${Prefs.themeIndex}"
        if (key != lastCfgKey && chapters.isNotEmpty()) {
            relayout(pageTurnView.index)
        } else {
            applyBars()
            applyBrightness()
        }
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        if (pages.isNotEmpty()) {
            // 自动入库：即使没手动点「存入书架」，退出时也会保留文章与进度
            saveToLibrary(silent = true)
            ArticleStore.get().updateProgress(articleId, pageTurnView.index, pages.size)
        }
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        pageTurnView.post { relayout(pageTurnView.index) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hiddenWebView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (panel.visibility == View.VISIBLE || catalogPanel.visibility == View.VISIBLE) {
            hideOverlays()
            if (barsVisible) bottomBar.visibility = View.VISIBLE
            return
        }
        super.onBackPressed()
    }

    // ---------- 目录 ----------

    private inner class ChapterAdapter : RecyclerView.Adapter<ChapterVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterVH {
            val tv = TextView(this@ReaderActivity).apply {
                val p = dp(14f).toInt()
                setPadding(dp(16f).toInt(), p, dp(16f).toInt(), p)
                textSize = 14f
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return ChapterVH(tv)
        }

        override fun onBindViewHolder(holder: ChapterVH, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = chapters.size
    }

    private inner class ChapterVH(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
        fun bind(position: Int) {
            val ch = chapters[position]
            tv.text = "${position + 1}. ${ch.title.ifBlank { "第 ${position + 1} 章" }}"
            tv.setTextColor(ReaderThemes.of(Prefs.themeIndex).title)
            tv.setOnClickListener {
                pageTurnView.setIndex(ch.startPage)
                catalogPanel.visibility = View.GONE
                if (barsVisible) bottomBar.visibility = View.VISIBLE
            }
        }
    }

    private open class SimpleSeek : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {}
        override fun onStartTrackingTouch(s: SeekBar) {}
        override fun onStopTrackingTouch(s: SeekBar) {}
    }
}
