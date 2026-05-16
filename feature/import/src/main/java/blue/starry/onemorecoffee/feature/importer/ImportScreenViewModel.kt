package blue.starry.onemorecoffee.feature.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ImportScreenViewModel @Inject constructor(
    private val visitRepository: VisitRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<ImportUiState>(ImportUiState.Waiting)
    val uiState: StateFlow<ImportUiState> = mutableUiState

    private val mutableReturnToSettingsEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val returnToSettingsEvents: SharedFlow<Unit> = mutableReturnToSettingsEvents

    fun importJson(json: String) {
        viewModelScope.launch {
            mutableUiState.update { ImportUiState.Importing }

            try {
                val result = visitRepository.importStarbucksVisits(json)
                mutableUiState.update { ImportUiState.Completed(result) }
                mutableReturnToSettingsEvents.emit(Unit)
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
}
