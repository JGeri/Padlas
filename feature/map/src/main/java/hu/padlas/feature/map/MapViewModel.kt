package hu.padlas.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class MapViewModel @Inject constructor(
    private val repository: MapRepository,
) : ViewModel() {

    /** Ids of the nodes the user drilled into, outermost first. */
    private val path = MutableStateFlow<List<String>>(emptyList())

    /** Folders fetched lazily by [MapRepository.expand], keyed by file path. */
    private val expanded = MutableStateFlow<Map<String, MapNode>>(emptyMap())

    private val cacheTourIndex = MutableStateFlow(0)

    val state: StateFlow<MapUiState> = combine(
        repository.map,
        path,
        expanded,
        cacheTourIndex,
    ) { map, currentPath, extra, tourIndex ->
        val chain = resolveChain(map.root, currentPath, extra)
        MapUiState(
            loading = false,
            map = map,
            chain = chain,
            node = chain.lastOrNull() ?: map.root,
            cacheTourIndex = tourIndex,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun refresh() = repository.refresh()

    fun drill(node: MapNode) {
        if (node.children.isEmpty() && node.path != null) {
            // The probe only ever walks two levels; ask for the next two from here.
            viewModelScope.launch {
                val loaded = repository.expand(node.path) ?: return@launch
                expanded.update { it + (node.path to loaded) }
                path.update { it + node.id }
            }
            return
        }
        if (node.children.isEmpty()) return
        path.update { it + node.id }
    }

    fun up(): Boolean {
        val current = path.value
        if (current.isEmpty()) return false
        path.value = current.dropLast(1)
        return true
    }

    fun jumpTo(depth: Int) {
        path.update { it.take(depth) }
    }

    fun advanceCacheTour() = cacheTourIndex.update { it + 1 }

    fun resetCacheTour() {
        cacheTourIndex.value = 0
    }

    private fun resolveChain(
        root: MapNode,
        ids: List<String>,
        extra: Map<String, MapNode>,
    ): List<MapNode> {
        val chain = ArrayList<MapNode>(ids.size + 1)
        chain += root
        var current = root
        for (id in ids) {
            val child = current.children.firstOrNull { it.id == id }
                ?: extra.values.firstOrNull { it.id == id }
                ?: break
            val enriched = if (child.children.isEmpty() && child.path != null) {
                extra[child.path]?.copy(id = child.id, label = child.label) ?: child
            } else {
                child
            }
            chain += enriched
            current = enriched
        }
        return chain
    }
}

internal data class MapUiState(
    val loading: Boolean = true,
    val map: StorageMap? = null,
    val chain: List<MapNode> = emptyList(),
    val node: MapNode? = null,
    val cacheTourIndex: Int = 0,
) {
    val depth: Int get() = (chain.size - 1).coerceAtLeast(0)
}
