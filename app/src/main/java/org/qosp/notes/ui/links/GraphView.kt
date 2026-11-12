package org.qosp.notes.ui.links

import android.animation.ValueAnimator
import androidx.core.animation.doOnEnd
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.provider.Settings
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import com.google.android.material.color.MaterialColors
import org.qosp.notes.R
import org.qosp.notes.components.links.GraphEdge
import org.qosp.notes.components.links.GraphNode
import org.qosp.notes.components.links.GraphPhysics
import org.qosp.notes.components.links.NodeKind
import org.qosp.notes.components.links.PointF2
import org.qosp.notes.data.model.Attachment
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.roundToInt

internal fun GraphNode.displayTitle(context: Context): String {
    if (title.isNotBlank()) return title
    if (kind != NodeKind.ATTACHMENT) return context.getString(R.string.indicator_untitled)
    return context.getString(when (attachmentType) {
        Attachment.Type.IMAGE -> R.string.network_file_photo
        Attachment.Type.AUDIO -> R.string.network_file_audio
        Attachment.Type.VIDEO -> R.string.network_file_video
        else -> R.string.network_file_other
    })
}

class GraphView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val primary = MaterialColors.getColor(this, org.qosp.notes.R.attr.colorPrimary)
    private val ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
    private val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
    private val tertiary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, primary)
    private val muted = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, ink)
    private val square = RectF()
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
    private data class LabelText(val title: String, val width: Float)
    private val labelText = HashMap<GraphNode, LabelText>()
    private var labelOrder = emptyList<GraphNode>()
    private var transition: ValueAnimator? = null
    private var simulatedKeys: Set<String> = emptySet()
    private val dashed = DashPathEffect(floatArrayOf(3 * density, 2 * density), 0f)
    private var nodes = emptyList<GraphNode>()
    private var edges = emptyList<GraphEdge>()
    private var original = emptyMap<String, PointF2>()
    private val positions = mutableMapOf<String, PointF2>()
    private var neighbours = emptyMap<String, Set<String>>()
    private var physics: GraphPhysics? = null
    private var animating = false
    private val frame = Choreographer.FrameCallback {
        animating = false
        val sim = physics ?: return@FrameCallback
        val moving = sim.step()
        for (key in simulatedKeys) sim.position(key)?.let { positions[key] = it }
        invalidate()
        if (moving) runPhysics()
    }
    private var schedule: TimelapseSchedule? = null
    internal var clock: () -> Long = { SystemClock.uptimeMillis() }
    private var startedAt = 0L
    private var shown: Set<String>? = null
    private var timelapseQueued = false
    private val timelapseFrame = Choreographer.FrameCallback {
        timelapseQueued = false
        timelapseTick()
    }

    internal fun timelapseTick() {
        val plan = schedule ?: return
        val elapsed = clock() - startedAt
        if (elapsed >= plan.duration) {
            stopTimelapse()
        } else {
            shown = plan.visibleAt(elapsed)
            invalidate()
            queueTimelapse()
        }
    }
    private var scale = 1f
    private var dx = 0f
    private var dy = 0f
    private var pressX = 0f
    private var pressY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragSimulation: GraphPhysics? = null
    private var dragged = false
    private var pinched = false
    private var pressedNode: GraphNode? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var held = false
    private val hold = Runnable { if (!dragged && !pinched && pressedNode != null) performLongClick() }
    var onNodeLongPress: (GraphNode) -> Unit = {}
    var onNodeTap: (GraphNode) -> Unit = {}

    /** Node kept highlighted with its neighbours while no finger is on the graph (its action sheet is open). */
    var highlighted: GraphNode? = null
        set(value) { field = value; invalidate() }

    /** The node whose links are highlighted (the one under the finger, else [highlighted]) and its neighbours. */
    internal val highlightedKeys: Set<String>
        get() {
            val key = (pressedNode ?: highlighted)?.key ?: return emptySet()
            return neighbours[key].orEmpty() + key
        }

    internal fun labelBounds(key: String): RectF? = labels.entries.firstOrNull { it.key.key == key }?.value
    internal fun screenPosition(key: String): PointF2? = positions[key]?.let { PointF2(dx + it.x * scale, dy + it.y * scale) }

    internal fun positionOf(key: String): PointF2? = positions[key]

    /** Keys of the nodes shown so far while the timelapse plays; null when it is not playing (everything is shown). */
    internal val visibleKeys: Set<String>? get() = shown

    val isPlayingTimelapse: Boolean get() = schedule != null

    /** Replays the graph growing: notes (key to creation seconds) appear in creation order, other nodes with their first note. */
    fun playTimelapse(appearAt: Map<String, Long>) {
        stopTimelapse()
        finishTransition()
        stopAnimation()
        val noteKeys = nodes.filter { it.kind == NodeKind.NOTE }.map { it.key }.toSet()
        val dated = appearAt.filterKeys { it in noteKeys }
        if (dated.size < 2) return
        val others = nodes.filter { it.kind != NodeKind.NOTE }.map { it.key }
        val plan = Timelapse.schedule(dated, others, neighbours)
        schedule = plan
        startedAt = clock()
        shown = plan.visibleAt(0)
        invalidate()
        queueTimelapse()
    }

    fun stopTimelapse() {
        if (timelapseQueued) Choreographer.getInstance().removeFrameCallback(timelapseFrame)
        timelapseQueued = false
        val wasPlaying = schedule != null
        schedule = null
        shown = null
        if (wasPlaying) invalidate()
    }

    private fun queueTimelapse() {
        if (timelapseQueued) return
        timelapseQueued = true
        Choreographer.getInstance().postFrameCallback(timelapseFrame)
    }
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
            val next = (scale * detector.scaleFactor).coerceIn(0.005f * density, 12f * density)
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
        val sameGeometry = original == layout && edges == newEdges
        val previous = positions.toMap()
        val previousCamera = Camera(scale, dx, dy)
        nodes = newNodes
        labelOrder = nodes.sortedWith(compareBy<GraphNode> { it.kind != NodeKind.NOTE }.thenByDescending { it.degree }.thenBy { it.key })
        labelText.clear()
        labels.clear()
        if (sameGeometry) { invalidate(); return }
        stopTimelapse()
        cancelTransition()
        stopAnimation()
        physics = null
        edges = newEdges
        original = layout
        neighbours = HashMap<String, MutableSet<String>>().also { map ->
            for (e in newEdges) {
                map.getOrPut(e.from) { HashSet() }.add(e.to)
                map.getOrPut(e.to) { HashSet() }.add(e.from)
            }
        }
        if (highlighted?.let { h -> newNodes.none { it.key == h.key } } == true) highlighted = null
        val targetCamera = entryCamera()
        animateLayout(previous, if (previous.isEmpty()) targetCamera else previousCamera, targetCamera)
    }

    private fun entryCamera(): Camera {
        val fit = fittedCamera(original.values)
        val linked = original.filterKeys { !neighbours[it].isNullOrEmpty() }
        val linkedFit = fittedCamera(linked.values)
        // A large collection of orphans must not make the actual links illegible on entry.
        return if (linked.size >= 2 && linkedFit.scale > fit.scale * 1.8f) linkedFit else fit
    }

    private data class Camera(val scale: Float, val x: Float, val y: Float)

    private fun fittedCamera(points: Collection<PointF2>): Camera {
        val left = points.minOfOrNull { it.x } ?: 0f
        val right = points.maxOfOrNull { it.x } ?: 0f
        val top = points.minOfOrNull { it.y } ?: 0f
        val bottom = points.maxOfOrNull { it.y } ?: 0f
        val margin = 68 * density
        val fit = min((width - margin) / (right - left).coerceAtLeast(1f),
            (height - margin) / (bottom - top).coerceAtLeast(1f)).coerceIn(0.005f * density, 2f * density)
        return Camera(fit, width / 2f - (left + right) * fit / 2, height / 2f - (top + bottom) * fit / 2)
    }

    private fun animateLayout(previous: Map<String, PointF2>, from: Camera, to: Camera) {
        positions.clear()
        if (original.isEmpty() || !isAttachedToWindow || width <= 0 || height <= 0 ||
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) {
            positions.putAll(original)
            scale = to.scale; dx = to.x; dy = to.y
            invalidate()
            return
        }
        val cx = (width / 2f - to.x) / to.scale
        val cy = (height / 2f - to.y) / to.scale
        val start = original.mapValues { (key, end) ->
            previous[key] ?: neighbours[key].orEmpty().firstNotNullOfOrNull { previous[it] }
                ?: PointF2(cx + (end.x - cx) * 0.52f, cy + (end.y - cy) * 0.52f)
        }
        fun at(t: Float) {
            for ((key, end) in original) {
                val p = start.getValue(key)
                positions[key] = PointF2(p.x + (end.x - p.x) * t, p.y + (end.y - p.y) * t)
            }
            scale = from.scale + (to.scale - from.scale) * t
            dx = from.x + (to.x - from.x) * t
            dy = from.y + (to.y - from.y) * t
            invalidate()
        }
        at(0f)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { at(it.animatedValue as Float) }
            doOnEnd { if (transition === it) transition = null }
        }
        transition = animator
        animator.start()
    }

    private fun cancelTransition() {
        transition?.cancel()
        transition = null
    }

    private fun finishTransition() {
        transition?.let { if (it.isRunning) it.end() }
        transition = null
    }

    fun recenter() {
        stopTimelapse()
        val previous = positions.toMap()
        cancelTransition()
        stopAnimation()
        physics = null
        animateLayout(previous, Camera(scale, dx, dy), fittedCamera(original.values))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        labelText.clear()
        labels.clear()
        if (oldw == 0 || oldh == 0) {
            cancelTransition()
            val camera = entryCamera()
            animateLayout(emptyMap(), camera, camera)
        }
    }

    private fun radius(node: GraphNode): Float {
        val size = if (node.kind == NodeKind.ATTACHMENT) 2.5f
            else (4f + 1.8f * sqrt(node.degree.toFloat())).coerceAtMost(18f)
        return size * density * sqrt(scale / density).coerceIn(0.55f, 1.3f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.pathEffect = null
        val focus = (pressedNode ?: highlighted)?.key
        val lit = highlightedKeys
        paint.color = outline
        paint.alpha = if (focus == null) 170 else 45
        paint.strokeWidth = 1.5f * density
        for (edge in edges) {
            if (edge.from == focus || edge.to == focus) continue
            if (shown?.let { edge.from !in it || edge.to !in it } == true) continue
            val a = positions[edge.from] ?: continue
            val b = positions[edge.to] ?: continue
            canvas.drawLine(dx + a.x * scale, dy + a.y * scale, dx + b.x * scale, dy + b.y * scale, paint)
        }
        if (focus != null) {
            paint.color = primary
            paint.strokeWidth = 2.5f * density
            for (edge in edges) {
                if (edge.from != focus && edge.to != focus) continue
                if (shown?.let { edge.from !in it || edge.to !in it } == true) continue
                val a = positions[edge.from] ?: continue
                val b = positions[edge.to] ?: continue
                canvas.drawLine(dx + a.x * scale, dy + a.y * scale, dx + b.x * scale, dy + b.y * scale, paint)
            }
        }
        labels.clear()
        occupiedLabels.setEmpty()
        val replay = schedule
        val elapsed = if (replay != null) clock() - startedAt else 0L
        for (node in nodes) {
            if (shown?.let { node.key !in it } == true) continue
            val p = positions[node.key] ?: continue
            val x = dx + p.x * scale
            val y = dy + p.y * scale
            val reveal = if (replay == null) 1f else
                ((elapsed - (replay.appearMs[node.key] ?: 0L)) / 200f).coerceIn(0f, 1f)
            val r = radius(node) * (0.5f + 0.5f * reveal)
            if (x + r < 0 || x - r > width || y + r < 0 || y - r > height) continue
            val matches = (focus == null || node.key in lit) &&
                (focus != null || node.displayTitle(context).contains(query, ignoreCase = true))
            val ghost = node.kind == NodeKind.MISSING
            val file = node.kind == NodeKind.ATTACHMENT
            paint.style = if (ghost || file) Paint.Style.STROKE else Paint.Style.FILL
            paint.color = when (node.kind) {
                NodeKind.MISSING -> outline
                NodeKind.TAG -> tertiary
                NodeKind.ATTACHMENT -> muted
                NodeKind.NOTE -> primary
            }
            paint.alpha = ((if (matches) 255 else 64) * reveal).roundToInt()
            paint.pathEffect = if (ghost) dashed else null
            paint.strokeWidth = 1.5f * density
            when (node.kind) {
                NodeKind.TAG -> {
                    square.set(x - r, y - r, x + r, y + r)
                    canvas.drawRoundRect(square, r * 0.3f, r * 0.3f, paint)
                }
                NodeKind.ATTACHMENT -> {
                    canvas.drawRect(x - r, y - r, x + r, y + r, paint)
                }
                else -> canvas.drawCircle(x, y, r, paint)
            }
            paint.style = Paint.Style.FILL
            paint.pathEffect = null
            square.set(x - r - density, y - r - density, x + r + density, y + r + density)
            square.roundOut(candidate)
            occupiedLabels.op(candidate, Region.Op.UNION)
        }
        fun label(node: GraphNode, priority: Boolean) {
            if (shown?.contains(node.key) == false) return
            if (!priority && focus != null && node.key !in lit) return
            val p = positions[node.key] ?: return
            val x = dx + p.x * scale
            val y = dy + p.y * scale
            if (x !in 0f..width.toFloat() || y !in 0f..height.toFloat()) return
            if (focus == null && query.isNotEmpty() && !node.displayTitle(context).contains(query, true)) return
            val fade = if (priority || query.isNotEmpty() || node.degree > 1 || nodes.size < 16) 1f
                else (scale / density * 1.9f).coerceIn(0.35f, 1f)
            val reveal = if (replay == null) 1f else
                ((elapsed - (replay.appearMs[node.key] ?: 0L)) / 200f).coerceIn(0f, 1f)
            caption.alpha = (255 * fade * reveal).roundToInt()
            halo.alpha = caption.alpha
            drawLabel(canvas, node, x, y, radius(node), priority)
        }
        (pressedNode ?: highlighted)?.let { label(it, true) }
        for (node in labelOrder) if (node.key != focus) label(node, false)
    }

    private fun drawLabel(canvas: Canvas, node: GraphNode, x: Float, y: Float, radius: Float, priority: Boolean) {
        val gap = radius + 4 * density
        val text = labelText.getOrPut(node) {
            val maxWidth = min(width * 0.58f, 210 * density)
            val title = TextUtils.ellipsize(node.displayTitle(context), caption, maxWidth, TextUtils.TruncateAt.END).toString()
            LabelText(title, caption.measureText(title))
        }
        val title = text.title
        val w = text.width
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
            candidate.inset(-density.roundToInt(), -density.roundToInt())
            if (occupiedLabels.quickReject(candidate)) return true
            overlap.set(candidate)
            return !overlap.op(occupiedLabels, Region.Op.INTERSECT)
        }
        if (!place(near, middle) && !place(far, middle) && !place(x - w / 2, y - gap - h)
            && !place(x - w / 2, y + gap) && !place(near, y - gap - h) && !place(far, y + gap)) {
            // On a dense cluster the active title gets the first free spot along the top.
            if (!priority || !place((width - w) / 2f, 4 * density)) return
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
            if (shown?.contains(node.key) == false) continue
            val p = positions[node.key] ?: continue
            val d = hypot(x - dx - p.x * scale, y - dy - p.y * scale)
            if (d < distance) { distance = d; nearest = node }
        }
        if (nearest != null && distance <= radius(nearest))
            return nearest
        return labels.entries.lastOrNull { it.value.contains(x, y) }?.key ?: nearest
    }

    override fun performLongClick(): Boolean {
        val node = pressedNode ?: return super.performLongClick()
        held = true
        onNodeLongPress(node)
        return true
    }

    private fun simulationFor(key: String): GraphPhysics? {
        if (positions.size < 2) return null
        if (positions.size <= GraphPhysics.MAX_LIVE_NODES && physics != null) return physics
        // Bound live spring work on large vaults to the dragged note and its closest graph neighbours.
        val keys = if (positions.size <= GraphPhysics.MAX_LIVE_NODES) positions.keys.toSet() else {
            val local = linkedSetOf(key)
            var layer = listOf(key)
            while (layer.isNotEmpty() && local.size < GraphPhysics.MAX_LIVE_NODES) {
                val next = mutableListOf<String>()
                for (source in layer) for (neighbour in neighbours[source].orEmpty()) {
                    if (local.size == GraphPhysics.MAX_LIVE_NODES) break
                    if (local.add(neighbour)) next.add(neighbour)
                }
                layer = next
            }
            local
        }
        simulatedKeys = keys
        physics = GraphPhysics(keys.associateWith { positions.getValue(it) }, edges)
        return physics
    }

    private fun runPhysics() {
        if (animating) return
        animating = true
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun stopAnimation() {
        if (animating) Choreographer.getInstance().removeFrameCallback(frame)
        animating = false
        physics?.stop()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hold)
        cancelTransition()
        stopTimelapse()
        stopAnimation()
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
                transition?.pause()
                dragSimulation = null
                // Hit-test the same positions and captions that were visible when the finger landed.
                val touched = hit(event.x, event.y)
                stopTimelapse()
                lastX = event.x; lastY = event.y
                pressX = lastX; pressY = lastY
                pinched = false; dragged = false; held = false
                pressedNode = touched
                if (pressedNode != null) {
                    postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                    invalidate()
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(hold)
                pinched = true
                cancelTransition()
                if (pressedNode != null) physics?.release()
                pressedNode = null
                invalidate()
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
                    cancelTransition()
                    removeCallbacks(hold)
                    val key = pressedNode?.key
                    val pos = positions[key]
                    val sx = event.x - lastX
                    val sy = event.y - lastY
                    if (pos != null && key != null) {
                        val moved = PointF2(pos.x + sx / scale, pos.y + sy / scale)
                        val sim = dragSimulation ?: simulationFor(key).also { dragSimulation = it }
                        positions[key] = moved
                        sim?.let { it.drag(key, moved); runPhysics() }
                    } else { dx += sx; dy += sy }
                    lastX = event.x; lastY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                if (event.actionMasked == MotionEvent.ACTION_UP && !dragged && !pinched && !held) performClick()
                if (pressedNode != null && physics?.isActive == true) {
                    physics?.release()
                    runPhysics()
                }
                pressedNode = null
                transition?.resume()
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
