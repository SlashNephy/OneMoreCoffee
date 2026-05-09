package blue.starry.onemorecoffee.core.data.repository

import blue.starry.onemorecoffee.core.data.database.dao.StoreDao
import blue.starry.onemorecoffee.core.data.starbucks.StarbucksStoreDataSource
import blue.starry.onemorecoffee.core.data.starbucks.StoreFieldMapper
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshResult
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class StoreRepositoryImpl @Inject constructor(
    private val storeDao: StoreDao,
    private val starbucksStoreDataSource: StarbucksStoreDataSource,
) : StoreRepository {
    override fun observeStoreSummaries(): Flow<List<StoreVisitSummary>> {
        return storeDao.observeSummaries()
    }

    override suspend fun refreshStores(): StoreRefreshResult {
        val hits = starbucksStoreDataSource.fetchAllStores()
        val stores = hits.mapNotNull { hit ->
            StoreFieldMapper.toEntity(
                fields = hit.fields,
                rawJson = hit.fields.toString(),
            )
        }

        storeDao.upsertAll(stores)
        if (stores.isEmpty()) {
            storeDao.deleteAllStores()
        } else {
            storeDao.deleteStoresNotIn(stores.map { store -> store.id })
        }

        return StoreRefreshResult(
            upserted = stores.size,
            skipped = hits.size - stores.size,
        )
    }
}
