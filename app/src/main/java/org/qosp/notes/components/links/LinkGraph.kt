package org.qosp.notes.components.links

/** A node of the note graph. [noteId] is null for a ghost (a link target without a note). */
data class GraphNode(val key: String, val title: String, val noteId: Long?, val degree: Int)

data class GraphEdge(val from: String, val to: String)

object LinkGraph {

    /**
     * Builds the graph. Nodes without any link are left out unless [showUnlinked]. With [focusKey] only that node and
     * its direct neighbours (and the edges touching the focus) are returned. Degree = number of distinct neighbours
     * in the whole graph. Two notes linking to each other share one edge (the first direction met).
     */
    fun build(
        index: LinkIndex,
        showUnlinked: Boolean = false,
        focusKey: String? = null,
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

        val all = LinkedHashMap<String, GraphNode>()
        for (note in index.visibleNotes) {
            val key = noteKey(note.id)
            all[key] = GraphNode(key, note.title, note.id, neighbours[key]?.size ?: 0)
        }
        for (key in neighbours.keys) {
            if (key in all) continue
            all[key] = GraphNode(key, ghostSpelling[key] ?: key, null, neighbours[key]?.size ?: 0)
        }

        if (focusKey != null) {
            val focus = all[focusKey] ?: return emptyList<GraphNode>() to emptyList()
            val keep = HashSet<String>(neighbours[focusKey].orEmpty()).apply { add(focusKey) }
            val nodes = all.values.filter { it.key in keep && (it.key == focus.key || it.degree > 0) }
            val edges = edgeSet.filter { it.from == focusKey || it.to == focusKey }
            return nodes to edges
        }

        val nodes = all.values.filter { showUnlinked || it.degree > 0 }
        return nodes to edgeSet.toList()
    }
}
