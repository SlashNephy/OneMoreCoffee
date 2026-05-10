package blue.starry.onemorecoffee.feature.settings

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshResult
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import blue.starry.onemorecoffee.core.domain.repository.VisitImportResult
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
class SettingsScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var storeRepository: FakeStoreRepository
    private lateinit var visitRepository: FakeVisitRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        storeRepository = FakeStoreRepository()
        visitRepository = FakeVisitRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshStores_setsLoadingAndSuccessMessage() = runTest {
        val refreshGate = CompletableDeferred<Unit>()
        storeRepository.refreshGate = refreshGate
        val viewModel = newViewModel()

        viewModel.refreshStores()
        runCurrent()
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        refreshGate.complete(Unit)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.statusMessage).isEqualTo("店舗データを更新しました: 3 件更新, 1 件スキップ")
    }

    @Test
    fun refreshStores_setsEnglishErrorMessage() = runTest {
        storeRepository.error = IllegalStateException("network down")
        val viewModel = newViewModel()

        viewModel.refreshStores()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.statusMessage).isEqualTo("Failed to refresh stores: network down")
    }

    @Test
    fun logoutImporter_callsRepositoryAndSetsMessage() = runTest {
        val viewModel = newViewModel()

        viewModel.logoutImporter()
        advanceUntilIdle()

        assertThat(visitRepository.logoutCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.statusMessage).isEqualTo("インポーターからログアウトしました")
    }

    private fun newViewModel(): SettingsScreenViewModel {
        return SettingsScreenViewModel(
            storeRepository = storeRepository,
            visitRepository = visitRepository,
        )
    }

    private class FakeStoreRepository : StoreRepository {
        var error: Throwable? = null
        var refreshGate: CompletableDeferred<Unit>? = null

        override fun observeStoreSummaries(): Flow<List<StoreVisitSummary>> {
            return emptyFlow()
        }

        override suspend fun refreshStores(): StoreRefreshResult {
            refreshGate?.await()
            error?.let { throw it }
            return StoreRefreshResult(upserted = 3, skipped = 1)
        }
    }

    private class FakeVisitRepository : VisitRepository {
        var logoutCount = 0

        override suspend fun importStarbucksVisits(json: String): VisitImportResult {
            return VisitImportResult(inserted = 0, duplicated = 0, unknownStoreVisits = 0, failed = 0)
        }

        override suspend fun logoutImporter() {
            logoutCount += 1
        }
    }
}
