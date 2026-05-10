package blue.starry.onemorecoffee.feature.map

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshProgress
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshResult
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import blue.starry.onemorecoffee.core.domain.usecase.ObserveStoreSummariesUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var storeRepository: FakeStoreRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        storeRepository = FakeStoreRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshStores_exposesProgressAndSuccessMessage() = runTest {
        val viewModel = newViewModel()

        viewModel.refreshStores()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
        assertThat(viewModel.uiState.value.progressMessage).isNull()
        assertThat(viewModel.uiState.value.statusMessage).isEqualTo("店舗データを更新しました: 1 件更新, 0 件スキップ")
        assertThat(storeRepository.progressMessages).containsExactly(
            StoreRefreshProgress.Connecting,
            StoreRefreshProgress.Fetching(fetched = 1, total = 2),
            StoreRefreshProgress.Saving,
        ).inOrder()
    }

    @Test
    fun refreshStores_exposesErrorMessage() = runTest {
        storeRepository.error = IllegalStateException("network down")
        val viewModel = newViewModel()

        viewModel.refreshStores()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
        assertThat(viewModel.uiState.value.progressMessage).isNull()
        assertThat(viewModel.uiState.value.statusMessage).isEqualTo("Failed to refresh stores: network down")
    }

    private fun newViewModel(): MapScreenViewModel {
        return MapScreenViewModel(
            observeStoreSummariesUseCase = ObserveStoreSummariesUseCase(storeRepository),
            storeRepository = storeRepository,
        )
    }

    private class FakeStoreRepository : StoreRepository {
        val stores = MutableStateFlow<List<StoreVisitSummary>>(emptyList())
        val progressMessages = mutableListOf<StoreRefreshProgress>()
        var error: Throwable? = null

        override fun observeStoreSummaries(): Flow<List<StoreVisitSummary>> {
            return stores
        }

        override suspend fun refreshStores(
            onProgress: (StoreRefreshProgress) -> Unit,
        ): StoreRefreshResult {
            error?.let { throw it }
            listOf(
                StoreRefreshProgress.Connecting,
                StoreRefreshProgress.Fetching(fetched = 1, total = 2),
                StoreRefreshProgress.Saving,
            ).forEach { event ->
                progressMessages += event
                onProgress(event)
            }
            return StoreRefreshResult(upserted = 1, skipped = 0)
        }
    }
}
