package blue.starry.onemorecoffee.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.usecase.ObserveStoreSummariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MapScreenViewModel @Inject constructor(
    observeStoreSummariesUseCase: ObserveStoreSummariesUseCase,
) : ViewModel() {
    val uiState: StateFlow<MapUiState> = observeStoreSummariesUseCase()
        .map { stores ->
            if (stores.isEmpty()) {
                MapUiState.Empty
            } else {
                MapUiState.Ready(stores)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MapUiState.Loading,
        )
}
