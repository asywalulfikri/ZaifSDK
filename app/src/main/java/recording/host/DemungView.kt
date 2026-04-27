package recording.host

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.math.*

@SuppressLint("UseKtx")
class DemungView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ══════════════════════════════════════════════════════════════
    //  KONSTANTA
    // ══════════════════════════════════════════════════════════════
    private val BILAH_COUNT = 7
    private val TAB_COUNT   = 2
    private val tabLabels   = arrayOf("PELOG", "SLENDRO")
    private val tabTypeKeys = arrayOf("demung_pelog", "demung_slendro")
    private val bilahSoundKeys = Array(BILAH_COUNT) { i -> "type${i + 1}" }
    private val heightRatios   = floatArrayOf(1.00f, 0.94f, 0.88f, 0.83f, 0.78f, 0.73f, 0.68f)

    // ── Listener untuk Rekaman (Penting!) ─────────────────────────
    var onBilahHitListener: ((index: Int, metadata: String) -> Unit)? = null


    // ── Dimensi & Inset ───────────────────────────────────────────
    private val tabHeightDp  = 36f
    private val tabPadHDp    = 6f
    private val tabGapDp     = 4f
    private val tabRadiusDp  = 8f
    private val gapDp        = 5f
    private val bilahPadHDp  = 6f
    private val frameInsetLeftDp   = 100f
    private val frameInsetRightDp  = 100f
    private val frameInsetTopDp    = 18f
    private val frameInsetBottomDp = 22f

    // ══════════════════════════════════════════════════════════════
    //  DATA CLASS ANIMASI
    // ══════════════════════════════════════════════════════════════
    private data class BilahAnim(
        val bilahIndex: Int,
        var hammerProgress:  Float = 0f,
        var vibrateProgress: Float = 0f,
        val ripples: MutableList<RippleAnim> = mutableListOf(),
        var hammerAnim:  ValueAnimator? = null,
        var vibrateAnim: ValueAnimator? = null,
        var active: Boolean = true
    )

    private data class RippleAnim(
        var progress: Float = 0f,
        var anim: ValueAnimator? = null,
        var alive: Boolean = true
    )

    private val activeAnims = mutableListOf<BilahAnim>()

    // ══════════════════════════════════════════════════════════════
    //  STATE UI
    // ══════════════════════════════════════════════════════════════
    private var selectedTab   = 0
    private var pressedTab    = -1

    private val bilahRects = Array(BILAH_COUNT) { RectF() }
    private val tabRects   = Array(TAB_COUNT)   { RectF() }
    private val frameRect  = RectF()
    private val innerRect  = RectF()

    private var bilahBitmap: Bitmap? = null
    private var paluBitmap:  Bitmap? = null
    private var frameBitmap: Bitmap? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bilahGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 220, 80); style = Paint.Style.FILL
    }
    private val tabBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A0D00") }
    private val tabNormalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3D2510") }
    private val tabActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B8720A") }
    private val tabPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E09020") }
    private val tabStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9B6A14"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val tabLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8D5A0"); textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val tabActiveLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#FFD040") }
    private val ripplePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; color = Color.parseColor("#FF8C00") }

    private val notAngka = arrayOf("1","2","3","4","5","6","7")
    private val notAngkaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = ResourcesCompat.getFont(context, sound.recorder.widget.R.font.campton_bold); color = Color.parseColor("#1A0800") }
    private val notAngkaGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = ResourcesCompat.getFont(context, sound.recorder.widget.R.font.campton_bold); color = Color.parseColor("#FFD070") }

    init {
        ContextCompat.getDrawable(context, R.drawable.bilah_gold)?.let { d ->
            bilahBitmap = Bitmap.createBitmap(d.intrinsicWidth, d.intrinsicHeight, Bitmap.Config.ARGB_8888).also {
                val c = Canvas(it); d.setBounds(0, 0, c.width, c.height); d.draw(c)
            }
        }
        ContextCompat.getDrawable(context, R.drawable.palu)?.let { d ->
            paluBitmap = Bitmap.createBitmap(d.intrinsicWidth, d.intrinsicHeight, Bitmap.Config.ARGB_8888).also {
                val c = Canvas(it); d.setBounds(0, 0, c.width, c.height); d.draw(c)
            }
        }
        ContextCompat.getDrawable(context, R.drawable.frame_saron_3)?.let { d ->
            frameBitmap = Bitmap.createBitmap(d.intrinsicWidth, d.intrinsicHeight, Bitmap.Config.ARGB_8888).also {
                val c = Canvas(it); d.setBounds(0, 0, c.width, c.height); d.draw(c)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val dp = resources.displayMetrics.density
        val tabHeightPx = tabHeightDp * dp
        val tabPadHPx   = tabPadHDp   * dp
        val tabGapPx    = tabGapDp    * dp
        val gapPx       = gapDp       * dp
        val bilahPadPx  = bilahPadHDp * dp

        val tabTop    = tabPadHPx / 2f
        val tabBottom = tabTop + tabHeightPx
        tabLabelPaint.textSize       = tabHeightPx * 0.38f
        tabActiveLabelPaint.textSize = tabHeightPx * 0.38f

        val tabHPad = tabPadHDp * 3f * dp
        val tabWidths = FloatArray(TAB_COUNT) { i -> tabLabelPaint.measureText(tabLabels[i]) + tabHPad * 2f }
        val totalTabsW = tabWidths.sum() + tabGapPx * (TAB_COUNT - 1)

        var startX = (w - totalTabsW) / 2f
        for (i in 0 until TAB_COUNT) {
            tabRects[i].set(startX, tabTop, startX + tabWidths[i], tabBottom)
            startX += tabWidths[i] + tabGapPx
        }

        val frameTop = tabBottom + tabPadHPx
        frameRect.set(0f, frameTop, w.toFloat(), h.toFloat())
        innerRect.set(frameRect.left + frameInsetLeftDp*dp, frameRect.top + frameInsetTopDp*dp, frameRect.right - frameInsetRightDp*dp, frameRect.bottom - frameInsetBottomDp*dp)

        val bilahAreaW   = innerRect.width() - bilahPadPx * 2
        val totalGap     = gapPx * (BILAH_COUNT - 1)
        val bilahWidth   = (bilahAreaW - totalGap) / BILAH_COUNT
        val innerCenterY = innerRect.centerY()

        for (i in 0 until BILAH_COUNT) {
            val left   = innerRect.left + bilahPadPx + i * (bilahWidth + gapPx)
            val right  = left + bilahWidth
            val bilahH = innerRect.height() * heightRatios[i] * 0.88f
            bilahRects[i].set(left, innerCenterY - bilahH / 2f, right, innerCenterY + bilahH / 2f)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ANIMASI
    // ══════════════════════════════════════════════════════════════
    fun triggerHitAnimation(bilahIndex: Int) {
        activeAnims.filter { it.bilahIndex == bilahIndex }.forEach { it.hammerAnim?.cancel(); it.vibrateAnim?.cancel(); it.active = false }
        activeAnims.removeAll { !it.active }

        val anim = BilahAnim(bilahIndex)
        activeAnims.add(anim)

        val hammerAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320; interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim.hammerProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) { anim.hammerProgress = 0f; invalidate() } })
        }
        val vibrateAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 700; startDelay = 80; interpolator = DecelerateInterpolator(2f)
            addUpdateListener { anim.vibrateProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) { anim.active = false; activeAnims.removeAll { !it.active }; invalidate() } })
        }
        anim.hammerAnim = hammerAnim; anim.vibrateAnim = vibrateAnim
        hammerAnim.start(); vibrateAnim.start()

        for (delay in longArrayOf(80L, 180L, 300L)) {
            val ripple = RippleAnim()
            anim.ripples.add(ripple)
            ripple.anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600; startDelay = delay; interpolator = DecelerateInterpolator(1.2f)
                addUpdateListener { ripple.progress = it.animatedValue as Float; invalidate() }
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) { ripple.alive = false; invalidate() } })
                start()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  DRAW
    // ══════════════════════════════════════════════════════════════
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawTabs(canvas)
        drawFrameBitmap(canvas)
        drawBilah(canvas)
        drawRipples(canvas)
        drawHammers(canvas)
    }

    private fun drawTabs(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        val radius = tabRadiusDp * dp
        val stripRect = RectF(tabRects[0].left - 2f*dp, tabRects[0].top - 2f*dp, tabRects[TAB_COUNT-1].right + 2f*dp, tabRects[0].bottom + 2f*dp)
        canvas.drawRoundRect(stripRect, radius+2f, radius+2f, tabBgPaint)
        for (i in 0 until TAB_COUNT) {
            val rect = tabRects[i]
            val fill = when { i == pressedTab -> tabPressedPaint; i == selectedTab -> tabActivePaint; else -> tabNormalPaint }
            canvas.drawRoundRect(rect, radius, radius, fill)
            canvas.drawRoundRect(rect, radius, radius, tabStrokePaint)
            val lp = if (i == selectedTab) tabActiveLabelPaint else tabLabelPaint
            canvas.drawText(tabLabels[i], rect.centerX(), rect.centerY() + lp.textSize * 0.36f, lp)
        }
    }

    private fun drawFrameBitmap(canvas: Canvas) { frameBitmap?.let { canvas.drawBitmap(it, null, frameRect, bitmapPaint) } }

    private fun drawRipples(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        for (bilahAnim in activeAnims) {
            val rect = bilahRects[bilahAnim.bilahIndex]
            val cx = rect.centerX(); val cy = rect.centerY()
            val maxR = rect.width() * 1.6f
            for (ripple in bilahAnim.ripples) {
                if (!ripple.alive && ripple.progress >= 1f) continue
                val p = ripple.progress
                val alpha = ((1f-p)*180).toInt().coerceIn(0, 255)
                ripplePaint.alpha = alpha; ripplePaint.strokeWidth = 2.5f*dp
                canvas.drawCircle(cx, cy, maxR*p, ripplePaint)
                if (p > 0.1f) { ripplePaint2.alpha = (alpha*0.5f).toInt(); ripplePaint2.strokeWidth = 1.5f*dp; canvas.drawCircle(cx, cy, maxR*p*0.55f, ripplePaint2) }
            }
        }
    }

    private fun drawBilah(canvas: Canvas) {
        val bmp = bilahBitmap; val dp = resources.displayMetrics.density
        for (i in 0 until BILAH_COUNT) {
            val baseRect = bilahRects[i]; val radius = baseRect.width() * 0.14f
            val anim = activeAnims.firstOrNull { it.bilahIndex == i && it.active }
            val vibOff = if (anim != null) sin((1f-anim.vibrateProgress)*35f*Math.PI.toFloat())*baseRect.width()*0.045f*anim.vibrateProgress else 0f
            val rect = RectF(baseRect.left+vibOff, baseRect.top, baseRect.right+vibOff, baseRect.bottom)

            if (anim != null && anim.vibrateProgress > 0.6f) {
                bilahGlowPaint.alpha = ((anim.vibrateProgress-0.6f)/0.4f*120).toInt()
                val ex = 3f*dp*anim.vibrateProgress
                canvas.drawRoundRect(RectF(rect.left-ex, rect.top-ex, rect.right+ex, rect.bottom+ex), radius+ex, radius+ex, bilahGlowPaint)
            }
            if (bmp != null) {
                val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
                canvas.save(); canvas.clipPath(path); canvas.drawBitmap(bmp, null, rect, bitmapPaint); canvas.restore()
            } else {
                canvas.drawRoundRect(rect, radius, radius, Paint().apply { val s=190-i*10; color=Color.rgb(s,s-20,s-55) })
            }
            canvas.drawOval(RectF(rect.left+3*dp, rect.bottom-2*dp, rect.right-3*dp, rect.bottom+5*dp), Paint().apply { color=Color.argb(80,0,0,0); maskFilter=BlurMaskFilter(4f*dp, BlurMaskFilter.Blur.NORMAL) })
            val fs = rect.width()*0.25f
            notAngkaPaint.textSize = fs; notAngkaGlowPaint.textSize = fs; notAngkaGlowPaint.maskFilter = BlurMaskFilter(fs*0.3f, BlurMaskFilter.Blur.NORMAL)
            if (anim != null && anim.vibrateProgress > 0.3f) { notAngkaGlowPaint.alpha=(anim.vibrateProgress*200).toInt(); canvas.drawText(notAngka[i], rect.centerX()+vibOff, rect.bottom-rect.width()*0.18f, notAngkaGlowPaint) }
            canvas.drawText(notAngka[i], rect.centerX()+vibOff, rect.bottom-rect.width()*0.18f, notAngkaPaint)
        }
    }

    private fun drawHammers(canvas: Canvas) {
        val bmp = paluBitmap ?: return; val dp = resources.displayMetrics.density
        for (anim in activeAnims.toList()) {
            if (!anim.active || anim.hammerProgress <= 0f) continue
            val p = anim.hammerProgress; val bilahRect = bilahRects[anim.bilahIndex]; val paluW = bilahRect.width()*3.2f; val paluH = paluW*(bmp.height.toFloat()/bmp.width.toFloat())
            val rot = if (p <= 0.42f) lerp(55f,0f,p/0.42f) else lerp(0f,55f,(p-0.42f)/0.58f)
            val headOffX = -0.78f*paluW; val headOffY = -0.78f*paluH; val rotRad = Math.toRadians(rot.toDouble())
            val cosR = cos(rotRad).toFloat(); val sinR = sin(rotRad).toFloat()
            val px = bilahRect.centerX()-(headOffX*cosR-headOffY*sinR); val py = (bilahRect.bottom+2f*dp)-(headOffX*sinR+headOffY*cosR)
            val alpha = when { p<0.10f -> (p/0.10f*255).toInt(); p>0.80f -> ((1f-(p-0.80f)/0.20f)*255).toInt(); else -> 255 }.coerceIn(0, 255)
            canvas.save(); canvas.rotate(rot, px, py); bitmapPaint.alpha = alpha; canvas.drawBitmap(bmp, null, RectF(px-0.9f*paluW, py-0.9f*paluH, px+0.1f*paluW, py+0.1f*paluH), bitmapPaint); bitmapPaint.alpha = 255; canvas.restore()
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  TOUCH & LOGIC
    // ══════════════════════════════════════════════════════════════
    private val pointerLastBilah = mutableMapOf<Int, Int>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pIdx = event.actionIndex
        val pId = event.getPointerId(pIdx)
        when (action) {
            MotionEvent.ACTION_DOWN -> { val tab = findTabAt(event.x, event.y); if (tab != -1) { pressedTab = tab; invalidate(); return true }; val bilah = findBilahAt(event.x, event.y); if (bilah != -1) { pointerLastBilah[pId] = bilah; hitBilah(bilah); invalidate() }; return true }
            MotionEvent.ACTION_POINTER_DOWN -> { val bilah = findBilahAt(event.getX(pIdx), event.getY(pIdx)); if (bilah != -1) { pointerLastBilah[pId] = bilah; hitBilah(bilah); invalidate() }; return true }
            MotionEvent.ACTION_MOVE -> { for (i in 0 until event.pointerCount) { val id = event.getPointerId(i); val b = findBilahAt(event.getX(i), event.getY(i)); if (b != -1 && b != pointerLastBilah[id]) { pointerLastBilah[id] = b; hitBilah(b) } }; invalidate(); return true }
            MotionEvent.ACTION_POINTER_UP -> { pointerLastBilah.remove(pId); invalidate(); return true }
            MotionEvent.ACTION_UP -> { if (pressedTab != -1) { if (findTabAt(event.x, event.y) == pressedTab) { selectedTab = pressedTab; performClick() }; pressedTab = -1 }; pointerLastBilah.remove(pId); invalidate(); return true }
            MotionEvent.ACTION_CANCEL -> { pointerLastBilah.clear(); pressedTab = -1; invalidate() }
        }
        return super.onTouchEvent(event)
    }

    // ── Update hitBilah untuk Mendukung Rekaman ───────────────────
    private fun hitBilah(index: Int) {
        val typeKey = tabTypeKeys[selectedTab]
        SoundPlayUtils.playSound(typeKey, bilahSoundKeys[index])
        triggerHitAnimation(index)

        // TRIGGER RECORDING KE SDK PANEL
        onBilahHitListener?.invoke(index, typeKey)

        performClick()
    }

    fun setSelectedTab(index: Int) { if (index in 0 until TAB_COUNT) { selectedTab = index; invalidate() } }
    fun getSelectedTab(): Int = selectedTab

    override fun performClick(): Boolean { super.performClick(); return true }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun findBilahAt(x: Float, y: Float): Int { for (i in 0 until BILAH_COUNT) if (bilahRects[i].contains(x, y)) return i; return -1 }
    private fun findTabAt(x: Float, y: Float): Int { for (i in 0 until TAB_COUNT) if (tabRects[i].contains(x, y)) return i; return -1 }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        // Buat salinan list agar tidak terjadi ConcurrentModificationException
        val animsToCancel = activeAnims.toList()

        animsToCancel.forEach {
            it.hammerAnim?.removeAllUpdateListeners() // Hapus listener agar tidak update UI lagi
            it.hammerAnim?.cancel()

            it.vibrateAnim?.removeAllUpdateListeners()
            it.vibrateAnim?.cancel()

            it.ripples.forEach { r ->
                r.anim?.removeAllUpdateListeners()
                r.anim?.cancel()
            }
        }
        activeAnims.clear()
    }
}