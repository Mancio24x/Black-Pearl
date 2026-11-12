package org.qosp.notes.tests

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.R
import org.qosp.notes.components.links.LinkGraph
import org.qosp.notes.components.links.PointF2
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.links.GraphView

class NetworkNavigationTest : KoinComponent {
    private val repo: NoteRepository by inject()

    @Test fun dragAndTapANoteInTheDrawerNetwork() {
        val source = Note(title = "Network test source", content = "[[Network test target]]", isLocalOnly = true)
        val target = Note(title = "Network test target", content = "Some text", isLocalOnly = true)
        val sourceId = runBlocking { repo.insertNote(source) }
        val targetId = runBlocking { repo.insertNote(target) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.findViewById<DrawerLayout>(R.id.drawer).openDrawer(GravityCompat.START, false)
                }
                onView(withText(R.string.nav_network)).perform(click())
                onView(withId(R.id.graph)).check(matches(isDisplayed()))
                // The application rebuilds its index asynchronously after a short debounce.
                val index = getKoin().get<org.qosp.notes.components.links.LinkIndexRepository>()
                val snapshot = runBlocking { kotlinx.coroutines.withTimeout(10_000) {
                    index.index.first { it.resolve(target.title)?.id == targetId }
                } }
                val (nodes, edges) = LinkGraph.build(snapshot)
                val sourceNode = nodes.single { it.noteId == sourceId }
                val targetNode = nodes.single { it.noteId == targetId }
                scenario.onActivity {
                    val graph = it.findViewById<GraphView>(R.id.graph)
                    // A two-note fixture on the real graph View uses the same index, navigation and touch handler as the app.
                    graph.setGraph(listOf(sourceNode, targetNode), edges.filter { it.from == sourceNode.key && it.to == targetNode.key },
                        mapOf(sourceNode.key to PointF2(0f, -30f), targetNode.key to PointF2(0f, 30f)))
                    val rendered = requireNotNull(graph.screenPosition(targetNode.key))
                    val x = rendered.x
                    val y = rendered.y
                    val movedX = x + graph.width / 5
                    fun touch(action: Int, px: Float) {
                        val now = SystemClock.uptimeMillis()
                        val e = MotionEvent.obtain(now, now, action, px, y, 0)
                        try { graph.dispatchTouchEvent(e) } finally { e.recycle() }
                    }
                    touch(MotionEvent.ACTION_DOWN, x)
                    touch(MotionEvent.ACTION_MOVE, movedX)
                    touch(MotionEvent.ACTION_UP, movedX)
                    touch(MotionEvent.ACTION_DOWN, movedX)
                    touch(MotionEvent.ACTION_UP, movedX)
                }
                scenario.onActivity {
                    assertEquals(R.id.fragment_editor, it.navController.currentDestination?.id)
                    assertEquals(targetId, it.navController.currentBackStackEntry?.arguments?.getLong("noteId"))
                }
            }
        } finally {
            runBlocking { repo.deleteNotes(source.copy(id = sourceId), target.copy(id = targetId)) }
        }
    }
}
