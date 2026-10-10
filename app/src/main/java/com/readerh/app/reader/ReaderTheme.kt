package com.readerh.app.reader

import android.graphics.Color

data class ReaderTheme(
    val name: String,
    val bg: Int,
    val text: Int,
    val title: Int,
    val link: Int,
    val meta: Int,
    val isDark: Boolean
)

object ReaderThemes {
    val all = listOf(
        ReaderTheme("纸白", 0xFFF7F5F1.toInt(), 0xFF2B2B2B.toInt(), 0xFF111111.toInt(), 0xFF3D6BD9.toInt(), 0xFF9A9A9A.toInt(), false),
        ReaderTheme("纯白", 0xFFFFFFFF.toInt(), 0xFF222222.toInt(), 0xFF000000.toInt(), 0xFF3D6BD9.toInt(), 0xFF909090.toInt(), false),
        ReaderTheme("米黄", 0xFFFBF3DC.toInt(), 0xFF3A3226.toInt(), 0xFF241E14.toInt(), 0xFF8A6A2F.toInt(), 0xFFA79A80.toInt(), false),
        ReaderTheme("护眼绿", 0xFFCDE6C8.toInt(), 0xFF22331F.toInt(), 0xFF152612.toInt(), 0xFF3E7A34.toInt(), 0xFF7C9C78.toInt(), false),
        ReaderTheme("青蓝", 0xFFDCEAF3.toInt(), 0xFF1E2C36.toInt(), 0xFF111C24.toInt(), 0xFF2C6491.toInt(), 0xFF7C94A3.toInt(), false),
        ReaderTheme("暗夜", 0xFF1C1F26.toInt(), 0xFFB8BEC9.toInt(), 0xFFE6EAF2.toInt(), 0xFF7FA8E8.toInt(), 0xFF6B7280.toInt(), true),
        ReaderTheme("纯黑", 0xFF000000.toInt(), 0xFF9EA5B0.toInt(), 0xFFD5DAE2.toInt(), 0xFF6E97DA.toInt(), 0xFF5A616B.toInt(), true)
    )

    fun of(index: Int): ReaderTheme = all[index.coerceIn(0, all.lastIndex)]

    fun blend(from: Int, to: Int, ratio: Float): Int {
        val r = Color.red(from) + ((Color.red(to) - Color.red(from)) * ratio).toInt()
        val g = Color.green(from) + ((Color.green(to) - Color.green(from)) * ratio).toInt()
        val b = Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * ratio).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
