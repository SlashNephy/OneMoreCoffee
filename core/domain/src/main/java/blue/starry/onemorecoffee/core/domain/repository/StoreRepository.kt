package blue.starry.onemorecoffee.core.domain.repository

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import kotlinx.coroutines.flow.Flow

interface StoreRepository {
    fun observeStoreSummaries(): Flow<List<StoreVisitSummary>>

    suspend fun refreshStores(): StoreRefreshResult
}

data class StoreRefreshResult(
    val upserted: Int,
    val skipped: Int,
)
