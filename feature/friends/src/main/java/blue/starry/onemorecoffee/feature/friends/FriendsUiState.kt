package blue.starry.onemorecoffee.feature.friends

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.League
import blue.starry.onemorecoffee.core.domain.model.LeagueMember

sealed interface FriendsUiState {
    data object Loading : FriendsUiState

    data object NotJoined : FriendsUiState

    data class Joined(
        val league: League?,
        val ranking: List<RankingEntry>,
        val feed: List<FeedItem>,
        val myUid: String,
    ) : FriendsUiState
}

data class RankingEntry(
    val rank: Int,
    val member: LeagueMember,
)

data class FeedItem(
    val event: ActivityEvent,
    // 退会済みメンバーのイベントは member が null になる
    val member: LeagueMember?,
)
