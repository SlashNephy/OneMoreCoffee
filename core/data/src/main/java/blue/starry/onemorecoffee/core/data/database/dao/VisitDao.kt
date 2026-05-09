package blue.starry.onemorecoffee.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity

@Dao
interface VisitDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore(visit: VisitEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore(visits: List<VisitEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM visits")
    fun count(): Int

    @Query("DELETE FROM visits")
    fun deleteAll()
}
