package org.qosp.notes.components.html

/** One imported HTML document. [entry] is the main page, relative to the document folder. */
data class HtmlDocument(
    val id: String,
    val title: String,
    val entry: String,
    val importedAt: Long,
    val lastOpenedAt: Long = 0L,
    val progressUpdatedAt: Long = 0L,
)
