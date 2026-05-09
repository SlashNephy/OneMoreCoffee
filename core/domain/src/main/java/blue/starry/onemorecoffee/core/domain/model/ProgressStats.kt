package blue.starry.onemorecoffee.core.domain.model

data class ProgressStats(
    val totalStores: Int,
    val visitedStores: Int,
    val completionRate: Double,
) {
    companion object {
        fun from(summaries: List<StoreVisitSummary>): ProgressStats {
            val totalStores = summaries.size
            val visitedStores = summaries.count(StoreVisitSummary::isVisited)
            val completionRate = if (totalStores == 0) {
                0.0
            } else {
                visitedStores.toDouble() / totalStores
            }

            return ProgressStats(
                totalStores = totalStores,
                visitedStores = visitedStores,
                completionRate = completionRate,
            )
        }
    }
}
