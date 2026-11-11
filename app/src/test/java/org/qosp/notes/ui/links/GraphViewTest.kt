package org.qosp.notes.ui.links

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.R
import org.qosp.notes.components.links.GraphEdge
import org.qosp.notes.components.links.GraphNode
import org.qosp.notes.components.links.NodeKind
import org.qosp.notes.components.links.PointF2
import org.qosp.notes.data.model.Attachment
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraphViewTest {
    private val graph = GraphView(ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseAppTheme))
    private val node = GraphNode("a", "A", 1, 0)
    private var tapped: GraphNode? = null

    init {
        graph.layout(0, 0, 600, 600)
        graph.setGraph(listOf(node), emptyList(), mapOf("a" to PointF2(0f, 0f)))
        graph.onNodeTap = { tapped = it }
    }

    private fun touch(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(1, 10, action, x, y, 0)
        try { graph.onTouchEvent(event) } finally { event.recycle() }
    }

    private fun tap(x: Float, y: Float) {
        touch(MotionEvent.ACTION_DOWN, x, y)
        touch(MotionEvent.ACTION_UP, x, y)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "mdpi")
    @Test fun leafTitleOutsideTheDotOpensInALargeNetwork() {
        val leaf = GraphNode("leaf", "A long title for a leaf", 42, 1)
        val others = (1..16).map { GraphNode("n$it", "Other $it", it.toLong(), 1) }
        val points = others.mapIndexed { i, n -> n.key to PointF2(if (i % 2 == 0) -400f else 400f, 0f) }.toMap()
        graph.layout(0, 0, 1000, 600)
        graph.setGraph(others + leaf, emptyList(), points + (leaf.key to PointF2(0f, 0f)))
        graph.draw(Canvas(Bitmap.createBitmap(1000, 600, Bitmap.Config.ARGB_8888)))
        // The center leaf is at (500, 300). Tap its title outside the 24 dp node target.
        tap(540f, 300f)
        assertEquals(leaf, tapped)
    }

    @Test fun tapAfterDraggingNodeOpensItInItsNewPosition() {
        touch(MotionEvent.ACTION_DOWN, 300f, 300f)
        touch(MotionEvent.ACTION_MOVE, 470f, 300f)
        touch(MotionEvent.ACTION_UP, 470f, 300f)
        assertNull(tapped)
        tap(300f, 300f)
        assertNull(tapped)
        tap(470f, 300f)
        assertEquals(node, tapped)
    }

    @Test fun backgroundPanMovesGraphAndRecenterRestoresIt() {
        touch(MotionEvent.ACTION_DOWN, 20f, 20f)
        touch(MotionEvent.ACTION_MOVE, 120f, 120f)
        touch(MotionEvent.ACTION_UP, 120f, 120f)
        assertNull(tapped)
        tap(400f, 400f)
        assertEquals(node, tapped)
        tapped = null
        graph.recenter()
        tap(300f, 300f)
        assertEquals(node, tapped)
    }

    @Test fun resizeKeepsWhereTheGraphWasMoved() {
        touch(MotionEvent.ACTION_DOWN, 20f, 20f)
        touch(MotionEvent.ACTION_MOVE, 120f, 120f)
        touch(MotionEvent.ACTION_UP, 120f, 120f)
        graph.layout(0, 0, 600, 420) // the keyboard opens
        tap(400f, 400f)
        assertEquals(node, tapped)
    }

    // on screen a(380,300) is linked to b(460,300); c(140,300) has no link
    private val a = GraphNode("a", "A", 1, 1)
    private val b = GraphNode("b", "B", 2, 1)
    private val c = GraphNode("c", "C", 3, 0)

    private fun threeNodes() = graph.setGraph(listOf(a, b, c), listOf(GraphEdge("a", "b")),
        mapOf("a" to PointF2(0f, 0f), "b" to PointF2(40f, 0f), "c" to PointF2(-120f, 0f)))

    @Test fun pressingANodeHighlightsItAndItsLinkedNotes() {
        threeNodes()
        touch(MotionEvent.ACTION_DOWN, 380f, 300f)
        assertEquals(setOf("a", "b"), graph.highlightedKeys)
        touch(MotionEvent.ACTION_UP, 380f, 300f)
        assertEquals(emptySet<String>(), graph.highlightedKeys)
        graph.highlighted = c
        assertEquals(setOf("c"), graph.highlightedKeys)
    }

    @Test fun draggingANodePullsItsLinkedNote() {
        threeNodes()
        touch(MotionEvent.ACTION_DOWN, 380f, 300f)
        touch(MotionEvent.ACTION_MOVE, 380f, 360f)
        touch(MotionEvent.ACTION_MOVE, 380f, 420f)
        touch(MotionEvent.ACTION_UP, 380f, 420f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        val linked = graph.positionOf("b")!!.y
        val unlinked = abs(graph.positionOf("c")!!.y)
        assertTrue("linked note moved $linked, unlinked $unlinked", linked > 8f && linked > 3 * unlinked)
        assertEquals(null, tapped)
    }

    private val n1 = GraphNode("note:1", "One", 1, 1)
    private val n2 = GraphNode("note:2", "Two", 2, 2)
    private val n3 = GraphNode("note:3", "Three", 3, 2)
    private val tag = GraphNode("tag:x", "x", null, 1, NodeKind.TAG)
    private val created = mapOf("note:1" to 1L, "note:2" to 2L, "note:3" to 3L)

    private fun timelapseGraph() = graph.setGraph(listOf(n1, n2, n3, tag),
        listOf(GraphEdge("note:1", "note:2"), GraphEdge("note:2", "note:3"), GraphEdge("note:3", "tag:x")),
        mapOf("note:1" to PointF2(-60f, 0f), "note:2" to PointF2(0f, 0f), "note:3" to PointF2(60f, 0f), "tag:x" to PointF2(120f, 0f)))

    private var now = 0L

    /** Moves the fake timelapse clock and lets a few frames run so the view reads it. */
    private fun advance(ms: Long) {
        now += ms
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
    }

    @Test fun timelapseShowsNotesInCreationOrder() {
        timelapseGraph()
        graph.clock = { now }
        graph.playTimelapse(created)
        assertTrue(graph.isPlayingTimelapse)
        assertEquals(emptySet<String>(), graph.visibleKeys)
        advance(1600)
        assertEquals(setOf("note:1", "note:2"), graph.visibleKeys)
        advance(1900)
        assertFalse(graph.isPlayingTimelapse)
        assertNull(graph.visibleKeys)
    }

    @Test fun touchStopsTimelapse() {
        timelapseGraph()
        graph.clock = { now }
        graph.playTimelapse(created)
        assertTrue(graph.isPlayingTimelapse)
        touch(MotionEvent.ACTION_DOWN, 20f, 20f)
        assertNull(graph.visibleKeys)
        assertFalse(graph.isPlayingTimelapse)
    }

    @Test fun nodesAppearWithTheirFirstNote() {
        timelapseGraph()
        graph.clock = { now }
        graph.playTimelapse(created)
        advance(1600)
        assertFalse("tag:x" in graph.visibleKeys!!)
    }

    @Test fun tappingATagNodeDeliversKindTag() {
        val t = GraphNode("tag:x", "x", null, 1, NodeKind.TAG)
        graph.setGraph(listOf(t), emptyList(), mapOf(t.key to PointF2(0f, 0f)))
        tap(300f, 300f)
        assertEquals(NodeKind.TAG, tapped?.kind)
    }

    @Test fun tappingAnAttachmentNodeDeliversKindAttachment() {
        val f = GraphNode("file:1", "", null, 1, NodeKind.ATTACHMENT, Attachment.Type.IMAGE)
        graph.setGraph(listOf(f), emptyList(), mapOf(f.key to PointF2(0f, 0f)))
        tap(300f, 300f)
        assertEquals(NodeKind.ATTACHMENT, tapped?.kind)
    }

    @Test fun blankAttachmentTitleFallsBackToTheFileKindLabel() {
        val context = RuntimeEnvironment.getApplication()
        val expected = mapOf(
            Attachment.Type.IMAGE to R.string.network_file_photo,
            Attachment.Type.AUDIO to R.string.network_file_audio,
            Attachment.Type.VIDEO to R.string.network_file_video,
            Attachment.Type.GENERIC to R.string.network_file_other,
        )
        for ((type, res) in expected) {
            val f = GraphNode("file:$type", "", null, 1, NodeKind.ATTACHMENT, type)
            assertEquals(context.getString(res), f.displayTitle(context))
        }
    }
}
