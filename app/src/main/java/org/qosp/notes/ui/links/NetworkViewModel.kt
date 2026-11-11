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

class NetworkViewModel(repository: LinkIndexRepository) : ViewModel() {
    val index = repository.index
    val showUnlinked = MutableStateFlow(false)
    val focus = MutableStateFlow<String?>(null)

    val graph = combine(index, showUnlinked, focus) { current, unlinked, focused ->
        val (nodes, edges) = LinkGraph.build(current, unlinked, focused)
        val iterations = (12_000 / nodes.size.coerceAtLeast(1)).coerceIn(2, 180)
        NetworkGraph(nodes, edges, GraphLayout.layout(nodes, edges, iterations))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkGraph())
}
