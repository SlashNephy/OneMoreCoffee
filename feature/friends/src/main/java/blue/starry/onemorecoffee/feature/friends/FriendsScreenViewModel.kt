package blue.starry.onemorecoffee.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FriendsScreenViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
) : ViewModel() {
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    val uiState: StateFlow<FriendsUiState> = socialRepository.observeSession()
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(FriendsUiState.NotJoined)
            } else {
                combine(
                    socialRepository.observeLeague(),
                    socialRepository.observeMembers(),
                    socialRepository.observeActivities(),
                ) { league, members, activities ->
                    FriendsUiState.Joined(
                        league = league,
                        ranking = ranking(members),
                        feed = activities.map { event ->
                            FeedItem(event = event, member = members.find { member -> member.uid == event.uid })
                        },
                        myUid = session.uid,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FriendsUiState.Loading,
        )

    init {
        // 参加済みなら画面表示時に自分の統計を最新化する（インポートを介さない訪問記録の反映漏れ対策）
        viewModelScope.launch {
            socialRepository.observeSession().filterNotNull().first()
            try {
                socialRepository.refreshOwnStats()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // 統計の更新失敗は画面表示を妨げない
            }
        }
    }

    fun createLeague(leagueName: String, displayName: String, emoji: String) {
        mutate { socialRepository.createLeague(leagueName, SocialProfile(displayName = displayName, emoji = emoji)) }
    }

    fun joinLeague(inviteCode: String, displayName: String, emoji: String) {
        mutate { socialRepository.joinLeague(inviteCode, SocialProfile(displayName = displayName, emoji = emoji)) }
    }

    fun leaveLeague() {
        mutate { socialRepository.leaveLeague() }
    }

    fun consumeError() {
        _errorMessage.value = null
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                block()
            } catch (error: Throwable) {
                // コルーチンのキャンセルは失敗ではないため再送出する（既存 ViewModel の規約に合わせる）
                if (error is CancellationException) throw error
                _errorMessage.value = error.message ?: "処理に失敗しました"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun ranking(members: List<LeagueMember>): List<RankingEntry> {
        return members
            .sortedWith(
                compareByDescending(LeagueMember::visitedStoreCount)
                    .thenBy(LeagueMember::displayName),
            )
            .mapIndexed { index, member -> RankingEntry(rank = index + 1, member = member) }
    }
}
