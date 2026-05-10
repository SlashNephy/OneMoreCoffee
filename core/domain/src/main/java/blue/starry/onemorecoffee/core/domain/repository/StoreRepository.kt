package blue.starry.onemorecoffee.core.domain.repository

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import kotlinx.coroutines.flow.Flow

interface StoreRepository {
    fun observeStoreSummaries(): Flow<List<StoreVisitSummary>>

    suspend fun refreshStores(
        onProgress: (StoreRefreshProgress) -> Unit = {},
    ): StoreRefreshResult
}

data class StoreRefreshResult(
    val upserted: Int,
    val skipped: Int,
)

sealed interface StoreRefreshProgress {
    data object Connecting : StoreRefreshProgress

    data class Fetching(
        val fetched: Int,
        val total: Int?,
    ) : StoreRefreshProgress

    data object Saving : StoreRefreshProgress

    data class Finished(
        val result: StoreRefreshResult,
    ) : StoreRefreshProgress
}
