package blue.starry.onemorecoffee.feature.stats

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshResult
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import blue.starry.onemorecoffee.core.domain.usecase.ObserveProgressStatsUseCase
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
class StatsScreenViewModelTest {
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
    fun uiState_startsWithZeroProgressStats() {
        val viewModel = newViewModel()

        assertThat(viewModel.uiState.value.totalStores).isEqualTo(0)
        assertThat(viewModel.uiState.value.visitedStores).isEqualTo(0)
        assertThat(viewModel.uiState.value.completionRate).isEqualTo(0.0)
    }

    @Test
    fun uiState_exposesObservedProgressStats() = runTest {
        repository.summaries.value = listOf(
            summary(id = "visited", visitCount = 1),
            summary(id = "unvisited", visitCount = 0),
        )
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.totalStores).isEqualTo(2)
        assertThat(viewModel.uiState.value.visitedStores).isEqualTo(1)
        assertThat(viewModel.uiState.value.completionRate).isEqualTo(0.5)
    }

    private fun newViewModel(): StatsScreenViewModel {
        return StatsScreenViewModel(
            observeProgressStatsUseCase = ObserveProgressStatsUseCase(repository),
        )
    }

    private fun summary(
        id: String,
        visitCount: Int,
    ): StoreVisitSummary {
        return StoreVisitSummary(
            id = id,
            name = "スターバックス",
            prefecture = "東京都",
            fullAddress = "東京都千代田区",
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

        override suspend fun refreshStores(): StoreRefreshResult {
            return StoreRefreshResult(upserted = 0, skipped = 0)
        }
    }
}
