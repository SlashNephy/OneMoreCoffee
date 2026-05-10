package blue.starry.onemorecoffee.feature.map

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary

data class MapUiState(
    val content: MapContentState = MapContentState.Loading,
    val isRefreshing: Boolean = false,
    val progressMessage: String? = null,
    val statusMessage: String? = null,
)

sealed interface MapContentState {
    data object Loading : MapContentState

    data object Empty : MapContentState

    data class Ready(
        val stores: List<StoreVisitSummary>,
    ) : MapContentState
}
