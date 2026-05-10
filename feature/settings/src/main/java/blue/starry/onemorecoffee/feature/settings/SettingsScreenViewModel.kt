package blue.starry.onemorecoffee.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshProgress
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsScreenViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    private val visitRepository: VisitRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState

    fun refreshStores() {
        viewModelScope.launch {
            mutableUiState.update { state ->
                state.copy(
                    isLoading = true,
                    progressMessage = "店舗マスタに接続しています",
                    statusMessage = null,
                )
            }

            try {
                val result = storeRepository.refreshStores { progress ->
                    mutableUiState.update { state ->
                        state.copy(progressMessage = progress.toMessage())
                    }
                }
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        progressMessage = null,
                        statusMessage = "店舗データを更新しました: ${result.upserted} 件更新, ${result.skipped} 件スキップ",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }

                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        progressMessage = null,
                        statusMessage = "Failed to refresh stores: ${error.message ?: error::class.simpleName}",
                    )
                }
            }
        }
    }

    fun logoutImporter() {
        viewModelScope.launch {
            mutableUiState.update { state ->
                state.copy(isLoading = true, progressMessage = null, statusMessage = null)
            }

            try {
                visitRepository.logoutImporter()
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        statusMessage = "インポーターからログアウトしました",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }

                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        statusMessage = "Failed to logout importer: ${error.message ?: error::class.simpleName}",
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
