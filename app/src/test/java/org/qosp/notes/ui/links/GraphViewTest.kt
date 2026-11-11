package org.qosp.notes.ui.links

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.R
import org.qosp.notes.components.links.GraphNode
import org.qosp.notes.components.links.PointF2
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
}
