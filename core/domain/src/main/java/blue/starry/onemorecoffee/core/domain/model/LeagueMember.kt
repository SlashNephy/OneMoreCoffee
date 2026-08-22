package blue.starry.onemorecoffee.core.domain.model

import java.time.Instant

data class LeagueMember(
    val uid: String,
    val displayName: String,
    val emoji: String,
    val visitedStoreCount: Int,
    val prefectureCount: Int,
    val updatedAt: Instant?,
)
