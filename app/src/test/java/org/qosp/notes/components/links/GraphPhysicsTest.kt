package org.qosp.notes.components.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class GraphPhysicsTest {
    private val chain = (0..3).map { GraphNode("c$it", "C$it", it.toLong(), 2) } + GraphNode("z", "Z", 9, 0)
    private val chainEdges = (0..2).map { GraphEdge("c$it", "c${it + 1}") }
    private val start = GraphLayout.layout(chain, chainEdges)

    private fun moved(p: GraphPhysics, key: String): Double {
        val a = start.getValue(key)
        val b = p.position(key)!!
        return hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
    }

    private fun dragBy(p: GraphPhysics, key: String, frames: Int = 30) {
        val s = start.getValue(key)
        for (f in 1..frames) {
            p.drag(key, PointF2(s.x + 6f * f.coerceAtMost(20), s.y + 6f * f.coerceAtMost(20)))
            p.step()
        }
    }

    @Test
    fun restingGraphDoesNotMove() {
        val p = GraphPhysics(start, chainEdges)
        assertFalse(p.step())
        assertFalse(p.isActive)
        for (key in start.keys) assertEquals(0.0, moved(p, key), 1e-4)
    }

    @Test
    fun heldNodeStaysUnderTheFinger() {
        val p = GraphPhysics(start, chainEdges)
        dragBy(p, "c0")
        val s = start.getValue("c0")
        assertEquals(PointF2(s.x + 120f, s.y + 120f), p.position("c0"))
    }

    @Test
    fun linkedNotesFollowAndOthersBarelyMove() {
        val p = GraphPhysics(start, chainEdges)
        dragBy(p, "c0")
        val near = moved(p, "c1")
        val far = moved(p, "c3")
        val stranger = moved(p, "z")
        assertTrue("neighbour moved $near", near > 40)
        assertTrue("neighbour $near, two links away $far", near > far)
        assertTrue("neighbour $near, unlinked $stranger", near > 10 * stranger)
    }

    @Test
    fun settlesAfterRelease() {
        val p = GraphPhysics(start, chainEdges)
        dragBy(p, "c0")
        p.release()
        var frames = 0
        while (p.step()) frames++
        assertTrue("took $frames frames", frames < 300)
        assertFalse(p.isActive)
        for (key in start.keys) {
            val pos = p.position(key)!!
            assertTrue(pos.x.isFinite() && pos.y.isFinite())
        }
    }

    @Test
    fun unknownNodeAndSingleNodeAreIgnored() {
        val p = GraphPhysics(start, chainEdges)
        p.drag("missing", PointF2(1f, 1f))
        assertFalse(p.step())
        val single = GraphPhysics(mapOf("a" to PointF2(0f, 0f)), emptyList())
        single.drag("a", PointF2(10f, 0f))
        assertFalse(single.step())
    }
}
