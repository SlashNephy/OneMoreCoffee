package blue.starry.onemorecoffee.core.domain.model

import java.time.LocalDate

data class Visit(
    val id: Long,
    val storeId: String,
    val visitedOn: LocalDate,
    val source: VisitSource,
)

enum class VisitSource {
    IMPORTED_STARBUCKS,
}
