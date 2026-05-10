package blue.starry.onemorecoffee.feature.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.usecase.ObserveStoreSummariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StoreListScreenViewModel @Inject constructor(
    observeStoreSummariesUseCase: ObserveStoreSummariesUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val visitedFilter = MutableStateFlow(VisitedFilter.All)

    val uiState: StateFlow<StoreListUiState> = combine(
        observeStoreSummariesUseCase(),
        query,
        visitedFilter,
    ) { stores, query, visitedFilter ->
        val normalizedQuery = query.trim()
        val filteredStores = stores
            .filter { store -> store.matchesQuery(normalizedQuery) }
            .filter { store -> visitedFilter.matches(store) }

        StoreListUiState(
            query = query,
            visitedFilter = visitedFilter,
            stores = filteredStores,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StoreListUiState(),
    )

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateVisitedFilter(value: VisitedFilter) {
        visitedFilter.value = value
    }

    private fun StoreVisitSummary.matchesQuery(query: String): Boolean {
        if (query.isEmpty()) {
            return true
        }

        return name.contains(query, ignoreCase = true) ||
            fullAddress.contains(query, ignoreCase = true)
    }

    private fun VisitedFilter.matches(store: StoreVisitSummary): Boolean {
        return when (this) {
            VisitedFilter.All -> true
            VisitedFilter.Visited -> store.isVisited
            VisitedFilter.Unvisited -> !store.isVisited
        }
    }
}
