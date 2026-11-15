package org.qosp.notes.components.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardStoreTest {
    private lateinit var file: File
    private lateinit var store: CardStore

    @Before fun setUp() {
        file = File(Files.createTempDirectory("cards").toFile(), "sub/cards.json")
        store = CardStore(file)
    }

    @Test fun emptyAtFirst() = assertTrue(store.list().isEmpty())

    @Test fun addedCardsSurviveANewStore() {
        val a = store.add("  Coop ", "4006381333931", "EAN_13", 10)
        val b = store.add("", "ABC", "CODE_128", 20)
        assertEquals("Coop", a.name)
        assertEquals("ABC", b.name) // no name given: the code is the name
        assertEquals(listOf(a, b), CardStore(file).list())
    }

    @Test fun renameAndDelete() {
        val a = store.add("Coop", "1", "CODE_128", 10)
        val b = store.add("Conad", "2", "CODE_128", 20)
        store.rename(a.id, " Coop Firenze ")
        store.rename(b.id, "   ") // blank: unchanged
        assertEquals(listOf("Coop Firenze", "Conad"), store.list().map { it.name })
        store.delete(a.id)
        assertEquals(listOf(b), store.list())
    }
}
