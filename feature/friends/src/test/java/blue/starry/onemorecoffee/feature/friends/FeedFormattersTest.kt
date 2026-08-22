package blue.starry.onemorecoffee.feature.friends

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class FeedFormattersTest {
    private val now = Instant.parse("2026-07-07T12:00:00Z")

    @Test
    fun formatRelativeTime_coversAllRanges() {
        assertThat(formatRelativeTime(now.minus(Duration.ofSeconds(30)), now)).isEqualTo("たった今")
        assertThat(formatRelativeTime(now.minus(Duration.ofMinutes(5)), now)).isEqualTo("5 分前")
        assertThat(formatRelativeTime(now.minus(Duration.ofHours(3)), now)).isEqualTo("3 時間前")
        assertThat(formatRelativeTime(now.minus(Duration.ofDays(2)), now)).isEqualTo("2 日前")
        assertThat(formatRelativeTime(now.minus(Duration.ofDays(10)), now)).isEqualTo("6/27")
    }

    @Test
    fun feedItemText_visit() {
        val event = ActivityEvent.Visit(
            id = "u1_1783",
            uid = "u1",
            createdAt = now,
            storeId = "1783",
            storeName = "丸の内オアゾ店",
            prefecture = "東京都",
            visitedOn = LocalDate.of(2026, 7, 7),
        )

        assertThat(feedItemText(event, memberName = "ねぴ"))
            .isEqualTo("ねぴ さんが 丸の内オアゾ店（東京都）を初訪問")
    }

    @Test
    fun feedItemText_backfillAndJoined() {
        val backfill = ActivityEvent.Backfill(id = "b", uid = "u1", createdAt = now, count = 287)
        val joined = ActivityEvent.MemberJoined(id = "j", uid = "u1", createdAt = now)

        assertThat(feedItemText(backfill, memberName = "ねぴ")).isEqualTo("ねぴ さんが過去の訪問 287 店舗分を登録")
        assertThat(feedItemText(joined, memberName = "ねぴ")).isEqualTo("ねぴ さんがリーグに参加")
    }
}
