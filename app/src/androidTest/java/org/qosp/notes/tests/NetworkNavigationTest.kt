package org.qosp.notes.tests

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.R
import org.qosp.notes.components.links.GraphNode
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.links.GraphView

class NetworkNavigationTest : KoinComponent {
    private val repo: NoteRepository by inject()

    @Test fun drawerDestinationOpensNoteThroughNodeSheet() {
        val source = Note(title = "Network test source", content = "[[Network test target]]", isLocalOnly = true)
        val target = Note(title = "Network test target", content = "Some text", isLocalOnly = true)
        val sourceId = runBlocking { repo.insertNote(source) }
        val targetId = runBlocking { repo.insertNote(target) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.findViewById<NavigationView>(R.id.navigation_view).menu.performIdentifierAction(R.id.fragment_network, 0)
                }
                onView(withId(R.id.graph)).check(matches(isDisplayed()))
                // The application rebuilds its index asynchronously after a short debounce.
                val index = getKoin().get<org.qosp.notes.components.links.LinkIndexRepository>()
                runBlocking { kotlinx.coroutines.withTimeout(10_000) { index.index.first { it.resolve(target.title)?.id == targetId } } }
                scenario.onActivity {
                    it.findViewById<GraphView>(R.id.graph).onNodeTap(GraphNode("network test target", target.title, targetId, 1))
                }
                onView(withText(R.string.network_open)).perform(click())
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
