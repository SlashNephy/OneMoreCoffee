package blue.starry.onemorecoffee.feature.importer

import blue.starry.onemorecoffee.core.domain.repository.VisitImportResult

sealed interface ImportUiState {
    data object Waiting : ImportUiState

    data object Importing : ImportUiState

    data class Completed(
        val result: VisitImportResult,
    ) : ImportUiState

    data class Failed(
        val message: String,
    ) : ImportUiState
}
