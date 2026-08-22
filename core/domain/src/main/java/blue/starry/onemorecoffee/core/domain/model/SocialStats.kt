package blue.starry.onemorecoffee.core.domain.model

data class SocialStats(
    val visitedStoreCount: Int,
    val prefectureCount: Int,
) {
    companion object {
        fun from(summaries: List<StoreVisitSummary>): SocialStats {
            val visited = summaries.filter(StoreVisitSummary::isVisited)

            return SocialStats(
                visitedStoreCount = visited.size,
                prefectureCount = visited.map(StoreVisitSummary::prefecture).distinct().size,
            )
        }
    }
}
