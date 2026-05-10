package blue.starry.onemorecoffee.feature.list

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshResult
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshProgress
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import blue.starry.onemorecoffee.core.domain.usecase.ObserveStoreSummariesUseCase
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreListScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeStoreRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeStoreRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_filtersStoresByQueryCaseInsensitively() = runTest {
        repository.summaries.value = listOf(
            summary(id = "ginza", name = "STARBUCKS RESERVE", fullAddress = "東京都中央区銀座"),
            summary(id = "shibuya", name = "スターバックス 渋谷", fullAddress = "東京都渋谷区"),
        )
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.updateQuery("reserve")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.stores.map(StoreVisitSummary::id)).containsExactly("ginza")
    }

    @Test
    fun uiState_filtersStoresByJapaneseAddress() = runTest {
        repository.summaries.value = listOf(
            summary(id = "ginza", fullAddress = "東京都中央区銀座"),
            summary(id = "osaka", fullAddress = "大阪府大阪市"),
        )
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.updateQuery("銀座")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.stores.map(StoreVisitSummary::id)).containsExactly("ginza")
    }

    @Test
    fun uiState_filtersStoresByVisitedStatus() = runTest {
        repository.summaries.value = listOf(
            summary(id = "visited", visitCount = 2),
            summary(id = "unvisited", visitCount = 0),
        )
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.updateVisitedFilter(VisitedFilter.Visited)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.stores.map(StoreVisitSummary::id)).containsExactly("visited")
    }

    private fun newViewModel(): StoreListScreenViewModel {
        return StoreListScreenViewModel(
            observeStoreSummariesUseCase = ObserveStoreSummariesUseCase(repository),
        )
    }

    private fun summary(
        id: String,
        name: String = "スターバックス",
        fullAddress: String = "東京都千代田区",
        visitCount: Int = 0,
    ): StoreVisitSummary {
        return StoreVisitSummary(
            id = id,
            name = name,
            prefecture = "東京都",
            fullAddress = fullAddress,
            latitude = 35.0,
            longitude = 139.0,
            isReserve = false,
            visitCount = visitCount,
            lastVisitedOn = if (visitCount > 0) LocalDate.of(2026, 5, 9) else null,
        )
    }

    private class FakeStoreRepository : StoreRepository {
        val summaries = MutableStateFlow(emptyList<StoreVisitSummary>())

        override fun observeStoreSummaries(): Flow<List<StoreVisitSummary>> {
            return summaries
        }

        override suspend fun refreshStores(
            onProgress: (StoreRefreshProgress) -> Unit,
        ): StoreRefreshResult {
            return StoreRefreshResult(upserted = 0, skipped = 0)
        }
    }
}
