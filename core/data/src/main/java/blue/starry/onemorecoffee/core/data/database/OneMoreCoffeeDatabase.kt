package blue.starry.onemorecoffee.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import blue.starry.onemorecoffee.core.data.database.dao.StoreDao
import blue.starry.onemorecoffee.core.data.database.dao.VisitDao
import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity

@Database(
    entities = [
        StoreEntity::class,
        VisitEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OneMoreCoffeeDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao

    abstract fun visitDao(): VisitDao
}
