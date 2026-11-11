package org.qosp.notes.components.links

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Plain 2D point (no android.graphics so the layout is JVM-testable). */
data class PointF2(val x: Float, val y: Float)

/** Fruchterman-Reingold force-directed layout, deterministic for a given [seed]. */
object GraphLayout {
    private const val K = 40.0
    private const val MIN_DIST = 0.01
    private const val MIN_SEPARATION = 10.0
    private const val GRAVITY = 0.05

    fun layout(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        iterations: Int = 300,
        seed: Long = 42,
    ): Map<String, PointF2> {
        val keys = nodes.map { it.key }.distinct()
        val n = keys.size
        if (n == 0) return emptyMap()
        if (n == 1) return mapOf(keys[0] to PointF2(0f, 0f))

        val indexOf = HashMap<String, Int>()
        keys.forEachIndexed { i, k -> indexOf[k] = i }
        val pairs = edges.mapNotNull { e ->
            val a = indexOf[e.from]
            val b = indexOf[e.to]
            if (a == null || b == null || a == b) null else a to b
        }

        val rnd = Random(seed)
        val radius = K * sqrt(n.toDouble()) / 2.0
        val x = DoubleArray(n)
        val y = DoubleArray(n)
        for (i in 0 until n) {
            val angle = 2 * PI * i / n
            val r = radius * (0.8 + 0.4 * rnd.nextDouble())
            x[i] = r * cos(angle)
            y[i] = r * sin(angle)
        }

        val dx = DoubleArray(n)
        val dy = DoubleArray(n)
        val t0 = max(K, K * sqrt(n.toDouble()) / 4.0)
        val steps = max(iterations, 1)

        for (iter in 0 until steps) {
            val t = t0 * (1.0 - iter.toDouble() / steps)
            dx.fill(0.0)
            dy.fill(0.0)
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var vx = x[i] - x[j]
                    var vy = y[i] - y[j]
                    var d = sqrt(vx * vx + vy * vy)
                    if (d < MIN_DIST) {
                        // coincident: separate along a deterministic direction
                        val a = 2 * PI * (i * 31 + j) / 97.0
                        vx = cos(a)
                        vy = sin(a)
                        d = MIN_DIST
                    }
                    val f = K * K / d
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
                dx[i] -= x[i] * GRAVITY
                dy[i] -= y[i] * GRAVITY
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
        val n = x.size
        repeat(60) {
            var moved = false
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var vx = x[i] - x[j]
                    var vy = y[i] - y[j]
                    var d = sqrt(vx * vx + vy * vy)
                    if (d >= MIN_SEPARATION) continue
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
