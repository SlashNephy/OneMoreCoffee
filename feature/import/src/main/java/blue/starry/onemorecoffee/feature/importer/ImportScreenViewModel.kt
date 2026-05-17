package blue.starry.onemorecoffee.feature.importer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.repository.VisitImportResult
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ImportScreenViewModel @Inject constructor(
    private val visitRepository: VisitRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<ImportUiState>(ImportUiState.Waiting)
    val uiState: StateFlow<ImportUiState> = mutableUiState

    private val mutableReturnToSettingsEvents = Channel<VisitImportResult>(capacity = Channel.BUFFERED)
    val returnToSettingsEvents: Flow<VisitImportResult> = mutableReturnToSettingsEvents.receiveAsFlow()

    fun importJson(json: String) {
        viewModelScope.launch {
            mutableUiState.update { ImportUiState.Importing }

            try {
                val result = visitRepository.importStarbucksVisits(json)
                logImportCompleted(result)
                mutableUiState.update { ImportUiState.Completed(result) }
                mutableReturnToSettingsEvents.send(result)
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }

                mutableUiState.update {
                    ImportUiState.Failed("Failed to import visits.")
                }
            }
        }
    }

    private companion object {
        private const val TAG = "ImportScreenViewModel"

        private fun logImportCompleted(result: VisitImportResult) {
            runCatching {
                Log.d(
                    TAG,
                    "Visit import completed: inserted=${result.inserted}, duplicated=${result.duplicated}, " +
                        "unknownStoreVisits=${result.unknownStoreVisits}, failed=${result.failed}",
                )
            }
        }
    }
}
