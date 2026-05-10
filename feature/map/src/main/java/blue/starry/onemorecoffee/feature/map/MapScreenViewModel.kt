package blue.starry.onemorecoffee.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshProgress
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import blue.starry.onemorecoffee.core.domain.usecase.ObserveStoreSummariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MapScreenViewModel @Inject constructor(
    observeStoreSummariesUseCase: ObserveStoreSummariesUseCase,
    private val storeRepository: StoreRepository,
) : ViewModel() {
    private val refreshUiState = MutableStateFlow(MapRefreshUiState())

    val uiState: StateFlow<MapUiState> = combine(
        observeStoreSummariesUseCase()
        .map { stores ->
            if (stores.isEmpty()) {
                MapContentState.Empty
            } else {
                MapContentState.Ready(stores)
            }
        },
        refreshUiState,
    ) { content, refreshState ->
        MapUiState(
            content = content,
            isRefreshing = refreshState.isRefreshing,
            progressMessage = refreshState.progressMessage,
            statusMessage = refreshState.statusMessage,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MapUiState(),
        )

    fun refreshStores() {
        viewModelScope.launch {
            refreshUiState.update { state ->
                state.copy(
                    isRefreshing = true,
                    progressMessage = "店舗マスタに接続しています",
                    statusMessage = null,
                )
            }

            try {
                val result = storeRepository.refreshStores { progress ->
                    refreshUiState.update { state ->
                        state.copy(progressMessage = progress.toMessage())
                    }
                }
                refreshUiState.update { state ->
                    state.copy(
                        isRefreshing = false,
                        progressMessage = null,
                        statusMessage = "店舗データを更新しました: ${result.upserted} 件更新, ${result.skipped} 件スキップ",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }

                refreshUiState.update { state ->
                    state.copy(
                        isRefreshing = false,
                        progressMessage = null,
                        statusMessage = "Failed to refresh stores: ${error.message ?: error::class.simpleName}",
                    )
                }
            }
        }
    }

    private fun StoreRefreshProgress.toMessage(): String {
        return when (this) {
            StoreRefreshProgress.Connecting -> "店舗マスタに接続しています"
            is StoreRefreshProgress.Fetching -> buildString {
                append("店舗マスタを取得しています: ")
                append(fetched)
                total?.let { total ->
                    append(" / ")
                    append(total)
                }
                append(" 件")
            }
            StoreRefreshProgress.Saving -> "店舗マスタを保存しています"
            is StoreRefreshProgress.Finished -> "店舗マスタを更新しました"
        }
    }
}

private data class MapRefreshUiState(
    val isRefreshing: Boolean = false,
    val progressMessage: String? = null,
    val statusMessage: String? = null,
)
