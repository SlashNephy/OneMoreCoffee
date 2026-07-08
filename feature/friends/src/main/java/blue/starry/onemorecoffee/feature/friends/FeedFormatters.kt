package blue.starry.onemorecoffee.feature.friends

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

// 利用者は全員日本在住のため Asia/Tokyo 固定（設計書 §4.2）
private val zone = ZoneId.of("Asia/Tokyo")

fun formatRelativeTime(createdAt: Instant, now: Instant): String {
    val duration = Duration.between(createdAt, now)

    return when {
        duration.toMinutes() < 1 -> "たった今"
        duration.toHours() < 1 -> "${duration.toMinutes()} 分前"
        duration.toDays() < 1 -> "${duration.toHours()} 時間前"
        duration.toDays() < 7 -> "${duration.toDays()} 日前"
        else -> {
            val date = createdAt.atZone(zone).toLocalDate()
            "${date.monthValue}/${date.dayOfMonth}"
        }
    }
}

fun feedItemText(event: ActivityEvent, memberName: String): String {
    return when (event) {
        is ActivityEvent.Visit -> "$memberName さんが ${event.storeName}（${event.prefecture}）を初訪問"
        is ActivityEvent.Backfill -> "$memberName さんが過去の訪問 ${event.count} 店舗分を登録"
        is ActivityEvent.MemberJoined -> "$memberName さんがリーグに参加"
    }
}
