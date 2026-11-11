package org.qosp.notes.ui.links

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
}
