package org.qosp.notes.components.links

/** A node of the note graph. [noteId] is null for a ghost (a link target without a note). */
data class GraphNode(val key: String, val title: String, val noteId: Long?, val degree: Int)

data class GraphEdge(val from: String, val to: String)

object LinkGraph {

    /**
     * Builds the graph. Nodes without any link are left out unless [showUnlinked]. With [focusKey] only that node and
     * its direct neighbours (and the edges touching the focus) are returned. Degree = number of distinct neighbours
     * in the whole graph.
     */
    fun build(
        index: LinkIndex,
        showUnlinked: Boolean = false,
        focusKey: String? = null,
    ): Pair<List<GraphNode>, List<GraphEdge>> {
        val ghostSpelling = index.ghostTargets().associateBy { WikiLinks.key(it) }

        val edgeSet = LinkedHashSet<GraphEdge>()
        val neighbours = HashMap<String, MutableSet<String>>()
        for (note in index.titles) {
            val from = WikiLinks.key(note.title)
            for (link in index.outgoing(note.id)) {
                val to = WikiLinks.key(link.target)
                if (to.isEmpty() || to == from) continue
                edgeSet.add(GraphEdge(from, to))
                neighbours.getOrPut(from) { HashSet() }.add(to)
                neighbours.getOrPut(to) { HashSet() }.add(from)
            }
        }

        val all = LinkedHashMap<String, GraphNode>()
        for (note in index.titles) {
            val key = WikiLinks.key(note.title)
            if (key in all) continue
            val real = index.resolve(note.title) ?: continue
            all[key] = GraphNode(key, real.title, real.id, neighbours[key]?.size ?: 0)
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
