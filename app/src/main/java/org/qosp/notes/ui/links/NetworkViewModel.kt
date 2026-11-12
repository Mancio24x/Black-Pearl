package org.qosp.notes.ui.links

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.qosp.notes.components.links.GraphEdge
import org.qosp.notes.components.links.GraphLayout
import org.qosp.notes.components.links.GraphNode
import org.qosp.notes.components.links.LinkGraph
import org.qosp.notes.components.links.LinkIndexRepository
import org.qosp.notes.components.links.PointF2

data class NetworkGraph(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
    val positions: Map<String, PointF2> = emptyMap(),
)

private data class GraphOptions(
    val unlinked: Boolean,
    val focus: String?,
    val tags: Boolean,
    val attachments: Boolean,
    val depth: Int,
)

class NetworkViewModel(repository: LinkIndexRepository) : ViewModel() {
    var controlsOpen = false
    val index = repository.index
    val showUnlinked = MutableStateFlow(true)
    val focus = MutableStateFlow<String?>(null)
    val showTags = MutableStateFlow(false)
    val showAttachments = MutableStateFlow(false)
    val depth = MutableStateFlow(1)

    private val options = combine(showUnlinked, focus, showTags, showAttachments, depth) { unlinked, focused, tags, files, levels ->
        GraphOptions(unlinked, focused, tags, files, levels)
    }

    val graph = combine(index, options) { current, o ->
        val (nodes, edges) = LinkGraph.build(current, o.unlinked, o.focus, o.depth, o.tags, o.attachments)
        NetworkGraph(nodes, edges, GraphLayout.layout(nodes, edges, GraphLayout.iterationsFor(nodes.size)))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkGraph())
}
