package com.readerh.app.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.text.Html
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.readerh.app.util.ImageLoader
import kotlin.math.roundToInt

data class PageConfig(
    val width: Int,
    val height: Int,
    val fontSp: Float,
    val lineMult: Float,
    val paraDp: Float,
    val justify: Boolean
)

/**
 * 负责把 HTML 章节转换成可用于原生分页的 Spanned，并完成分页。
 */
object ReaderEngine {

    fun textPaint(context: Context, cfg: PageConfig, color: Int): TextPaint =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = cfg.fontSp * context.resources.displayMetrics.scaledDensity
            this.color = color
        }

    /** 预清理：去掉脚本/样式/空白节点，抽取图片地址 */
    fun preProcess(html: String): String {
        var h = html
        h = h.replace(Regex("(?is)<(script|style|noscript|iframe|svg|video|audio|form)[^>]*>.*?</\\1>"), "")
        h = h.replace(Regex("(?is)<!--.*?-->"), "")
        h = h.replace(Regex("(?is)\\s+(on[a-z]+|style|class|id)=\"[^\"]*\""), " ")
        h = h.replace(Regex("(?is)<(span|font|section)\\s*/?>"), "")
        h = h.replace(Regex("(?is)</(span|font|section)>"), "")
        h = h.replace(Regex("(?is)<br\\s*/?>"), "<br/>")
        h = h.replace(Regex("(?is)<(p|div|li|h[1-6]|blockquote|tr)([^>]*)>"), "<$1>")
        h = h.trim()
        return h
    }

    fun imageUrls(html: String): List<String> {
        val out = LinkedHashSet<String>()
        val re = Regex("(?is)<img[^>]+src=\"([^\"]+)\"")
        re.findAll(html).forEach { out.add(it.groupValues[1]) }
        return out.toList()
    }

    /** 预热图片，避免主线程阻塞太久；在后台线程调用 */
    fun warmImages(urls: List<String>, maxWidth: Int) {
        urls.take(30).forEach { ImageLoader.get(it, maxWidth) }
    }

    /**
     * 生成整章的 Spanned（含标题）。必须在后台线程调用（图片会同步下载）。
     */
    fun buildSpanned(
        context: Context,
        html: String,
        title: String,
        cfg: PageConfig,
        paint: TextPaint
    ): Spanned {
        val res = context.resources
        val maxW = cfg.width
        val maxH = (cfg.height * 0.75f).roundToInt()

        val getter = Html.ImageGetter { src ->
            val bmp = ImageLoader.get(src, maxW)
            if (bmp == null) {
                val ph = Bitmap.createBitmap(
                    1.coerceAtLeast(maxW / 3), 1.coerceAtLeast(40), Bitmap.Config.RGB_565
                ).apply { eraseColor(0xFFDDDDDD.toInt()) }
                val placeholder = BitmapDrawable(res, ph)
                placeholder.setBounds(0, 0, placeholder.intrinsicWidth, placeholder.intrinsicHeight)
                placeholder
            } else {
                scaleDrawable(bmp, maxW, maxH, res)
            }
        }

        val body = try {
            Html.fromHtml(preProcess(html), Html.FROM_HTML_MODE_COMPACT, getter, null)
        } catch (e: Exception) {
            SpannableString(preProcess(html).replace(Regex("<[^>]+>"), ""))
        }

        val sb = SpannableStringBuilder()
        if (title.isNotBlank()) {
            val t = SpannableString(title)
            t.setSpan(RelativeSizeSpan(1.25f), 0, t.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            t.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, t.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(t).append("\n")
        }
        sb.append(body)
        applyParagraphSpacing(sb, (cfg.paraDp * res.displayMetrics.density).roundToInt())
        trimEdges(sb)
        return SpannableString.valueOf(sb)
    }

    private fun scaleDrawable(bmp: Bitmap, maxW: Int, maxH: Int, res: android.content.res.Resources): BitmapDrawable {
        var w = bmp.width
        var h = bmp.height
        val scale = minOf(maxW.toFloat() / w, maxH.toFloat() / h, 1f)
        w = (w * scale).roundToInt().coerceAtLeast(1)
        h = (h * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        val dr = BitmapDrawable(res, scaled)
        dr.setBounds(0, 0, w, h)
        return dr
    }

    /** 把段间的空行替换为「段距」行（高度=段距×行距倍数） */
    private fun applyParagraphSpacing(sb: SpannableStringBuilder, paraPx: Int) {
        val raw = sb.toString()
        var idx = 0
        var offset = 0
        while (true) {
            val i = raw.indexOf("\n\n", idx)
            if (i < 0) break
            val spacer = SpannableString("\n \n")
            spacer.setSpan(AbsoluteSizeSpan(paraPx.coerceAtLeast(1)), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val pos = i + offset
            sb.replace(pos, pos + 2, spacer)
            offset += spacer.length - 2
            idx = i + 2
        }
    }

    private fun trimEdges(sb: SpannableStringBuilder) {
        while (sb.isNotEmpty() && sb[0] == '\n') sb.delete(0, 1)
        while (sb.isNotEmpty() && sb[sb.length - 1] == '\n') sb.delete(sb.length - 1, sb.length)
    }

    fun paginate(
        spanned: Spanned,
        cfg: PageConfig,
        paint: TextPaint
    ): List<CharSequence> = Paginator.paginate(
        content = spanned,
        width = cfg.width,
        height = cfg.height,
        paint = paint,
        lineMult = cfg.lineMult,
        lineExtra = 0f,
        justify = cfg.justify
    )

    /** 判断某页是否只有图片（用于避免空白页） */
    fun hasImage(cs: CharSequence): Boolean =
        (cs as? Spanned)?.getSpans(0, cs.length, ImageSpan::class.java)?.isNotEmpty() == true
}
