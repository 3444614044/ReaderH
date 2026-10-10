package com.readerh.app

import android.content.Context
import kotlin.math.roundToInt

object Prefs {
    private const val NAME = "circle_reader"

    private const val K_FONT_SP = "font_sp"
    private const val K_LINE = "line_mult_x100"
    private const val K_PARA = "para_dp"
    private const val K_THEME = "theme"
    private const val K_BRIGHTNESS = "brightness"
    private const val K_FOLLOW_SYSTEM = "follow_system"
    private const val K_COLUMNS = "columns"
    private const val K_JUSTIFY = "justify"
    private const val K_AUTO_NEXT = "auto_next"
    private const val K_KEEP_SCREEN = "keep_screen"
    private const val K_IMMERSIVE = "immersive"
    private const val K_JS = "js"
    private const val K_DESKTOP_UA = "desktop_ua"
    private const val K_TURN_MODE = "turn_mode"
    private const val K_SEARCH_ENGINE = "search_engine"

    private fun sp(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var fontSp: Int
        get() = sp(app()).getInt(K_FONT_SP, 18)
        set(v) = sp(app()).edit().putInt(K_FONT_SP, v).apply()

    var lineMult: Float
        get() = sp(app()).getInt(K_LINE, 180) / 100f
        set(v) = sp(app()).edit().putInt(K_LINE, (v * 100).roundToInt()).apply()

    var paraDp: Int
        get() = sp(app()).getInt(K_PARA, 8)
        set(v) = sp(app()).edit().putInt(K_PARA, v).apply()

    var themeIndex: Int
        get() = sp(app()).getInt(K_THEME, 0)
        set(v) = sp(app()).edit().putInt(K_THEME, v).apply()

    var brightness: Int
        get() = sp(app()).getInt(K_BRIGHTNESS, -1)
        set(v) = sp(app()).edit().putInt(K_BRIGHTNESS, v).apply()

    var followSystemBrightness: Boolean
        get() = sp(app()).getBoolean(K_FOLLOW_SYSTEM, true)
        set(v) = sp(app()).edit().putBoolean(K_FOLLOW_SYSTEM, v).apply()

    var columns: Int
        get() = sp(app()).getInt(K_COLUMNS, 1)
        set(v) = sp(app()).edit().putInt(K_COLUMNS, v).apply()

    var justify: Boolean
        get() = sp(app()).getBoolean(K_JUSTIFY, true)
        set(v) = sp(app()).edit().putBoolean(K_JUSTIFY, v).apply()

    var autoNext: Boolean
        get() = sp(app()).getBoolean(K_AUTO_NEXT, true)
        set(v) = sp(app()).edit().putBoolean(K_AUTO_NEXT, v).apply()

    var keepScreen: Boolean
        get() = sp(app()).getBoolean(K_KEEP_SCREEN, true)
        set(v) = sp(app()).edit().putBoolean(K_KEEP_SCREEN, v).apply()

    var immersive: Boolean
        get() = sp(app()).getBoolean(K_IMMERSIVE, false)
        set(v) = sp(app()).edit().putBoolean(K_IMMERSIVE, v).apply()

    var javascript: Boolean
        get() = sp(app()).getBoolean(K_JS, true)
        set(v) = sp(app()).edit().putBoolean(K_JS, v).apply()

    var desktopUa: Boolean
        get() = sp(app()).getBoolean(K_DESKTOP_UA, false)
        set(v) = sp(app()).edit().putBoolean(K_DESKTOP_UA, v).apply()

    var turnMode: Int
        get() = sp(app()).getInt(K_TURN_MODE, 0)
        set(v) = sp(app()).edit().putInt(K_TURN_MODE, v).apply()

    var searchEngine: Int
        get() = sp(app()).getInt(K_SEARCH_ENGINE, 0)
        set(v) = sp(app()).edit().putInt(K_SEARCH_ENGINE, v).apply()

    @Volatile
    private var appRef: App? = null

    fun init(app: App) {
        appRef = app
    }

    private fun app(): Context = appRef ?: throw IllegalStateException("Prefs 未初始化")
}
