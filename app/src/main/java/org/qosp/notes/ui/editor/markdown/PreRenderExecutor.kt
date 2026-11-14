package org.qosp.notes.ui.editor.markdown

import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Background executor for the editor's Markdown highlighting. One thread at most: a Markwon instance is not
 * thread-safe (its table visitor keeps the row being built), so two renders of quick edits must never overlap
 * (crash "List.size() on a null object" in TableRowSpan). The thread ends by itself after a minute without work.
 */
fun newPreRenderExecutor(): ExecutorService =
    ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS, LinkedBlockingQueue())
