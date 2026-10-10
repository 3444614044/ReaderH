package com.readerh.app.reader

import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint

/**
 * 基于 StaticLayout 的原生分页器：按行切分，保证不会把一行切成两半。
 */
object Paginator {

    fun paginate(
        content: CharSequence,
        width: Int,
        height: Int,
        paint: TextPaint,
        lineMult: Float,
        lineExtra: Float,
        justify: Boolean
    ): List<CharSequence> {
        if (width <= 0 || height <= 0 || content.isEmpty()) return emptyList()
        val builder = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineExtra, lineMult)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
        if (Build.VERSION.SDK_INT >= 26 && justify) {
            builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
        val layout = builder.build()
        val pages = ArrayList<CharSequence>()

        var first = 0
        var i = 0
        while (i < layout.lineCount) {
            val occupied = layout.getLineBottom(i) - layout.getLineTop(first)
            val nextIsHard = i + 1 < layout.lineCount && isParagraphEnd(content, layout.getLineEnd(i))
            if (occupied > height && i > first) {
                pages.add(slice(content, layout, first, i))
                first = i
                continue
            }
            if (nextIsHard && occupied + (layout.getLineBottom(i + 1) - layout.getLineBottom(i)) > height && i > first) {
                pages.add(slice(content, layout, first, i))
                first = i + 1
                i++
                continue
            }
            i++
        }
        if (first < layout.lineCount) {
            pages.add(slice(content, layout, first, layout.lineCount - 1))
        }
        return pages.filter { it.toString().isNotBlank() || it is Spanned && hasImage(it) }
    }

    private fun hasImage(cs: CharSequence): Boolean {
        return (cs as? Spanned)?.getSpans(0, cs.length, android.text.style.ImageSpan::class.java)?.isNotEmpty() == true
    }

    private fun isParagraphEnd(cs: CharSequence, end: Int): Boolean {
        var j = end - 1
        var newlines = 0
        while (j >= 0 && j > end - 4) {
            if (cs[j] == '\n') newlines++
            j--
        }
        return newlines > 0
    }

    private fun slice(cs: CharSequence, layout: StaticLayout, fromLine: Int, toLine: Int): CharSequence {
        var start = layout.getLineStart(fromLine)
        var end = layout.getLineEnd(toLine)
        while (start < end && cs[start].isWhitespace()) start++
        while (end > start && cs[end - 1].isWhitespace()) end--
        return cs.subSequence(start, end)
    }
}
