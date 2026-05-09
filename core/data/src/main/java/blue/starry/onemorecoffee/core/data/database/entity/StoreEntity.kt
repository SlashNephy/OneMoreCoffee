package blue.starry.onemorecoffee.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameEn: String?,
    val prefCode: String,
    val prefecture: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double,
    val isReserve: Boolean,
    val rawJson: String,
    val lastSeenAt: Instant,
)
