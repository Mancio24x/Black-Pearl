package org.qosp.notes.components.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.data.model.Note
import java.io.File
import kotlin.math.hypot

class GraphLayoutTest {

    private fun demoGraph(): Pair<List<GraphNode>, List<GraphEdge>> {
        val text = File("../docs/superpowers/specs/linked-notes/The Bible.md").readText()
        val notes = listOf(
            Note(id = 1, title = "The Bible", content = text),
            Note(id = 2, title = "Genesis", content = "[[The Bible]]"),
        )
        return LinkGraph.build(LinkIndex(notes))
    }

    @Test
    fun deterministic() {
        val (nodes, edges) = demoGraph()
        assertEquals(GraphLayout.layout(nodes, edges), GraphLayout.layout(nodes, edges))
    }

    @Test
    fun noNaN() {
        val (nodes, edges) = demoGraph()
        val pos = GraphLayout.layout(nodes, edges)
        assertEquals(nodes.size, pos.size)
        assertTrue(pos.values.none { it.x.isNaN() || it.y.isNaN() || it.x.isInfinite() || it.y.isInfinite() })
    }

    @Test
    fun nodesSeparated() {
        val (nodes, edges) = demoGraph()
        val pts = GraphLayout.layout(nodes, edges).values.toList()
        for (i in pts.indices) for (j in i + 1 until pts.size) {
            val d = hypot((pts[i].x - pts[j].x).toDouble(), (pts[i].y - pts[j].y).toDouble())
            assertTrue("pair $i,$j only $d apart", d >= 8.0)
        }
    }

    @Test
    fun hubNearCenter() {
        val (nodes, edges) = demoGraph()
        val hub = GraphLayout.layout(nodes, edges).getValue("the bible")
        val d = hypot(hub.x.toDouble(), hub.y.toDouble())
        assertTrue("hub at $d", d < 40.0)
    }

    @Test
    fun emptyAndSingle() {
        assertTrue(GraphLayout.layout(emptyList(), emptyList()).isEmpty())
        val one = GraphLayout.layout(listOf(GraphNode("a", "A", 1, 0)), emptyList())
        assertFalse(one.isEmpty())
    }

    @Test
    fun layoutOfThreeHundredNodesFinishes() {
        val nodes = (0 until 300).map { GraphNode("n$it", "N$it", it.toLong(), 2) }
        val edges = (0 until 300).flatMap { listOf(GraphEdge("n$it", "n${(it + 1) % 300}"), GraphEdge("n$it", "n${(it * 7) % 300}")) }
        val start = System.nanoTime()
        val pos = GraphLayout.layout(nodes, edges)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(300, pos.size)
        assertTrue("took $ms ms", ms < 1500)
    }
}
