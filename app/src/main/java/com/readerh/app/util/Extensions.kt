package com.readerh.app.util

import android.content.Context
import android.util.TypedValue
import android.widget.Toast

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.sp(v: Float): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics
)

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

/** 去掉 HTML 里的标签，得到纯文本摘要 */
fun htmlToText(html: String, limit: Int = 120): String {
    val noScript = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
    val plain = noScript
        .replace(Regex("(?is)<br\\s*/?>"), "\n")
        .replace(Regex("(?is)</(p|div|li|h[1-6]|tr)>"), "\n")
        .replace(Regex("(?s)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\n{2,}"), "\n")
        .trim()
    return if (plain.length <= limit) plain else plain.substring(0, limit) + "…"
}

fun hostOf(url: String): String {
    return try {
        val u = java.net.URL(url)
        u.host.removePrefix("www.")
    } catch (e: Exception) {
        url.take(30)
    }
}
