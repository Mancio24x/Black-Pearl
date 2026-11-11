package org.qosp.notes.components.links

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Live version of [GraphLayout]'s forces for the Network screen, one [step] per frame.
 *
 * The starting positions are the resting state: the force each node still feels there is measured once and
 * subtracted, so nothing moves until a node is dragged. While a node is held, its neighbours follow it through
 * their links and the others make room; after [release] the movement fades out and stops by itself.
 */
class GraphPhysics(start: Map<String, PointF2>, edges: List<GraphEdge>) {
    private val keys = start.keys.toList()
    private val n = keys.size
    private val indexOf: Map<String, Int> = keys.withIndex().associate { it.value to it.index }
    private val x = DoubleArray(n) { start.getValue(keys[it]).x.toDouble() }
    private val y = DoubleArray(n) { start.getValue(keys[it]).y.toDouble() }
    private val vx = DoubleArray(n)
    private val vy = DoubleArray(n)
    private val fx = DoubleArray(n)
    private val fy = DoubleArray(n)
    private val restX = DoubleArray(n)
    private val restY = DoubleArray(n)
    private val springs: List<Pair<Int, Int>> = edges.mapNotNull { e ->
        val a = indexOf[e.from]
        val b = indexOf[e.to]
        if (a == null || b == null || a == b) null else if (a < b) a to b else b to a
    }.distinct()
    private var pinned = -1
    private var heat = 0.0

    init {
        forces(restX, restY)
    }

    /** True while a node is held or the graph is still settling. */
    val isActive: Boolean get() = pinned >= 0 || heat > 0.0

    fun position(key: String): PointF2? = indexOf[key]?.let { PointF2(x[it].toFloat(), y[it].toFloat()) }

    /** Holds the node [key] at [to] (layout coordinates) and wakes the simulation. */
    fun drag(key: String, to: PointF2) {
        val i = indexOf[key] ?: return
        pinned = i
        x[i] = to.x.toDouble()
        y[i] = to.y.toDouble()
        vx[i] = 0.0
        vy[i] = 0.0
        heat = DRAG_HEAT
    }

    /** Lets go of the held node; the graph keeps moving a little and then stops. */
    fun release() {
        pinned = -1
    }

    fun stop() {
        pinned = -1
        heat = 0.0
        vx.fill(0.0)
        vy.fill(0.0)
    }

    /** Advances one frame. Returns false when nothing moves any more (call [drag] to wake it again). */
    fun step(): Boolean {
        if (heat <= 0.0 || n < 2) return false
        forces(fx, fy)
        val gain = heat * GAIN
        var fastest = 0.0
        for (i in 0 until n) {
            if (i == pinned) continue
            var ux = (vx[i] + (fx[i] - restX[i]) * gain) * FRICTION
            var uy = (vy[i] + (fy[i] - restY[i]) * gain) * FRICTION
            val speed = sqrt(ux * ux + uy * uy)
            if (speed > MAX_STEP) {
                ux *= MAX_STEP / speed
                uy *= MAX_STEP / speed
            }
            vx[i] = ux
            vy[i] = uy
            x[i] += ux
            y[i] += uy
            fastest = max(fastest, minOf(speed, MAX_STEP))
        }
        if (pinned < 0) heat *= COOLING
        if (fastest < REST_SPEED || (pinned < 0 && heat < MIN_HEAT)) {
            vx.fill(0.0)
            vy.fill(0.0)
            if (pinned < 0) heat = 0.0
            return false
        }
        return true
    }

    /** Same forces as [GraphLayout]: every pair repels, links pull, a light pull towards the centre. */
    private fun forces(outX: DoubleArray, outY: DoubleArray) {
        outX.fill(0.0)
        outY.fill(0.0)
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val ux = x[i] - x[j]
                val uy = y[i] - y[j]
                val d = max(sqrt(ux * ux + uy * uy), MIN_DIST)
                val f = K * K / (d * d)
                outX[i] += ux * f
                outY[i] += uy * f
                outX[j] -= ux * f
                outY[j] -= uy * f
            }
        }
        for ((a, b) in springs) {
            val ux = x[a] - x[b]
            val uy = y[a] - y[b]
            val d = max(sqrt(ux * ux + uy * uy), MIN_DIST)
            val f = d / K
            outX[a] -= ux * f
            outY[a] -= uy * f
            outX[b] += ux * f
            outY[b] += uy * f
        }
        for (i in 0 until n) {
            outX[i] -= x[i] * GRAVITY
            outY[i] -= y[i] * GRAVITY
        }
    }

    companion object {
        /** Above this many nodes a frame would be too slow on older phones: only the dragged node moves. */
        const val MAX_LIVE_NODES = 300

        private const val K = 40.0
        private const val GRAVITY = 0.05
        private const val MIN_DIST = 1.0
        private const val GAIN = 0.04
        private const val FRICTION = 0.6
        private const val MAX_STEP = 12.0
        private const val DRAG_HEAT = 0.3
        private const val COOLING = 0.96
        private const val MIN_HEAT = 0.005
        private const val REST_SPEED = 0.02
    }
}
