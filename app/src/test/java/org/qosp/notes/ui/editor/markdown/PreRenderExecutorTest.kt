package org.qosp.notes.ui.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PreRenderExecutorTest {
    /** Quick edits queue several renders: they run one after the other, never two at once. */
    @Test
    fun rendersNeverOverlap() {
        val executor = newPreRenderExecutor()
        val running = AtomicInteger()
        val mostAtOnce = AtomicInteger()
        val done = AtomicInteger()
        repeat(8) {
            executor.submit {
                mostAtOnce.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                Thread.sleep(30)
                running.decrementAndGet()
                done.incrementAndGet()
            }
        }
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
        assertEquals(8, done.get())
        assertEquals(1, mostAtOnce.get())
    }
}
