package blue.starry.onemorecoffee.core.domain.model

import java.time.Instant
import java.time.LocalDate

sealed interface ActivityEvent {
    val id: String
    val uid: String
    val createdAt: Instant

    data class Visit(
        override val id: String,
        override val uid: String,
        override val createdAt: Instant,
        val storeId: String,
        val storeName: String,
        val prefecture: String,
        val visitedOn: LocalDate,
    ) : ActivityEvent

    data class Backfill(
        override val id: String,
        override val uid: String,
        override val createdAt: Instant,
        val count: Int,
    ) : ActivityEvent

    data class MemberJoined(
        override val id: String,
        override val uid: String,
        override val createdAt: Instant,
    ) : ActivityEvent
}
