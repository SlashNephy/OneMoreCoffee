package blue.starry.onemorecoffee.core.social

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialStats
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class SocialDocumentsTest {
    private val createdAt = Timestamp(1_751_900_000, 0)

    @Test
    fun toActivityEvent_parsesVisit() {
        val data = mapOf(
            "uid" to "user1",
            "type" to "VISIT",
            "storeId" to "1783",
            "storeName" to "丸の内オアゾ店",
            "prefecture" to "東京都",
            "visitedOn" to "2026-07-05",
            "createdAt" to createdAt,
        )

        val event = SocialDocuments.toActivityEvent(id = "user1_1783", data = data)

        assertThat(event).isEqualTo(
            ActivityEvent.Visit(
                id = "user1_1783",
                uid = "user1",
                createdAt = Instant.ofEpochSecond(1_751_900_000),
                storeId = "1783",
                storeName = "丸の内オアゾ店",
                prefecture = "東京都",
                visitedOn = LocalDate.of(2026, 7, 5),
            ),
        )
    }

    @Test
    fun toActivityEvent_parsesBackfillAndMemberJoined() {
        val backfill = SocialDocuments.toActivityEvent(
            id = "user1_backfill_287",
            data = mapOf("uid" to "user1", "type" to "BACKFILL", "count" to 287L, "createdAt" to createdAt),
        )
        val joined = SocialDocuments.toActivityEvent(
            id = "user1_joined",
            data = mapOf("uid" to "user1", "type" to "MEMBER_JOINED", "createdAt" to createdAt),
        )

        assertThat(backfill).isEqualTo(
            ActivityEvent.Backfill(
                id = "user1_backfill_287",
                uid = "user1",
                createdAt = Instant.ofEpochSecond(1_751_900_000),
                count = 287,
            ),
        )
        assertThat(joined).isEqualTo(
            ActivityEvent.MemberJoined(
                id = "user1_joined",
                uid = "user1",
                createdAt = Instant.ofEpochSecond(1_751_900_000),
            ),
        )
    }

    @Test
    fun toActivityEvent_unknownType_returnsNull() {
        val event = SocialDocuments.toActivityEvent(
            id = "x",
            data = mapOf("uid" to "user1", "type" to "REACTION", "createdAt" to createdAt),
        )

        assertThat(event).isNull()
    }

    @Test
    fun visitDocument_roundTripsThroughToActivityEvent() {
        val document = SocialDocuments.visitDocument(
            uid = "user1",
            storeId = "1783",
            storeName = "丸の内オアゾ店",
            prefecture = "東京都",
            visitedOn = LocalDate.of(2026, 7, 5),
        )
        // createdAt は FieldValue.serverTimestamp() なのでサーバー付与値に置き換えて往復を確認する
        val event = SocialDocuments.toActivityEvent(
            id = SocialDocuments.visitActivityId(uid = "user1", storeId = "1783"),
            data = document + mapOf("createdAt" to createdAt),
        )

        assertThat(event).isInstanceOf(ActivityEvent.Visit::class.java)
        assertThat((event as ActivityEvent.Visit).storeName).isEqualTo("丸の内オアゾ店")
    }

    @Test
    fun toLeagueMember_parsesStatsWithDefaults() {
        val member = SocialDocuments.toLeagueMember(
            uid = "user1",
            data = mapOf(
                "displayName" to "ねぴ",
                "emoji" to "☕",
                "stats" to mapOf(
                    "visitedStoreCount" to 42L,
                    "prefectureCount" to 3L,
                    "updatedAt" to createdAt,
                ),
            ),
        )

        assertThat(member.displayName).isEqualTo("ねぴ")
        assertThat(member.visitedStoreCount).isEqualTo(42)
        assertThat(member.prefectureCount).isEqualTo(3)
        assertThat(member.updatedAt).isEqualTo(Instant.ofEpochSecond(1_751_900_000))
    }

    @Test
    fun memberDocument_containsProfileAndStats() {
        val document = SocialDocuments.memberDocument(
            profile = SocialProfile(displayName = "ねぴ", emoji = "☕"),
            stats = SocialStats(visitedStoreCount = 42, prefectureCount = 3),
        )

        val member = SocialDocuments.toLeagueMember(uid = "user1", data = document)

        assertThat(member.displayName).isEqualTo("ねぴ")
        assertThat(member.emoji).isEqualTo("☕")
        assertThat(member.visitedStoreCount).isEqualTo(42)
    }
}
