package org.qosp.notes.components.links

import org.qosp.notes.data.model.Attachment
import java.util.Locale

enum class NodeKind { NOTE, MISSING, TAG, ATTACHMENT }

/**
 * A node of the note graph. [noteId] is null for a ghost (a link target without a note) and for a tag; for an attachment
 * it is the id of the owning note. An attachment's [title] is its file name, else its description, else "".
 */
data class GraphNode(
    val key: String,
    val title: String,
    val noteId: Long?,
    val degree: Int,
    val kind: NodeKind = if (noteId != null) NodeKind.NOTE else NodeKind.MISSING,
    val attachmentType: Attachment.Type? = null,
)

data class GraphEdge(val from: String, val to: String)

object LinkGraph {

    /**
     * Builds the graph. Nodes without any link are left out unless [showUnlinked]. With [tags] / [attachments] tag and
     * attachment nodes (and their edges to the notes) are added. With [focusKey] only that node and everything within
     * [depth] steps (1..5) of it is returned, with every edge between those nodes. Degree = number of distinct
     * neighbours in the whole graph. Two notes linking to each other share one edge (the first direction met).
     */
    fun build(
        index: LinkIndex,
        showUnlinked: Boolean = false,
        focusKey: String? = null,
        depth: Int = 1,
        tags: Boolean = false,
        attachments: Boolean = false,
    ): Pair<List<GraphNode>, List<GraphEdge>> {
        // Keep a node for every visible database row. Wiki links still resolve to the newest matching title.
        fun noteKey(id: Long) = "note:$id"
        fun targetKey(title: String) = index.resolve(title)?.let { noteKey(it.id) } ?: "missing:${WikiLinks.key(title)}"
        val ghostSpelling = index.ghostTargets().associateBy(::targetKey)

        val edgeSet = LinkedHashSet<GraphEdge>()
        val pairs = HashSet<Pair<String, String>>()
        val neighbours = HashMap<String, MutableSet<String>>()
        for (note in index.visibleNotes) {
            val from = noteKey(note.id)
            for (link in index.outgoing(note.id)) {
                if (WikiLinks.key(link.target).isEmpty()) continue
                val to = targetKey(link.target)
                if (to == from) continue
                if (pairs.add(if (from < to) from to to else to to from)) edgeSet.add(GraphEdge(from, to))
                neighbours.getOrPut(from) { HashSet() }.add(to)
                neighbours.getOrPut(to) { HashSet() }.add(from)
            }
        }

        val extra = LinkedHashMap<String, GraphNode>()
        fun connect(from: String, node: GraphNode) {
            extra.putIfAbsent(node.key, node)
            edgeSet.add(GraphEdge(from, node.key))
            neighbours.getOrPut(from) { HashSet() }.add(node.key)
            neighbours.getOrPut(node.key) { HashSet() }.add(from)
        }
        for (note in index.visibleNotes) {
            val from = noteKey(note.id)
            if (tags) {
                for (name in index.tagsOf(note.id)) {
                    connect(from, GraphNode("tag:${name.lowercase(Locale.ROOT)}", "#$name", null, 0, NodeKind.TAG))
                }
            }
            if (attachments) {
                note.attachments.forEachIndexed { i, a ->
                    if (a.isEmpty()) return@forEachIndexed
                    val title = a.fileName.ifEmpty { a.description }
                    connect(from, GraphNode("file:${note.id}:$i", title, note.id, 0, NodeKind.ATTACHMENT, a.type))
                }
            }
        }

        val all = LinkedHashMap<String, GraphNode>()
        for (note in index.visibleNotes) {
            val key = noteKey(note.id)
            all[key] = GraphNode(key, note.title, note.id, neighbours[key]?.size ?: 0)
        }
        for (key in neighbours.keys) {
            if (key in all) continue
            val node = extra[key] ?: GraphNode(key, ghostSpelling[key] ?: key, null, 0, NodeKind.MISSING)
            all[key] = node.copy(title = if (node.kind == NodeKind.MISSING) ghostSpelling[key] ?: key else node.title,
                degree = neighbours[key]?.size ?: 0)
        }

        if (focusKey != null) {
            val focus = all[focusKey] ?: return emptyList<GraphNode>() to emptyList()
            val keep = HashSet<String>().apply { add(focusKey) }
            var frontier = listOf(focusKey)
            repeat(depth.coerceIn(1, 5)) {
                frontier = frontier.flatMap { neighbours[it].orEmpty() }.filter { keep.add(it) }
            }
            val nodes = all.values.filter { it.key in keep && (it.key == focus.key || it.degree > 0) }
            val edges = edgeSet.filter { it.from in keep && it.to in keep }
            return nodes to edges
        }

        val nodes = all.values.filter { showUnlinked || it.degree > 0 }
        return nodes to edgeSet.toList()
    }
}
