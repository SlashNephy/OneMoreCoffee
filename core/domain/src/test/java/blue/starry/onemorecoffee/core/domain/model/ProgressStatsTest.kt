package blue.starry.onemorecoffee.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class ProgressStatsTest {
    @Test
    fun from_returnsProgressForVisitedAndUnvisitedSummaries() {
        val summaries = listOf(
            summary(id = "visited", visitCount = 1),
            summary(id = "unvisited", visitCount = 0),
        )

        val stats = ProgressStats.from(summaries)

        assertThat(stats.totalStores).isEqualTo(2)
        assertThat(stats.visitedStores).isEqualTo(1)
        assertThat(stats.completionRate).isEqualTo(0.5)
    }

    @Test
    fun from_returnsZeroProgressForEmptySummaries() {
        val stats = ProgressStats.from(emptyList())

        assertThat(stats.totalStores).isEqualTo(0)
        assertThat(stats.visitedStores).isEqualTo(0)
        assertThat(stats.completionRate).isEqualTo(0.0)
    }

    @Test
    fun isVisited_isTrueOnlyWhenVisitCountIsPositive() {
        assertThat(summary(id = "positive", visitCount = 1).isVisited).isTrue()
        assertThat(summary(id = "zero", visitCount = 0).isVisited).isFalse()
    }

    private fun summary(
        id: String,
        visitCount: Int,
    ): StoreVisitSummary {
        return StoreVisitSummary(
            id = id,
            name = "Starbucks Coffee",
            prefecture = "Tokyo",
            fullAddress = "Tokyo",
            latitude = 35.0,
            longitude = 139.0,
            isReserve = false,
            visitCount = visitCount,
            lastVisitedOn = if (visitCount > 0) LocalDate.of(2026, 5, 9) else null,
        )
    }
}
