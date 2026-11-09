package org.qosp.notes.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Spanned
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import io.noties.markwon.image.AsyncDrawableSpan
import org.qosp.notes.R
import org.qosp.notes.ui.editor.markdown.ImageBlocks
import org.qosp.notes.ui.editor.markdown.ImageGestures
import org.qosp.notes.ui.editor.markdown.ImageLayoutPlugin
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Black Pearl: drawn over the note preview, lets the user handle a picture directly. Tap a picture: a rounded frame
 * appears with a round **resize** handle on its corner (live size, snaps to 25/50/75/100 %) and a **move** handle on
 * top (drag the picture between the lines; a line shows where it lands), plus a small bar: alignment, more, remove.
 * Touches elsewhere go through to the text (and close the frame).
 */
class ImageHandlesView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    interface Listener {
        fun onResize(ref: ImageBlocks.Ref, widthPercent: Int)
        fun onMove(ref: ImageBlocks.Ref, steps: Int)
        fun onAlign(ref: ImageBlocks.Ref, align: ImageBlocks.Align)
        fun onMore(ref: ImageBlocks.Ref)
        fun onRemove(ref: ImageBlocks.Ref)
    }

    var textView: TextView? = null
    var scrollView: NestedScrollView? = null
    var listener: Listener? = null

    var selected: ImageBlocks.Ref? = null
        private set

    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = value * density

    private val primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val background = MaterialColors.getColor(this, android.R.attr.colorBackground)
    private val onPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary)

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
        color = primary
    }
    private val coverPaint = Paint().apply { color = background }
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primary
        strokeWidth = dp(3)
        strokeCap = Paint.Cap.ROUND
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary }
    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onPrimary
        textSize = dp(14)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val image = RectF() // picture bounds, in this view's coordinates
    private var widthPercent: Int? = null
    private var align = ImageBlocks.Align.LEFT

    // Gesture state
    private var resizing = false
    private var resizePercent = 0
    private var moving = false
    private var moveY = 0f
    private var dropIndex = -1
    private var dropY = 0f

    private val frame = View(context).apply {
        contentDescription = context.getString(R.string.image_actions_title)
        isClickable = true
    }
    private val resizeHandle = handle(R.drawable.ic_resize, R.string.image_resize)
    private val moveHandle = handle(R.drawable.ic_drag, R.string.image_move)
    private val toolbar = MaterialCardView(context).apply {
        radius = dp(24)
        cardElevation = dp(3)
        setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4).toInt(), 0, dp(4).toInt(), 0)
                addView(toolButton(R.drawable.ic_align_left, R.string.image_align_left) { align(ImageBlocks.Align.LEFT) })
                addView(toolButton(R.drawable.ic_align_center, R.string.image_align_center) { align(ImageBlocks.Align.CENTER) })
                addView(toolButton(R.drawable.ic_align_right, R.string.image_align_right) { align(ImageBlocks.Align.RIGHT) })
                addView(toolButton(R.drawable.ic_more_vert, R.string.sheet_more_settings) { selected?.let { listener?.onMore(it) } })
                addView(toolButton(R.drawable.ic_bin, R.string.image_remove_from_note) { selected?.let { listener?.onRemove(it) } })
            }
        )
    }

    // The text is redrawn after each change (and pictures load again): follow the selected picture, hiding the
    // handles while it is not drawn yet
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (selected != null && !resizing && !moving) showHandles(place())
        true
    }

    private fun showHandles(show: Boolean) {
        listOf(frame, toolbar, moveHandle, resizeHandle).forEach { if (it.isVisible != show) it.isVisible = show }
    }

    init {
        setWillNotDraw(false)
        addView(frame, LayoutParams(0, 0))
        addView(toolbar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(moveHandle)
        addView(resizeHandle)
        listOf(frame, toolbar, moveHandle, resizeHandle).forEach { it.isVisible = false }
        setupResize()
        setupMove()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDraw)
        super.onDetachedFromWindow()
    }

    fun select(ref: ImageBlocks.Ref) {
        selected = ref
        placed.setEmpty()
        if (!place()) return clear()
        showHandles(true)
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun clear() {
        selected = null
        placed.setEmpty()
        resizing = false
        moving = false
        showHandles(false)
        invalidate()
    }

    // A tap outside the frame and the handles closes it, and still reaches the text below
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && selected != null) clear()
        return false
    }

    /** Finds the selected picture in the text and puts frame, handles and bar around it. */
    private val placed = RectF()

    private fun place(): Boolean {
        val ref = selected ?: return false
        if (!findImage(ref, image) || image.isEmpty) return false
        if (image == placed) return true // nothing moved: avoid redrawing every frame
        placed.set(image)
        frame.fitTo(image)
        resizeHandle.center(if (align == ImageBlocks.Align.RIGHT) image.left else image.right, image.bottom)
        moveHandle.center(image.centerX(), image.top)
        toolbar.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val barW = toolbar.measuredWidth.toFloat()
        val barH = toolbar.measuredHeight.toFloat()
        toolbar.translationX = (image.centerX() - barW / 2).coerceIn(0f, (width - barW).coerceAtLeast(0f))
        // above the picture; below it if there is no room; else over its lower part (never cut off)
        toolbar.translationY = when {
            image.top - barH - dp(28) >= 0 -> image.top - barH - dp(28)
            image.bottom + dp(24) + barH <= height -> image.bottom + dp(24)
            else -> (image.bottom - barH - dp(28)).coerceAtLeast(image.top + dp(28))
        }
        invalidate()
        return true
    }

    private fun findImage(ref: ImageBlocks.Ref, out: RectF): Boolean {
        val tv = textView ?: return false
        val text = tv.text as? Spanned ?: return false
        val layout = tv.layout ?: return false
        val span = text.getSpans(0, text.length, ImageLayoutPlugin.ImageClickSpan::class.java)
            .firstOrNull { it.ref == ref } ?: return false
        val start = text.getSpanStart(span)
        val drawable = text.getSpans(start, start + 1, AsyncDrawableSpan::class.java).firstOrNull()?.drawable ?: return false
        val bounds = drawable.bounds
        if (bounds.isEmpty) return false
        val line = layout.getLineForOffset(start)
        // the text view and this overlay share the same parent
        val x = layout.getPrimaryHorizontal(start) + tv.totalPaddingLeft + tv.left - this.left
        val y = (layout.getLineTop(line) + tv.totalPaddingTop + tv.top - this.top).toFloat()
        out.set(x, y, x + bounds.width(), y + bounds.height())
        val textWidth = (layout.width).coerceAtLeast(1)
        widthPercent = (bounds.width() * 100f / textWidth).roundToInt()
        align = when (layout.getParagraphAlignment(line)) {
            android.text.Layout.Alignment.ALIGN_CENTER -> ImageBlocks.Align.CENTER
            android.text.Layout.Alignment.ALIGN_OPPOSITE -> ImageBlocks.Align.RIGHT
            else -> ImageBlocks.Align.LEFT
        }
        return true
    }

    // ---------- resize ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun setupResize() {
        var startX = 0f
        var startWidth = 0f
        resizeHandle.setOnTouchListener { _, e ->
            val tv = textView ?: return@setOnTouchListener false
            val textWidth = (tv.layout?.width ?: tv.width).toFloat().coerceAtLeast(1f)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    resizing = true
                    startX = e.rawX
                    startWidth = image.width()
                    resizePercent = (startWidth * 100f / textWidth).roundToInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    var dx = e.rawX - startX
                    if (align == ImageBlocks.Align.RIGHT) dx = -dx
                    if (align == ImageBlocks.Align.CENTER) dx *= 2 // centred: grows on both sides
                    val percent = ImageGestures.snapPercent((startWidth + dx) * 100f / textWidth)
                    if (percent != resizePercent) {
                        if (percent in ImageGestures.SNAPS) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        resizePercent = percent
                    }
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resizing = false
                    val ref = selected
                    if (ref != null && e.actionMasked == MotionEvent.ACTION_UP && resizePercent != widthPercent) {
                        listener?.onResize(ref, resizePercent)
                    }
                    invalidate()
                }
            }
            true
        }
    }

    /** The picture's frame at the size being dragged (kept on the side it is aligned to). */
    private fun resizedRect(out: RectF) {
        val tv = textView ?: return
        val textWidth = (tv.layout?.width ?: tv.width).toFloat()
        val w = textWidth * resizePercent / 100f
        val h = if (image.width() > 0) image.height() * w / image.width() else image.height()
        val textLeft = tv.left - left + tv.totalPaddingLeft.toFloat()
        val l = when (align) {
            ImageBlocks.Align.LEFT -> textLeft
            ImageBlocks.Align.CENTER -> textLeft + (textWidth - w) / 2
            ImageBlocks.Align.RIGHT -> textLeft + textWidth - w
        }
        out.set(l, image.top, l + w, image.top + h)
    }

    // ---------- move ----------

    private val autoScroll = object : Runnable {
        var speed = 0
        override fun run() {
            if (!moving || speed == 0) return
            scrollView?.scrollBy(0, speed)
            moveY += speed
            updateDrop()
            postOnAnimation(this)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMove() {
        var grab = 0f
        var downY = 0f
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        fun drag(e: MotionEvent, fromHandle: Boolean): Boolean {
            val location = IntArray(2).also { getLocationOnScreen(it) }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    downY = e.rawY
                    grab = e.rawY - location[1] - image.top
                    moveY = image.top
                    dropIndex = -1
                    moving = fromHandle
                    if (moving) invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moving && abs(e.rawY - downY) > slop) moving = true
                    if (!moving) return true
                    moveY = e.rawY - location[1] - grab
                    updateDrop()
                    // near the top or bottom of the screen: scroll the note
                    val sv = scrollView
                    if (sv != null) {
                        val svLoc = IntArray(2).also { sv.getLocationOnScreen(it) }
                        val edge = dp(72)
                        autoScroll.speed = when {
                            e.rawY < svLoc[1] + edge -> -dp(10).toInt()
                            e.rawY > svLoc[1] + sv.height - edge -> dp(10).toInt()
                            else -> 0
                        }
                        removeCallbacks(autoScroll)
                        if (autoScroll.speed != 0) postOnAnimation(autoScroll)
                    }
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(autoScroll)
                    val ref = selected
                    if (!moving) {
                        // a tap on the selected picture: its options
                        if (!fromHandle && e.actionMasked == MotionEvent.ACTION_UP && ref != null) listener?.onMore(ref)
                        return true
                    }
                    moving = false
                    val steps = if (dropIndex >= 0) ImageGestures.steps(imageUnit(), dropIndex) else 0
                    if (ref != null && e.actionMasked == MotionEvent.ACTION_UP && steps != 0) listener?.onMove(ref, steps)
                    dropIndex = -1
                    invalidate()
                }
            }
            return true
        }
        moveHandle.setOnTouchListener { _, e -> drag(e, fromHandle = true) }
        frame.setOnTouchListener { _, e -> drag(e, fromHandle = false) }
    }

    private fun units(): List<IntRange> = ImageGestures.units(textView?.text ?: "")

    private fun imageUnit(): Int {
        val tv = textView ?: return -1
        val text = tv.text as? Spanned ?: return -1
        val span = text.getSpans(0, text.length, ImageLayoutPlugin.ImageClickSpan::class.java)
            .firstOrNull { it.ref == selected } ?: return -1
        val start = text.getSpanStart(span)
        return units().indexOfFirst { start in it }
    }

    /** Nearest place between two lines to the dragged picture's middle. */
    private fun updateDrop() {
        val tv = textView ?: return
        val layout = tv.layout ?: return
        val offsetY = tv.top - top + tv.totalPaddingTop.toFloat()
        val units = units()
        if (units.isEmpty()) return
        val boundaries = units.map { layout.getLineTop(layout.getLineForOffset(it.first)) + offsetY } +
            (layout.getLineBottom(layout.getLineForOffset(units.last().last)) + offsetY)
        val middle = moveY + image.height() / 2
        val index = boundaries.indices.minByOrNull { abs(boundaries[it] - middle) } ?: return
        if (index != dropIndex) {
            dropIndex = index
            dropY = boundaries[index]
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // ---------- drawing ----------

    private val tmp = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (selected == null) return
        val drawable = currentDrawable()
        when {
            resizing -> {
                resizedRect(tmp)
                canvas.drawRect(image, coverPaint) // hide the picture at its old size
                drawable?.let { d ->
                    val save = canvas.save()
                    canvas.translate(tmp.left, tmp.top)
                    canvas.scale(tmp.width() / image.width(), tmp.height() / image.height())
                    d.draw(canvas)
                    canvas.restoreToCount(save)
                }
                canvas.drawRoundRect(tmp, dp(12), dp(12), framePaint)
                bubble(canvas, "$resizePercent%", tmp.centerX(), tmp.centerY())
            }
            moving -> {
                canvas.drawRect(image, coverPaint)
                if (dropIndex >= 0) canvas.drawLine(dp(8), dropY, width - dp(8), dropY, dropPaint)
                drawable?.let { d ->
                    val save = canvas.save()
                    canvas.translate(image.left, moveY)
                    canvas.saveLayerAlpha(0f, 0f, image.width(), image.height(), 200)
                    d.draw(canvas)
                    canvas.restoreToCount(save)
                }
                tmp.set(image.left, moveY, image.right, moveY + image.height())
                canvas.drawRoundRect(tmp, dp(12), dp(12), framePaint)
            }
            else -> canvas.drawRoundRect(image, dp(12), dp(12), framePaint)
        }
    }

    private fun currentDrawable(): android.graphics.drawable.Drawable? {
        val tv = textView ?: return null
        val text = tv.text as? Spanned ?: return null
        val span = text.getSpans(0, text.length, ImageLayoutPlugin.ImageClickSpan::class.java)
            .firstOrNull { it.ref == selected } ?: return null
        val start = text.getSpanStart(span)
        return text.getSpans(start, start + 1, AsyncDrawableSpan::class.java).firstOrNull()?.drawable
    }

    private fun bubble(canvas: Canvas, label: String, x: Float, y: Float) {
        val w = bubbleText.measureText(label) + dp(24)
        val h = dp(32)
        tmp.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        canvas.drawRoundRect(tmp, h / 2, h / 2, bubblePaint)
        canvas.drawText(label, x, y - (bubbleText.descent() + bubbleText.ascent()) / 2, bubbleText)
    }

    // ---------- small helpers ----------

    private fun align(to: ImageBlocks.Align) {
        val ref = selected ?: return
        if (to != align) listener?.onAlign(ref, to)
    }

    private fun handle(icon: Int, label: Int) = ImageButton(context).apply {
        val size = dp(HANDLE_DP).toInt()
        layoutParams = LayoutParams(size, size)
        minimumWidth = size
        minimumHeight = size
        setImageDrawable(ContextCompat.getDrawable(context, icon))
        imageTintList = android.content.res.ColorStateList.valueOf(onPrimary)
        background = ContextCompat.getDrawable(context, R.drawable.bg_handle)
        elevation = dp(3)
        contentDescription = context.getString(label)
        TooltipCompat.setTooltipText(this, contentDescription)
    }

    private fun toolButton(icon: Int, label: Int, onClick: () -> Unit) = ImageButton(context).apply {
        val size = dp(44).toInt()
        layoutParams = LinearLayout.LayoutParams(size, size)
        setImageDrawable(ContextCompat.getDrawable(context, icon))
        background = ContextCompat.getDrawable(context, R.drawable.bg_round_ripple)
        contentDescription = context.getString(label)
        TooltipCompat.setTooltipText(this, contentDescription)
        setOnClickListener { onClick() }
    }

    private fun View.fitTo(rect: RectF) {
        layoutParams = (layoutParams as LayoutParams).apply {
            width = rect.width().toInt()
            height = rect.height().toInt()
        }
        translationX = rect.left
        translationY = rect.top
    }

    private fun View.center(x: Float, y: Float) {
        val size = HANDLE_DP * density
        translationX = x - size / 2
        translationY = y - size / 2
    }

    private companion object {
        const val HANDLE_DP = 36
    }
}
