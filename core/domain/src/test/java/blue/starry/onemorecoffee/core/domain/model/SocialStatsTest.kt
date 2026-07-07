package blue.starry.onemorecoffee.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class SocialStatsTest {
    @Test
    fun from_countsVisitedStoresAndDistinctPrefectures() {
        val summaries = listOf(
            summary(id = "1", prefecture = "東京都", visitCount = 2),
            summary(id = "2", prefecture = "東京都", visitCount = 1),
            summary(id = "3", prefecture = "京都府", visitCount = 1),
            summary(id = "4", prefecture = "大阪府", visitCount = 0),
        )

        val stats = SocialStats.from(summaries)

        assertThat(stats.visitedStoreCount).isEqualTo(3)
        assertThat(stats.prefectureCount).isEqualTo(2)
    }

    @Test
    fun from_emptySummaries_returnsZero() {
        val stats = SocialStats.from(emptyList())

        assertThat(stats.visitedStoreCount).isEqualTo(0)
        assertThat(stats.prefectureCount).isEqualTo(0)
    }

    private fun summary(
        id: String,
        prefecture: String,
        visitCount: Int,
    ): StoreVisitSummary {
        return StoreVisitSummary(
            id = id,
            name = "スターバックス",
            prefecture = prefecture,
            fullAddress = "住所",
            latitude = 35.0,
            longitude = 139.0,
            isReserve = false,
            visitCount = visitCount,
            lastVisitedOn = if (visitCount > 0) LocalDate.of(2026, 7, 1) else null,
        )
    }
}
