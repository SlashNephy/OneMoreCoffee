package blue.starry.onemorecoffee.core.domain.model

// 一度の同期で初訪問が閾値を超えたら、個別イベントではなく件数だけの BACKFILL に畳む。
// フィードにインポート由来のイベントが数百件流れるのを防ぐ（設計書 §4.4）。
sealed interface VisitPublicationPlan {
    data object None : VisitPublicationPlan

    data class Individual(
        val visits: List<FirstVisit>,
    ) : VisitPublicationPlan

    data class Backfill(
        val count: Int,
    ) : VisitPublicationPlan

    companion object {
        const val BACKFILL_THRESHOLD = 5

        fun of(firstVisits: List<FirstVisit>): VisitPublicationPlan {
            return when {
                firstVisits.isEmpty() -> None
                firstVisits.size <= BACKFILL_THRESHOLD -> Individual(firstVisits)
                else -> Backfill(count = firstVisits.size)
            }
        }
    }
}
