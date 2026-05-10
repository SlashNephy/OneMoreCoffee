package blue.starry.onemorecoffee.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
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
                state.copy(isLoading = true, statusMessage = null)
            }

            try {
                val result = storeRepository.refreshStores()
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
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
                        statusMessage = "Failed to refresh stores: ${error.message ?: error::class.simpleName}",
                    )
                }
            }
        }
    }

    fun logoutImporter() {
        viewModelScope.launch {
            mutableUiState.update { state ->
                state.copy(isLoading = true, statusMessage = null)
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
}
