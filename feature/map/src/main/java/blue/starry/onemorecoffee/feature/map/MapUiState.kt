package blue.starry.onemorecoffee.feature.map

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary

sealed interface MapUiState {
    data object Loading : MapUiState

    data object Empty : MapUiState

    data class Ready(
        val stores: List<StoreVisitSummary>,
    ) : MapUiState
}
