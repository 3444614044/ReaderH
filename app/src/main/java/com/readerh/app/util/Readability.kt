package com.readerh.app.util

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener

data class ExtractResult(
    val title: String = "",
    val byline: String = "",
    val content: String = "",
    val text: String = "",
    val nextUrl: String = "",
    val siteName: String = "",
    val length: Int = 0
) {
    val isEmpty: Boolean get() = content.isBlank() || length < 120
}

/**
 * 读取 assets 中的正文抽取脚本，并把 JS 返回的 JSON 解析成 ExtractResult。
 */
object Readability {

    @Volatile
    private var cached: String? = null

    fun script(context: Context): String = cached ?: synchronized(this) {
        cached ?: context.assets.open("readability.js").bufferedReader().use { it.readText() }
            .also { cached = it }
    }

    fun parse(raw: String?): ExtractResult {
        if (raw.isNullOrBlank()) return ExtractResult()
        return try {
            val unescaped = (JSONTokener(raw).nextValue() as? String) ?: raw
            val o = JSONObject(unescaped)
            ExtractResult(
                title = o.optString("title", ""),
                byline = o.optString("byline", ""),
                content = o.optString("content", ""),
                text = o.optString("text", ""),
                nextUrl = o.optString("nextPageUrl", ""),
                siteName = o.optString("siteName", ""),
                length = o.optInt("length", 0)
            )
        } catch (e: Exception) {
            ExtractResult()
        }
    }
}
