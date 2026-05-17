package blue.starry.onemorecoffee.feature.importer

import blue.starry.onemorecoffee.core.domain.repository.VisitImportResult
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeVisitRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeVisitRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_startsWithWaiting() {
        val viewModel = newViewModel()

        assertThat(viewModel.uiState.value).isEqualTo(ImportUiState.Waiting)
    }

    @Test
    fun importJson_setsImportingThenCompleted() = runTest {
        val importGate = CompletableDeferred<Unit>()
        repository.importGate = importGate
        repository.result = VisitImportResult(inserted = 2, duplicated = 1, unknownStoreVisits = 3, failed = 4)
        val viewModel = newViewModel()

        viewModel.importJson("[{}]")
        runCurrent()
        assertThat(viewModel.uiState.value).isEqualTo(ImportUiState.Importing)

        importGate.complete(Unit)
        advanceUntilIdle()

        assertThat(repository.importedJson).isEqualTo("[{}]")
        assertThat(viewModel.uiState.value).isEqualTo(ImportUiState.Completed(repository.result))
    }

    @Test
    fun importJson_emitsReturnToSettingsEventOnCompletedImport() = runTest {
        repository.result = VisitImportResult(inserted = 2, duplicated = 1, unknownStoreVisits = 3, failed = 4)
        val viewModel = newViewModel()
        val event = async {
            viewModel.returnToSettingsEvents.first()
        }

        viewModel.importJson("[{}]")
        advanceUntilIdle()

        assertThat(event.await()).isEqualTo(repository.result)
    }

    @Test
    fun importJson_setsFailedWithEnglishMessageOnError() = runTest {
        repository.error = IllegalStateException("broken")
        val viewModel = newViewModel()

        viewModel.importJson("[{}]")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(ImportUiState.Failed("Failed to import visits."))
    }

    @Test
    fun importJson_doesNotConvertCancellationExceptionToFailed() = runTest {
        repository.error = CancellationException("cancelled")
        val viewModel = newViewModel()

        viewModel.importJson("[{}]")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(ImportUiState.Importing)
    }

    private fun newViewModel(): ImportScreenViewModel {
        return ImportScreenViewModel(repository)
    }

    private class FakeVisitRepository : VisitRepository {
        var importedJson: String? = null
        var result = VisitImportResult(inserted = 0, duplicated = 0, unknownStoreVisits = 0, failed = 0)
        var error: Throwable? = null
        var importGate: CompletableDeferred<Unit>? = null

        override suspend fun importStarbucksVisits(json: String): VisitImportResult {
            importedJson = json
            importGate?.await()
            error?.let { throw it }
            return result
        }

        override suspend fun logoutImporter() = Unit
    }
}
