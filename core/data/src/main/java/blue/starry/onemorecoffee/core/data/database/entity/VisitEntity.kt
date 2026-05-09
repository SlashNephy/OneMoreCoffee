package blue.starry.onemorecoffee.core.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import java.time.LocalDate

@Entity(
    tableName = "visits",
    indices = [
        Index(value = ["storeId", "visitedOn"], unique = true),
    ],
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeId: String,
    val visitedOn: LocalDate,
    val source: VisitSource,
)
