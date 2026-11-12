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
        val hub = GraphLayout.layout(nodes, edges).getValue(nodes.single { it.title == "The Bible" }.key)
        val d = hypot(hub.x.toDouble(), hub.y.toDouble())
        assertTrue("hub at $d", d < 40.0)
    }

    @Test
    fun addingIsolatedNotesKeepsTheLinkedComponentAtItsOriginalSize() {
        val (nodes, links) = demoGraph()
        val linked = GraphLayout.layout(nodes, links)
        val orphans = (1..240).map { GraphNode("orphan:$it", "Note $it", it.toLong(), 0) }
        val mixed = GraphLayout.layout(nodes + orphans, links)
        assertEquals(linked, mixed.filterKeys { it in linked })
        assertEquals(orphans.size, orphans.map { mixed.getValue(it.key) }.distinct().size)
        assertTrue(orphans.all { mixed.getValue(it.key).y > linked.values.maxOf { p -> p.y } })
    }

    @Test
    fun disconnectedPairsKeepTheSameInternalSpacing() {
        val nodes = (1..40).map { GraphNode("n$it", "N$it", it.toLong(), 1) }
        val links = nodes.chunked(2).map { GraphEdge(it[0].key, it[1].key) }
        val single = GraphLayout.layout(nodes.take(2), links.take(1))
        val packed = GraphLayout.layout(nodes, links)
        fun distance(a: PointF2, b: PointF2) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
        val expected = distance(single.getValue("n1"), single.getValue("n2"))
        for (edge in links) assertEquals(expected, distance(packed.getValue(edge.from), packed.getValue(edge.to)), 0.01)
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

    /** Shaped like the Bible Study Kit: one hub, 66 books, ~20 chapters each, chapters linked in a row. */
    private fun kitLikeGraph(): Pair<List<GraphNode>, List<GraphEdge>> {
        val nodes = ArrayList<GraphNode>()
        val edges = ArrayList<GraphEdge>()
        nodes.add(GraphNode("hub", "Bible", 0, 66))
        var id = 1L
        for (b in 1..66) {
            nodes.add(GraphNode("b$b", "Book $b", id++, 21))
            edges.add(GraphEdge("hub", "b$b"))
            for (c in 1..19) {
                nodes.add(GraphNode("b$b-$c", "Book $b-$c", id++, 2))
                edges.add(GraphEdge("b$b", "b$b-$c"))
                if (c > 1) edges.add(GraphEdge("b$b-${c - 1}", "b$b-$c"))
            }
        }
        return nodes to edges
    }

    @Test
    fun aKitSizedGraphIsOneCompactRoundGroupAndFast() {
        val (nodes, edges) = kitLikeGraph()
        val started = System.currentTimeMillis()
        val pos = GraphLayout.layout(nodes, edges, GraphLayout.iterationsFor(nodes.size))
        val ms = System.currentTimeMillis() - started
        assertEquals(nodes.size, pos.size)
        assertTrue("took $ms ms", ms < 5000)
        val cx = pos.values.map { it.x }.average()
        val cy = pos.values.map { it.y }.average()
        val radii = pos.values.map { hypot(it.x - cx, it.y - cy) }.sorted()
        val r90 = radii[radii.size * 9 / 10]
        // the old layout left the kit at ~0.75 * K * sqrt(n): long spokes and scattered groups
        assertTrue("r90 $r90", r90 < 0.45 * 40 * Math.sqrt(nodes.size.toDouble()))
        // the hub stays in the middle
        val hub = pos.getValue("hub")
        assertTrue("hub at ${hypot(hub.x - cx, hub.y - cy)}", hypot(hub.x - cx, hub.y - cy) < r90 / 3)
    }
}
