package blue.starry.onemorecoffee.feature.list

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary

data class StoreListUiState(
    val query: String = "",
    val visitedFilter: VisitedFilter = VisitedFilter.All,
    val stores: List<StoreVisitSummary> = emptyList(),
) {
    val isEmpty: Boolean
        get() = stores.isEmpty()
}

enum class VisitedFilter(
    val label: String,
) {
    All("すべて"),
    Visited("訪問済み"),
    Unvisited("未訪問"),
}
