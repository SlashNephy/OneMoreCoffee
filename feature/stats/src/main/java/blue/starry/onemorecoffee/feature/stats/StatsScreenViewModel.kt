package blue.starry.onemorecoffee.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.model.ProgressStats
import blue.starry.onemorecoffee.core.domain.usecase.ObserveProgressStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StatsScreenViewModel @Inject constructor(
    observeProgressStatsUseCase: ObserveProgressStatsUseCase,
) : ViewModel() {
    val uiState: StateFlow<ProgressStats> = observeProgressStatsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgressStats(totalStores = 0, visitedStores = 0, completionRate = 0.0),
        )
}
