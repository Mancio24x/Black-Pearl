package org.qosp.notes.ui.links

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.google.android.material.color.MaterialColors
import org.qosp.notes.R
import org.qosp.notes.components.links.GraphEdge
import org.qosp.notes.components.links.GraphNode
import org.qosp.notes.components.links.PointF2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

internal fun GraphNode.displayTitle(context: Context): String = title.ifBlank { context.getString(R.string.indicator_untitled) }

class GraphView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val primary = MaterialColors.getColor(this, org.qosp.notes.R.attr.colorPrimary)
    private val ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
    private val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
    private val caption = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 13 * resources.displayMetrics.scaledDensity
    }
    private val halo = Paint(caption).apply {
        color = surface
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 3 * density
    }
    private val occupiedLabels = Region()
    private val overlap = Region()
    private val candidate = Rect()
    private val labels = LinkedHashMap<GraphNode, RectF>()
    private val dashed = DashPathEffect(floatArrayOf(3 * density, 2 * density), 0f)
    private var nodes = emptyList<GraphNode>()
    private var edges = emptyList<GraphEdge>()
    private var original = emptyMap<String, PointF2>()
    private val positions = mutableMapOf<String, PointF2>()
    private var scale = 1f
    private var dx = 0f
    private var dy = 0f
    private var pressX = 0f
    private var pressY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragged = false
    private var pinched = false
    private var pressedNode: GraphNode? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var held = false
    private val hold = Runnable { if (!dragged && !pinched && pressedNode != null) performLongClick() }
    var onNodeLongPress: (GraphNode) -> Unit = {}
    var onNodeTap: (GraphNode) -> Unit = {}
    var query: String = ""
        set(value) { field = value.trim(); invalidate() }

    private val zoom = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var focusX = 0f
        private var focusY = 0f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            focusX = detector.focusX
            focusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (scale * detector.scaleFactor).coerceIn(0.1f * density, 8f * density)
            dx = detector.focusX - (focusX - dx) * next / scale
            dy = detector.focusY - (focusY - dy) * next / scale
            focusX = detector.focusX
            focusY = detector.focusY
            scale = next
            invalidate()
            return true
        }
    })

    fun setGraph(newNodes: List<GraphNode>, newEdges: List<GraphEdge>, layout: Map<String, PointF2>) {
        if (nodes == newNodes && edges == newEdges && original == layout) return
        nodes = newNodes
        edges = newEdges
        original = layout
        recenter()
    }

    fun recenter() {
        positions.clear()
        positions.putAll(original)
        val points = positions.values
        val left = points.minOfOrNull { it.x } ?: 0f
        val right = points.maxOfOrNull { it.x } ?: 0f
        val top = points.minOfOrNull { it.y } ?: 0f
        val bottom = points.maxOfOrNull { it.y } ?: 0f
        val margin = 68 * density
        scale = min((width - margin) / (right - left).coerceAtLeast(1f),
            (height - margin) / (bottom - top).coerceAtLeast(1f)).coerceIn(0.05f * density, 2f * density)
        dx = width / 2f - (left + right) * scale / 2
        dy = height / 2f - (top + bottom) * scale / 2
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // first layout only: a later resize (the keyboard opening for the search) keeps zoom, position and moved nodes
        if (oldw == 0 || oldh == 0) recenter()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.pathEffect = null
        paint.color = outline
        paint.alpha = 215
        paint.strokeWidth = 1.5f * density
        for (edge in edges) {
            val a = positions[edge.from] ?: continue
            val b = positions[edge.to] ?: continue
            canvas.drawLine(dx + a.x * scale, dy + a.y * scale, dx + b.x * scale, dy + b.y * scale, paint)
        }
        labels.clear()
        occupiedLabels.setEmpty()
        for (node in nodes) {
            val p = positions[node.key] ?: continue
            val x = dx + p.x * scale
            val y = dy + p.y * scale
            val r = (4f + 1.8f * sqrt(node.degree.toFloat())).coerceAtMost(18f) * density
            val matches = node.displayTitle(context).contains(query, ignoreCase = true)
            paint.style = if (node.noteId == null) Paint.Style.STROKE else Paint.Style.FILL
            paint.color = if (node.noteId == null) outline else primary
            paint.alpha = if (matches) 255 else 64
            paint.pathEffect = if (node.noteId == null) dashed else null
            paint.strokeWidth = 1.5f * density
            canvas.drawCircle(x, y, r, paint)
            paint.style = Paint.Style.FILL
            paint.pathEffect = null
            if (matches && x in 0f..width.toFloat() && y in 0f..height.toFloat()) {
                drawLabel(canvas, node, x, y, r)
            }
        }
    }

    private fun drawLabel(canvas: Canvas, node: GraphNode, x: Float, y: Float, radius: Float) {
        val gap = radius + 3 * density
        val maxWidth = min(width * 0.42f, 180 * density)
        val title = TextUtils.ellipsize(node.displayTitle(context), caption, maxWidth, TextUtils.TruncateAt.END).toString()
        val w = caption.measureText(title)
        val h = caption.descent() - caption.ascent()
        val right = x + gap
        val left = x - gap - w
        val middle = y - h / 2
        val preferRight = x >= width / 2f
        val near = if (preferRight) right else left
        val far = if (preferRight) left else right
        val box = RectF()
        fun place(leftEdge: Float, topEdge: Float): Boolean {
            val px = leftEdge.coerceIn(0f, (width - w).coerceAtLeast(0f))
            val py = topEdge.coerceIn(0f, (height - h).coerceAtLeast(0f))
            box.set(px, py, px + w, py + h)
            box.roundOut(candidate)
            if (occupiedLabels.quickReject(candidate)) return true
            overlap.set(candidate)
            return !overlap.op(occupiedLabels, Region.Op.INTERSECT)
        }
        if (!place(near, middle) && !place(far, middle) && !place(x - w / 2, y - gap - h)
            && !place(x - w / 2, y + gap) && !place(near, y - gap - h) && !place(far, y + gap)) {
            place(near, middle)
        }
        occupiedLabels.op(candidate, Region.Op.UNION)
        labels[node] = box
        val baseline = box.top - caption.ascent()
        canvas.drawText(title, box.left, baseline, halo)
        canvas.drawText(title, box.left, baseline, caption)
    }

    private fun hit(x: Float, y: Float): GraphNode? {
        var distance = 24 * density
        var nearest: GraphNode? = null
        for (node in nodes) {
            val p = positions[node.key] ?: continue
            val d = hypot(x - dx - p.x * scale, y - dy - p.y * scale)
            if (d < distance) { distance = d; nearest = node }
        }
        if (nearest != null && distance <= (4 + 1.8f * sqrt(nearest.degree.toFloat())).coerceAtMost(18f) * density)
            return nearest
        return labels.entries.lastOrNull { it.value.contains(x, y) }?.key ?: nearest
    }

    override fun performLongClick(): Boolean {
        val node = pressedNode ?: return super.performLongClick()
        held = true
        onNodeLongPress(node)
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hold)
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        pressedNode?.let(onNodeTap)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        zoom.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                pressX = lastX; pressY = lastY
                pinched = false; dragged = false; held = false
                pressedNode = hit(lastX, lastY)
                if (pressedNode != null) postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(hold)
                pinched = true
                pressedNode = null
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.pointerCount == 2) {
                val remaining = 1 - event.actionIndex
                lastX = event.getX(remaining)
                lastY = event.getY(remaining)
                dragged = true
            }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && !held) {
                if (abs(event.x - pressX) > slop || abs(event.y - pressY) > slop) dragged = true
                if (dragged) {
                    removeCallbacks(hold)
                    val key = pressedNode?.key
                    val pos = positions[key]
                    val sx = event.x - lastX
                    val sy = event.y - lastY
                    if (pos != null && key != null) positions[key] = PointF2(pos.x + sx / scale, pos.y + sy / scale)
                    else { dx += sx; dy += sy }
                    lastX = event.x; lastY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                if (event.actionMasked == MotionEvent.ACTION_UP && !dragged && !pinched && !held) performClick()
                pressedNode = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
