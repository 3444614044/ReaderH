package com.readerh.app.settings

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.readerh.app.Prefs
import com.readerh.app.R
import com.readerh.app.data.ArticleStore
import com.readerh.app.reader.ReaderThemes
import com.readerh.app.util.SearchEngines
import com.readerh.app.util.dp

class SettingsActivity : AppCompatActivity() {

    private lateinit var seekFont: SeekBar
    private lateinit var seekLine: SeekBar
    private lateinit var seekPara: SeekBar
    private lateinit var tvFont: TextView
    private lateinit var tvLine: TextView
    private lateinit var tvPara: TextView
    private lateinit var themeWrap: LinearLayout
    private lateinit var spinnerColumns: Spinner
    private lateinit var spinnerEngine: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        seekFont = findViewById(R.id.seekFont)
        seekLine = findViewById(R.id.seekLine)
        seekPara = findViewById(R.id.seekPara)
        tvFont = findViewById(R.id.tvFont)
        tvLine = findViewById(R.id.tvLine)
        tvPara = findViewById(R.id.tvPara)
        themeWrap = findViewById(R.id.themeWrap)
        spinnerColumns = findViewById(R.id.spinnerColumns)
        spinnerEngine = findViewById(R.id.spinnerEngine)

        bindSeek(seekFont, (Prefs.fontSp - 10).coerceIn(0, 30)) {
            Prefs.fontSp = it + 10
            tvFont.text = "${it + 10}sp"
        }
        bindSeek(seekLine, ((Prefs.lineMult - 1f) * 10f).toInt().coerceIn(0, 30)) {
            Prefs.lineMult = 1f + it * 0.1f
            tvLine.text = String.format("%.1f", 1f + it * 0.1f)
        }
        bindSeek(seekPara, Prefs.paraDp.coerceIn(0, 24)) {
            Prefs.paraDp = it
            tvPara.text = "${it}dp"
        }
        tvFont.text = "${Prefs.fontSp}sp"
        tvLine.text = String.format("%.1f", Prefs.lineMult)
        tvPara.text = "${Prefs.paraDp}dp"

        spinnerColumns.setSelection((Prefs.columns - 1).coerceIn(0, 2))
        spinnerColumns.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                p: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long
            ) {
                Prefs.columns = pos + 1
            }

            override fun onNothingSelected(p: android.widget.AdapterView<*>) = Unit
        }

        spinnerEngine.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, SearchEngines.names()
        )
        spinnerEngine.setSelection(Prefs.searchEngine.coerceIn(0, SearchEngines.all.lastIndex))
        spinnerEngine.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                p: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long
            ) {
                Prefs.searchEngine = pos
            }

            override fun onNothingSelected(p: android.widget.AdapterView<*>) = Unit
        }

        buildThemeSwatches()

        bindCheckBox(R.id.cbJustify, Prefs.justify) { Prefs.justify = it }
        bindCheckBox(R.id.cbAutoNext, Prefs.autoNext) { Prefs.autoNext = it }
        bindCheckBox(R.id.cbKeepScreen, Prefs.keepScreen) { Prefs.keepScreen = it }
        bindCheckBox(R.id.cbImmersive, Prefs.immersive) { Prefs.immersive = it }
        bindCheckBox(R.id.cbJs, Prefs.javascript) { Prefs.javascript = it }
        bindCheckBox(R.id.cbDesktop, Prefs.desktopUa) { Prefs.desktopUa = it }

        findViewById<View>(R.id.btnClear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.label_clear)
                .setMessage(R.string.label_about)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    ArticleStore.get().clear()
                }
                .show()
        }
    }

    private fun bindSeek(bar: SeekBar, value: Int, onChange: (Int) -> Unit) {
        bar.progress = value
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar, v: Int, fromUser: Boolean) {
                if (fromUser) onChange(v)
            }

            override fun onStartTrackingTouch(b: SeekBar) = Unit
            override fun onStopTrackingTouch(b: SeekBar) = Unit
        })
    }

    private fun bindCheckBox(id: Int, value: Boolean, onChange: (Boolean) -> Unit) {
        val cb = findViewById<CheckBox>(id)
        cb.isChecked = value
        cb.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun buildThemeSwatches() {
        themeWrap.removeAllViews()
        val size = dp(34f).toInt()
        val margin = dp(6f).toInt()
        ReaderThemes.all.forEachIndexed { i, t ->
            val v = TextView(this).apply {
                text = t.name.take(1)
                gravity = Gravity.CENTER
                setTextColor(t.text)
                setBackgroundColor(t.bg)
                textSize = 14f
            }
            val lp = LinearLayout.LayoutParams(size, size).apply {
                leftMargin = margin
                rightMargin = margin
            }
            v.layoutParams = lp
            v.setOnClickListener {
                Prefs.themeIndex = i
                buildThemeSwatches()
            }
            val sel = i == Prefs.themeIndex
            v.elevation = if (sel) 6f else 0f
            v.alpha = if (sel) 1f else 0.75f
            themeWrap.addView(v)
        }
    }
}
