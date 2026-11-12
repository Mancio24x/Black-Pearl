package org.qosp.notes.components.links

import java.util.Random
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Plain 2D point (no android.graphics so the layout is JVM-testable). */
data class PointF2(val x: Float, val y: Float)

internal fun gridCellKey(cx: Long, cy: Long): Long = (cx shl 32) xor (cy and 0xffffffffL)

/** Points sorted by grid cell, so the points of one cell are found by binary search without per-frame maps. */
internal class CloseGrid(x: DoubleArray, y: DoubleArray, radius: Double) {
    val keys: LongArray
    val order: IntArray
    val cx: LongArray
    val cy: LongArray

    init {
        val n = x.size
        cx = LongArray(n) { Math.floor(x[it] / radius).toLong() }
        cy = LongArray(n) { Math.floor(y[it] / radius).toLong() }
        val cell = LongArray(n) { gridCellKey(cx[it], cy[it]) }
        order = (0 until n).sortedBy { cell[it] }.toIntArray()
        keys = LongArray(n) { cell[order[it]] }
    }

    /** First position in [order] whose cell key is >= [key]. */
    fun lowerBound(key: Long): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }
}

/**
 * Calls [action] once for every pair of points that may be closer than [radius] (same or neighbouring grid cell;
 * callers check the distance): the work grows with the number of points, not with its square, so thousands of notes
 * stay fast.
 */
internal inline fun forEachClosePair(x: DoubleArray, y: DoubleArray, radius: Double, action: (Int, Int) -> Unit) {
    val grid = CloseGrid(x, y, radius)
    val n = x.size
    for (i in 0 until n) {
        for (ox in -1L..1L) for (oy in -1L..1L) {
            val key = gridCellKey(grid.cx[i] + ox, grid.cy[i] + oy)
            var at = grid.lowerBound(key)
            while (at < n && grid.keys[at] == key) {
                val j = grid.order[at++]
                if (j > i) action(i, j)
            }
        }
    }
}

/**
 * Force-directed layout, deterministic for a given [seed]. Linked notes pull together; notes push away only from
 * notes nearby ([CUTOFF]); a steady pull towards the centre keeps each linked group one round cluster, like
 * Obsidian's graph, instead of a spread of long spokes.
 */
object GraphLayout {
    internal const val K = 40.0
    private const val MIN_DIST = 0.01
    private const val MIN_SEPARATION = 10.0

    /** Notes farther apart than this do not push each other. */
    internal const val CUTOFF = K * 3

    /** Constant pull of every note towards the centre of its group. */
    internal const val PULL = 30.0

    /** Iterations that keep the layout around a second on a phone for large vaults. */
    fun iterationsFor(nodeCount: Int): Int = (400_000 / nodeCount.coerceAtLeast(1)).coerceIn(80, 300)

    fun layout(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        iterations: Int = 300,
        seed: Long = 42,
    ): Map<String, PointF2> {
        val keys = nodes.map { it.key }.distinct()
        if (keys.isEmpty()) return emptyMap()
        val neighbours = keys.associateWith { ArrayList<String>() }
        val links = edges.filter { it.from != it.to && it.from in neighbours && it.to in neighbours }
        for (link in links) {
            neighbours.getValue(link.from).add(link.to)
            neighbours.getValue(link.to).add(link.from)
        }
        val groups = mutableListOf<List<String>>()
        val groupByKey = HashMap<String, Int>()
        val isolated = mutableListOf<String>()
        for (key in keys) {
            if (neighbours.getValue(key).isEmpty()) { isolated.add(key); continue }
            if (key in groupByKey) continue
            val group = arrayListOf(key)
            groupByKey[key] = groups.size
            var cursor = 0
            while (cursor < group.size) {
                for (next in neighbours.getValue(group[cursor++])) {
                    if (groupByKey.putIfAbsent(next, groups.size) == null) group.add(next)
                }
            }
            groups.add(group)
        }
        val byGroup = links.groupBy { groupByKey.getValue(it.from) }
        val islands = groups.mapIndexed { i, group ->
            layoutConnected(group, byGroup[i].orEmpty(), iterations, seed)
        }.sortedByDescending { it.size }
        val points = packIslands(islands)

        // Orphans have no springs. Put them near the network without letting their repulsion
        // squeeze an entire linked component into a tiny corner of the fitted viewport.
        if (isolated.isNotEmpty()) {
            val columns = ceil(sqrt(isolated.size.toDouble())).toInt()
            val left = points.values.minOfOrNull { it.x } ?: 0f
            val right = points.values.maxOfOrNull { it.x } ?: 0f
            val bottom = points.values.maxOfOrNull { it.y } ?: 0f
            val gap = ((right - left) / (columns - 1).coerceAtLeast(1)).coerceIn(60f, 100f)
            val top = if (points.isEmpty()) 0f else bottom + 48f
            isolated.forEachIndexed { i, key ->
                val onRow = minOf(columns, isolated.size - (i / columns) * columns)
                val x = (left + right) / 2 + (i % columns - (onRow - 1) / 2f) * gap
                points[key] = PointF2(x, top + (i / columns) * 42f)
            }
        }
        return points
    }

    private data class Bounds(val left: Float, val top: Float, val width: Float, val height: Float)

    private fun packIslands(islands: List<Map<String, PointF2>>): MutableMap<String, PointF2> {
        if (islands.size <= 1) return islands.firstOrNull()?.toMutableMap() ?: mutableMapOf()
        val gap = 48f
        val boxes = islands.map { points ->
            val left = points.values.minOf { it.x }
            val top = points.values.minOf { it.y }
            Bounds(left, top, points.values.maxOf { it.x } - left + gap, points.values.maxOf { it.y } - top + gap)
        }
        val rowWidth = max(boxes.maxOf { it.width }, sqrt(boxes.sumOf { (it.width * it.height).toDouble() }).toFloat())
        val points = LinkedHashMap<String, PointF2>()
        var x = 0f
        var y = 0f
        var rowHeight = 0f
        for ((i, island) in islands.withIndex()) {
            val (left, top, width, height) = boxes[i]
            if (x > 0f && x + width > rowWidth) { y += rowHeight; x = 0f; rowHeight = 0f }
            for ((key, p) in island) points[key] = PointF2(x + p.x - left, y + p.y - top)
            rowHeight = max(rowHeight, height)
            x += width
        }
        return points
    }

    private fun layoutConnected(
        keys: List<String>, edges: List<GraphEdge>, iterations: Int, seed: Long,
    ): Map<String, PointF2> {
        val n = keys.size
        if (n == 0) return emptyMap()
        if (n == 1) return mapOf(keys[0] to PointF2(0f, 0f))

        val indexOf = HashMap<String, Int>()
        keys.forEachIndexed { i, k -> indexOf[k] = i }
        val pairs = edges.mapNotNull { e ->
            val a = indexOf[e.from]
            val b = indexOf[e.to]
            if (a == null || b == null || a == b) null else if (a < b) a to b else b to a
        }.distinct()
        val adjacency = Array(n) { ArrayList<Int>() }
        for ((a, b) in pairs) {
            adjacency[a].add(b)
            adjacency[b].add(a)
        }

        // Start breadth-first from the most linked note, on a sunflower spiral: linked notes begin close together
        // and the group is already round, so the forces only tidy it up.
        val order = IntArray(n)
        val seen = BooleanArray(n)
        var filled = 0
        val first = (0 until n).maxByOrNull { adjacency[it].size } ?: 0
        order[filled++] = first
        seen[first] = true
        var head = 0
        while (head < filled) {
            for (next in adjacency[order[head++]]) if (!seen[next]) {
                seen[next] = true
                order[filled++] = next
            }
        }
        val rnd = Random(seed)
        val x = DoubleArray(n)
        val y = DoubleArray(n)
        val golden = PI * (3 - sqrt(5.0))
        for (k in 0 until n) {
            val i = order[k]
            val r = K * 0.6 * sqrt(k + 0.5)
            x[i] = r * cos(k * golden) + rnd.nextDouble() - 0.5
            y[i] = r * sin(k * golden) + rnd.nextDouble() - 0.5
        }

        val dx = DoubleArray(n)
        val dy = DoubleArray(n)
        val t0 = K * 2
        val steps = max(iterations, 1)

        for (iter in 0 until steps) {
            val t = t0 * (1.0 - iter.toDouble() / steps) + 0.5
            dx.fill(0.0)
            dy.fill(0.0)
            forEachClosePair(x, y, CUTOFF) { i, j ->
                var vx = x[i] - x[j]
                var vy = y[i] - y[j]
                var d = sqrt(vx * vx + vy * vy)
                if (d < CUTOFF) {
                    if (d < MIN_DIST) {
                        // coincident: separate along a deterministic direction
                        val a = 2 * PI * (i * 31 + j) / 97.0
                        vx = cos(a)
                        vy = sin(a)
                        d = MIN_DIST
                    }
                    val f = K * K / d * (1 - d / CUTOFF)
                    val fx = vx / d * f
                    val fy = vy / d * f
                    dx[i] += fx
                    dy[i] += fy
                    dx[j] -= fx
                    dy[j] -= fy
                }
            }
            for ((a, b) in pairs) {
                val vx = x[a] - x[b]
                val vy = y[a] - y[b]
                val d = max(sqrt(vx * vx + vy * vy), MIN_DIST)
                val f = d * d / K
                val fx = vx / d * f
                val fy = vy / d * f
                dx[a] -= fx
                dy[a] -= fy
                dx[b] += fx
                dy[b] += fy
            }
            for (i in 0 until n) {
                val r = max(sqrt(x[i] * x[i] + y[i] * y[i]), MIN_DIST)
                dx[i] -= x[i] / r * PULL
                dy[i] -= y[i] / r * PULL
                val d = max(sqrt(dx[i] * dx[i] + dy[i] * dy[i]), MIN_DIST)
                val step = min(d, t)
                x[i] += dx[i] / d * step
                y[i] += dy[i] / d * step
            }
        }

        separate(x, y)

        val cx = x.average()
        val cy = y.average()
        return keys.indices.associate { i -> keys[i] to PointF2((x[i] - cx).toFloat(), (y[i] - cy).toFloat()) }
    }

    /** Final relaxation so no two nodes overlap: pushes too-close pairs apart, stops when none is left. */
    private fun separate(x: DoubleArray, y: DoubleArray) {
        repeat(60) {
            var moved = false
            forEachClosePair(x, y, MIN_SEPARATION) { i, j ->
                var vx = x[i] - x[j]
                var vy = y[i] - y[j]
                var d = sqrt(vx * vx + vy * vy)
                if (d < MIN_SEPARATION) {
                    if (d < MIN_DIST) {
                        val a = 2 * PI * (i * 31 + j) / 97.0
                        vx = cos(a)
                        vy = sin(a)
                        d = 1.0
                    }
                    val push = (MIN_SEPARATION - d) / 2.0 + 0.1
                    x[i] += vx / d * push
                    y[i] += vy / d * push
                    x[j] -= vx / d * push
                    y[j] -= vy / d * push
                    moved = true
                }
            }
            if (!moved) return
        }
    }
}
