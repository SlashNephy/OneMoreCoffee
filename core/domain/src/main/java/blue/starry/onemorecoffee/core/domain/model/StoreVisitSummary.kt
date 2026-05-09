package blue.starry.onemorecoffee.core.domain.model

import java.time.LocalDate

data class StoreVisitSummary(
    val id: String,
    val name: String,
    val prefecture: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double,
    val isReserve: Boolean,
    val visitCount: Int,
    val lastVisitedOn: LocalDate?,
) {
    val isVisited: Boolean
        get() = visitCount > 0
}
