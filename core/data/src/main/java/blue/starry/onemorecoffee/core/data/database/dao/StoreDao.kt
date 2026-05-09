package blue.starry.onemorecoffee.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Upsert
    fun upsertAll(stores: List<StoreEntity>)

    @Query("DELETE FROM stores WHERE id NOT IN (:ids)")
    fun deleteStoresNotIn(ids: List<String>)

    @Query("SELECT * FROM stores ORDER BY prefCode ASC, name ASC")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("SELECT id FROM stores")
    fun ids(): List<String>
}
