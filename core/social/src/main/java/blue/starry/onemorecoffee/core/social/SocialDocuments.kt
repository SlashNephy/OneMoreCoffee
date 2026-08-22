package blue.starry.onemorecoffee.core.social

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialStats
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.time.Instant
import java.time.LocalDate

// Firestore ドキュメント (Map) とドメインモデルの相互変換。
// Firestore SDK の toObject() は使わず明示的に詰め替える（unit test しやすく、スキーマ変更に気付きやすい）。
object SocialDocuments {
    private const val TYPE_VISIT = "VISIT"
    private const val TYPE_BACKFILL = "BACKFILL"
    private const val TYPE_MEMBER_JOINED = "MEMBER_JOINED"

    fun visitActivityId(uid: String, storeId: String): String = "${uid}_$storeId"

    fun backfillActivityId(uid: String, count: Int): String = "${uid}_backfill_$count"

    fun memberJoinedActivityId(uid: String): String = "${uid}_joined"

    fun visitDocument(
        uid: String,
        storeId: String,
        storeName: String,
        prefecture: String,
        visitedOn: LocalDate,
    ): Map<String, Any> {
        return mapOf(
            "uid" to uid,
            "type" to TYPE_VISIT,
            "storeId" to storeId,
            "storeName" to storeName,
            "prefecture" to prefecture,
            "visitedOn" to visitedOn.toString(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
    }

    fun backfillDocument(uid: String, count: Int): Map<String, Any> {
        return mapOf(
            "uid" to uid,
            "type" to TYPE_BACKFILL,
            "count" to count.toLong(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
    }

    fun memberJoinedDocument(uid: String): Map<String, Any> {
        return mapOf(
            "uid" to uid,
            "type" to TYPE_MEMBER_JOINED,
            "createdAt" to FieldValue.serverTimestamp(),
        )
    }

    fun memberDocument(profile: SocialProfile, stats: SocialStats): Map<String, Any> {
        return mapOf(
            "displayName" to profile.displayName,
            "emoji" to profile.emoji,
            "joinedAt" to FieldValue.serverTimestamp(),
            "stats" to statsMap(stats),
        )
    }

    fun statsUpdate(stats: SocialStats): Map<String, Any> {
        return mapOf("stats" to statsMap(stats))
    }

    private fun statsMap(stats: SocialStats): Map<String, Any> {
        return mapOf(
            "visitedStoreCount" to stats.visitedStoreCount.toLong(),
            "prefectureCount" to stats.prefectureCount.toLong(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
    }

    fun toActivityEvent(id: String, data: Map<String, Any?>): ActivityEvent? {
        val uid = data["uid"] as? String ?: return null
        val createdAt = (data["createdAt"] as? Timestamp)?.toDate()?.toInstant() ?: Instant.EPOCH

        return when (data["type"]) {
            TYPE_VISIT -> ActivityEvent.Visit(
                id = id,
                uid = uid,
                createdAt = createdAt,
                storeId = data["storeId"] as? String ?: return null,
                storeName = data["storeName"] as? String ?: return null,
                prefecture = data["prefecture"] as? String ?: "",
                visitedOn = (data["visitedOn"] as? String)
                    ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
                    ?: return null,
            )
            TYPE_BACKFILL -> ActivityEvent.Backfill(
                id = id,
                uid = uid,
                createdAt = createdAt,
                count = (data["count"] as? Long)?.toInt() ?: 0,
            )
            TYPE_MEMBER_JOINED -> ActivityEvent.MemberJoined(
                id = id,
                uid = uid,
                createdAt = createdAt,
            )
            // 未知の type は将来のイベント種別（前方互換のため無視）
            else -> null
        }
    }

    fun toLeagueMember(uid: String, data: Map<String, Any?>): LeagueMember {
        @Suppress("UNCHECKED_CAST")
        val stats = data["stats"] as? Map<String, Any?> ?: emptyMap()

        return LeagueMember(
            uid = uid,
            displayName = data["displayName"] as? String ?: "（名前未設定）",
            emoji = data["emoji"] as? String ?: "☕",
            visitedStoreCount = (stats["visitedStoreCount"] as? Long)?.toInt() ?: 0,
            prefectureCount = (stats["prefectureCount"] as? Long)?.toInt() ?: 0,
            updatedAt = (stats["updatedAt"] as? Timestamp)?.toDate()?.toInstant(),
        )
    }
}
