package blue.starry.onemorecoffee.feature.friends

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.FirstVisit
import blue.starry.onemorecoffee.core.domain.model.League
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialSession
import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSocialRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSocialRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_noSession_isNotJoined() = runTest {
        val viewModel = FriendsScreenViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(FriendsUiState.NotJoined)
    }

    @Test
    fun uiState_joined_sortsRankingByVisitedCountThenName() = runTest {
        repository.session.value = SocialSession(uid = "me", leagueId = "league1")
        repository.members.value = listOf(
            member(uid = "a", name = "あかり", visited = 10),
            member(uid = "b", name = "いろは", visited = 42),
            member(uid = "c", name = "うみ", visited = 10),
        )
        val viewModel = FriendsScreenViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as FriendsUiState.Joined
        assertThat(state.ranking.map { it.member.uid }).containsExactly("b", "a", "c").inOrder()
        assertThat(state.ranking.map { it.rank }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun uiState_joined_attachesMemberToFeedItem() = runTest {
        repository.session.value = SocialSession(uid = "me", leagueId = "league1")
        repository.members.value = listOf(member(uid = "a", name = "あかり", visited = 1))
        repository.activities.value = listOf(
            ActivityEvent.MemberJoined(id = "a_joined", uid = "a", createdAt = Instant.EPOCH),
            ActivityEvent.MemberJoined(id = "x_joined", uid = "gone", createdAt = Instant.EPOCH),
        )
        val viewModel = FriendsScreenViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value as FriendsUiState.Joined
        assertThat(state.feed[0].member?.displayName).isEqualTo("あかり")
        assertThat(state.feed[1].member).isNull()
    }

    @Test
    fun createLeague_failure_exposesErrorMessage() = runTest {
        repository.shouldFailMutations = true
        val viewModel = FriendsScreenViewModel(repository)

        viewModel.createLeague(leagueName = "スタバ部", displayName = "ねぴ", emoji = "☕")
        advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isNotNull()

        viewModel.consumeError()

        assertThat(viewModel.errorMessage.value).isNull()
    }

    private fun member(uid: String, name: String, visited: Int): LeagueMember {
        return LeagueMember(
            uid = uid,
            displayName = name,
            emoji = "☕",
            visitedStoreCount = visited,
            prefectureCount = 1,
            updatedAt = null,
        )
    }

    private class FakeSocialRepository : SocialRepository {
        val session = MutableStateFlow<SocialSession?>(null)
        val league = MutableStateFlow<League?>(null)
        val members = MutableStateFlow(emptyList<LeagueMember>())
        val activities = MutableStateFlow(emptyList<ActivityEvent>())
        var shouldFailMutations = false

        override fun observeSession(): Flow<SocialSession?> = session
        override fun observeLeague(): Flow<League?> = league
        override fun observeMembers(): Flow<List<LeagueMember>> = members
        override fun observeActivities(): Flow<List<ActivityEvent>> = activities

        override suspend fun createLeague(leagueName: String, profile: SocialProfile): League {
            if (shouldFailMutations) throw IllegalStateException("failed")
            return League(id = "new", name = leagueName, inviteCode = "AAAA2222", createdBy = "me")
        }

        override suspend fun joinLeague(inviteCode: String, profile: SocialProfile): League {
            if (shouldFailMutations) throw IllegalStateException("failed")
            return League(id = "joined", name = "スタバ部", inviteCode = inviteCode, createdBy = "other")
        }

        override suspend fun leaveLeague() {
            if (shouldFailMutations) throw IllegalStateException("failed")
            session.value = null
        }

        override suspend fun publishFirstVisits(firstVisits: List<FirstVisit>) = Unit
        override suspend fun refreshOwnStats() = Unit
    }
}
