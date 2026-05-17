package blue.starry.onemorecoffee.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Upsert
    suspend fun upsertAll(stores: List<StoreEntity>)

    @Query("DELETE FROM stores WHERE id NOT IN (:ids)")
    suspend fun deleteStoresNotIn(ids: List<String>)

    @Query("DELETE FROM stores")
    suspend fun deleteAllStores()

    @Query("SELECT * FROM stores ORDER BY prefCode ASC, name ASC")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query(
        """
        SELECT
            stores.id AS id,
            stores.name AS name,
            stores.prefecture AS prefecture,
            stores.fullAddress AS fullAddress,
            stores.latitude AS latitude,
            stores.longitude AS longitude,
            stores.isReserve AS isReserve,
            COUNT(visits.id) AS visitCount,
            MAX(visits.visitedOn) AS lastVisitedOn,
            stores.rawJson AS rawJson
        FROM stores
        LEFT JOIN visits ON visits.storeId = stores.id
        GROUP BY
            stores.id,
            stores.name,
            stores.prefCode,
            stores.prefecture,
            stores.fullAddress,
            stores.latitude,
            stores.longitude,
            stores.isReserve,
            stores.rawJson
        ORDER BY stores.prefCode ASC, stores.name ASC
        """,
    )
    fun observeSummaries(): Flow<List<StoreVisitSummary>>

    @Query("SELECT id FROM stores")
    suspend fun ids(): List<String>
}
