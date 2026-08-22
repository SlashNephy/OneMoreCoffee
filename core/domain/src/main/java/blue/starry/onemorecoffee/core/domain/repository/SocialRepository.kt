package blue.starry.onemorecoffee.core.domain.repository

import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.FirstVisit
import blue.starry.onemorecoffee.core.domain.model.League
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialSession
import kotlinx.coroutines.flow.Flow

interface SocialRepository {
    // null = 未参加（またはソーシャル未構成）
    fun observeSession(): Flow<SocialSession?>

    fun observeLeague(): Flow<League?>

    fun observeMembers(): Flow<List<LeagueMember>>

    fun observeActivities(): Flow<List<ActivityEvent>>

    suspend fun createLeague(leagueName: String, profile: SocialProfile): League

    suspend fun joinLeague(inviteCode: String, profile: SocialProfile): League

    suspend fun leaveLeague()

    // インポート等で新たに訪問済みになった店舗を公開する。失敗しても呼び出し元の処理は失敗させない。
    suspend fun publishFirstVisits(firstVisits: List<FirstVisit>)

    suspend fun refreshOwnStats()
}

// Firebase が未構成（secrets 未設定）の端末で参加系操作をしたときに投げる
class SocialUnavailableException : IllegalStateException("Firebase が構成されていません")
