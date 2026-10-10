package com.readerh.app.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

enum class TurnMode { SIMULATION, COVER, SLIDE, NONE }

/**
 * 原生绘制的翻页视图：支持仿真卷曲 / 覆盖 / 平移 / 无动画四种翻页方式。
 */
class PageTurnView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var pages: List<CharSequence> = emptyList()
        private set

    var index: Int = 0
        private set

    var mode: TurnMode = TurnMode.SIMULATION
    var theme: ReaderTheme = ReaderThemes.of(0)
    var justify: Boolean = true
    var lineMult: Float = 1.8f
    var lineExtraPx: Float = 0f

    var onIndexChanged: ((Int) -> Unit)? = null
    var onCenterTap: (() -> Unit)? = null
    var onRequestNext: (() -> Unit)? = null
    var onRequestPrev: (() -> Unit)? = null

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f * resources.displayMetrics.scaledDensity
        color = Color.BLACK
    }

    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * resources.displayMetrics.density
        color = Color.GRAY
    }

    private val layoutCache = HashMap<Int, StaticLayout>()
    private val contentRect = RectF()
    private var pageW = 0
    private var pageH = 0

    private val padL get() = 18f * density
    private val padR get() = 18f * density
    private val padT get() = 14f * density
    private val padB get() = 26f * density
    private val density get() = resources.displayMetrics.density

    /** 拖拽角点（仿真模式） */
    private val corner = PointF()
    private var origin = PointF()
    private var target = PointF()
    private var forward = true
    private var dragging = false
    private var animating = false
    private var animator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    fun setPages(list: List<CharSequence>, index: Int, keepCacheFrom: Int = -1) {
        pages = list
        this.index = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        if (keepCacheFrom < 0) layoutCache.clear() else {
            layoutCache.keys.toList().forEach { if (it >= keepCacheFrom) layoutCache.remove(it) }
        }
        invalidate()
    }

    fun setIndex(i: Int, notify: Boolean = true) {
        val n = i.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        if (n != index) {
            index = n
            if (notify) onIndexChanged?.invoke(index)
        }
        invalidate()
    }

    fun invalidateLayouts() {
        layoutCache.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        contentRect.set(padL, padT, w - padR, h - padB)
        pageW = (contentRect.width()).toInt()
        pageH = (contentRect.height()).toInt()
        layoutCache.clear()
    }

    /** 正文可用宽度（已去掉左右留白） */
    fun contentWidth(): Int = pageW

    /** 正文可用高度（已去掉上下留白与页脚） */
    fun contentHeight(): Int = pageH

    private fun layoutOf(i: Int): StaticLayout? {
        if (i < 0 || i >= pages.size || pageW <= 0) return null
        layoutCache[i]?.let { return it }
        val cs = pages[i]
        val builder = StaticLayout.Builder.obtain(cs, 0, cs.length, textPaint, pageW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineExtraPx, lineMult)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
        if (android.os.Build.VERSION.SDK_INT >= 26 && justify) {
            builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
        val l = builder.build()
        layoutCache[i] = l
        if (layoutCache.size > 12) {
            val oldest = layoutCache.keys.filter { it != index }.minOrNull()
            if (oldest != null) layoutCache.remove(oldest)
        }
        return l
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.bg)
        if (mode == TurnMode.NONE || (!dragging && !animating)) {
            drawPage(canvas, index, 0f)
            drawFooter(canvas, index, 0f)
            return
        }
        val shift = if (forward) (origin.x - corner.x) else (corner.x - origin.x)
        if (shift <= 0.5f) {
            drawPage(canvas, index, 0f)
            drawFooter(canvas, index, 0f)
            return
        }
        when (mode) {
            TurnMode.SIMULATION -> drawSimulation(canvas)
            TurnMode.COVER -> {
                drawPage(canvas, index, 0f)
                drawPage(canvas, if (forward) index + 1 else index - 1, (width - shift))
                drawEdgeShadow(canvas, width - shift, forward)
                drawFooter(canvas, if (forward) index + 1 else index - 1, width - shift)
            }
            TurnMode.SLIDE -> {
                val off = -shift
                drawPage(canvas, index, off)
                drawPage(canvas, if (forward) index + 1 else index - 1, off + width)
                drawFooter(canvas, if (forward) index + 1 else index - 1, off + width)
            }
            else -> drawPage(canvas, index, 0f)
        }
    }

    private fun drawPage(canvas: Canvas, i: Int, offsetX: Float) {
        val l = layoutOf(i) ?: return
        canvas.save()
        canvas.translate(contentRect.left + offsetX, contentRect.top)
        l.draw(canvas)
        canvas.restore()
    }

    private fun drawFooter(canvas: Canvas, i: Int, offsetX: Float) {
        if (pages.isEmpty()) return
        val txt = "${i + 1}/${pages.size}  ${((i + 1) * 100f / pages.size).toInt()}%"
        footerPaint.color = theme.meta
        canvas.drawText(
            txt,
            contentRect.right - footerPaint.measureText(txt) + offsetX,
            height - 10f * density,
            footerPaint
        )
    }

    // ---------- 仿真翻页 ----------

    private fun axis(): Triple<PointF, PointF, PointF> {
        // 返回：轴上一点 axisPoint、单位方向 dir、法线 normal（使 origin 在正侧）
        val ox = origin.x
        val oy = origin.y
        val mx = (ox + corner.x) / 2f
        val my = (oy + corner.y) / 2f
        var dx = -(corner.y - oy)
        var dy = (corner.x - ox)
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 0.001f) {
            dx = 0f; dy = 1f
        } else {
            dx /= len; dy /= len
        }
        var nx = -dy
        var ny = dx
        if ((ox - mx) * nx + (oy - my) * ny < 0) {
            nx = -nx; ny = -ny
        }
        return Triple(PointF(mx, my), PointF(dx, dy), PointF(nx, ny))
    }

    private fun halfPlanePath(a: PointF, d: PointF, n: PointF, positive: Boolean): Path {
        val L = (width + height) * 2f
        val s = if (positive) 1f else -1f
        val p1x = a.x + d.x * L
        val p1y = a.y + d.y * L
        val p2x = a.x - d.x * L
        val p2y = a.y - d.y * L
        return Path().apply {
            moveTo(p1x, p1y)
            lineTo(p2x, p2y)
            lineTo(p2x + n.x * L * s, p2y + n.y * L * s)
            lineTo(p1x + n.x * L * s, p1y + n.y * L * s)
            close()
        }
    }

    private fun drawSimulation(canvas: Canvas) {
        val revealed = if (forward) index + 1 else index - 1
        val (a, d, n) = axis()

        // 1. 底层：纸被掀走后露出的那一页
        drawPage(canvas, revealed, 0f)
        drawFooter(canvas, revealed, 0f)

        // 2. 平铺未动的纸：折痕背侧（负侧），内容正向绘制
        canvas.save()
        canvas.clipPath(halfPlanePath(a, d, n, false))
        drawPage(canvas, index, 0f)
        drawFooter(canvas, index, 0f)
        // 折痕内侧投影，让卷曲处有厚度
        val crease = Paint().apply {
            shader = LinearGradient(
                a.x, a.y,
                a.x - n.x * 40f * density, a.y - n.y * 40f * density,
                0x33000000, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), crease)
        canvas.restore()

        // 3. 被掀起的纸片：绕折痕反射后落到背侧，显示当前页的镜像（纸的背面）
        val deg = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).toFloat()
        val m = Matrix()
        m.postTranslate(-a.x, -a.y)
        m.postRotate(-deg)
        m.postScale(1f, -1f)
        m.postRotate(deg)
        m.postTranslate(a.x, a.y)

        canvas.save()
        canvas.concat(m)
        canvas.clipPath(halfPlanePath(a, d, n, true))
        drawPage(canvas, index, 0f)
        drawFooter(canvas, index, 0f)
        // 背面比正面暗一些
        val back = Paint().apply {
            shader = LinearGradient(
                a.x, a.y, origin.x, origin.y,
                0x12000000, 0x4A000000, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(-width.toFloat(), -height.toFloat(), width * 2f, height * 2f, back)
        canvas.restore()
    }

    private fun drawEdgeShadow(canvas: Canvas, x: Float, leftSide: Boolean) {
        val w = 24f * density
        val from = if (leftSide) x else x - w
        val p = Paint().apply {
            shader = LinearGradient(from, 0f, from + w, 0f, Color.TRANSPARENT, 0x33000000, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(from, 0f, from + w, height.toFloat(), p)
    }

    // ---------- 触摸 ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (animating) return true
                downX = event.x
                downY = event.y
                moved = false
                forward = event.x > width * 0.5f
                val hasTarget = if (forward) index + 1 < pages.size else index - 1 >= 0
                dragging = hasTarget && mode != TurnMode.NONE
                if (dragging) {
                    // 翻页起点：贴住对应侧边缘，高度跟随手指，任意位置都能起翻
                    origin.set(if (forward) width.toFloat() else 0f, event.y)
                    corner.set(origin.x, origin.y)
                    if (mode == TurnMode.SIMULATION) {
                        target.set(if (forward) -width.toFloat() else 2f * width, origin.y)
                    } else {
                        target.set(if (forward) 0f else width.toFloat(), origin.y)
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true
                if (abs(event.x - downX) > 8 * density || abs(event.y - downY) > 8 * density) moved = true
                // 用「相对位移」而非绝对坐标，手指落在屏幕任意高度都不会让页面瞬间跳走
                val shift = (if (forward) downX - event.x else event.x - downX).coerceAtLeast(0f)
                corner.x = if (forward) origin.x - shift else origin.x + shift
                if (mode == TurnMode.SIMULATION) {
                    // 折痕的纵向位置跟随手指，形成真实的斜向卷曲
                    corner.y = event.y.coerceIn(height * 0.06f, height * 0.98f)
                } else {
                    corner.y = origin.y
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                if (!moved && abs(event.x - downX) < 12 * density && abs(event.y - downY) < 12 * density) {
                    // 轻点：按左右分区翻页，中间区切菜单
                    handleTap(event.x, event.y)
                    return true
                }
                if (!moved) {
                    corner.set(origin.x, origin.y)
                    invalidate()
                    return true
                }
                val shift = abs(corner.x - origin.x)
                val commit = shift > width * (if (mode == TurnMode.SIMULATION) 0.22f else 0.3f)
                finishTurn(commit, PointF(corner.x, corner.y))
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 轻点分区：左 35% 上一页，右 65% 以外下一页，中间切菜单。
     */
    private fun handleTap(x: Float, y: Float) {
        val w = width.toFloat()
        when {
            x > w * 0.65f -> {
                if (index + 1 < pages.size) animateTurn(true, y) else onRequestNext?.invoke()
            }
            x < w * 0.35f -> {
                if (index - 1 >= 0) animateTurn(false, y) else onRequestPrev?.invoke()
            }
            else -> onCenterTap?.invoke()
        }
    }

    /** 点击/自动翻页：从静止状态播放完整动画，起点高度跟随手指 */
    fun animateTurn(forwardTurn: Boolean, fromY: Float = height * 0.72f) {
        val y = fromY.coerceIn(height * 0.1f, height * 0.95f)
        forward = forwardTurn
        origin.set(if (forward) width.toFloat() else 0f, y)
        val start = PointF(origin.x, origin.y)
        val end = PointF(
            if (forward) -width.toFloat() else 2f * width,
            y
        )
        target = end
        startAnimator(start, end, commit = true)
    }

    private fun finishTurn(commit: Boolean, from: PointF) {
        val end = PointF(
            if (commit) target.x else origin.x,
            if (commit) target.y else origin.y
        )
        startAnimator(from, end, commit)
    }

    private fun startAnimator(from: PointF, to: PointF, commit: Boolean) {
        animator?.cancel()
        animating = true
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val duration = (dist / width.toFloat() * 420f).toLong().coerceIn(140L, 520L)
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                corner.set(from.x + dx * t, from.y + dy * t)
                invalidate()
            }
        }
        animator = anim
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                animating = false
                if (commit) {
                    val next = if (forward) index + 1 else index - 1
                    if (next in 0 until pages.size) {
                        index = next
                        onIndexChanged?.invoke(index)
                        if (index >= pages.size - 1) onRequestNext?.invoke()
                    } else if (forward) onRequestNext?.invoke() else onRequestPrev?.invoke()
                }
                corner.set(origin.x, origin.y)
                invalidate()
            }
        })
        anim.start()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
