package blue.starry.onemorecoffee.core.social

import android.content.Context
import android.util.Log
import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.FirstVisit
import blue.starry.onemorecoffee.core.domain.model.League
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialSession
import blue.starry.onemorecoffee.core.domain.model.SocialStats
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.model.VisitPublicationPlan
import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import blue.starry.onemorecoffee.core.domain.repository.SocialUnavailableException
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreSocialRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStore: SocialSessionStore,
    private val storeRepository: StoreRepository,
) : SocialRepository {
    override fun observeSession(): Flow<SocialSession?> {
        return sessionStore.leagueId.map { leagueId ->
            // uid は匿名サインイン時に確定し、その後は変化しない前提（リーグ参加前に必ずサインインする）
            val uid = if (isAvailable()) FirebaseAuth.getInstance().currentUser?.uid else null

            if (leagueId != null && uid != null) SocialSession(uid = uid, leagueId = leagueId) else null
        }
    }

    override fun observeLeague(): Flow<League?> {
        return observeInLeague(noLeague = null) { leagueId ->
            callbackFlow {
                val registration = firestore().collection("leagues").document(leagueId)
                    .addSnapshotListener { snapshot, _ ->
                        trySend(snapshot?.takeIf(DocumentSnapshot::exists)?.let(::toLeague))
                    }
                awaitClose { registration.remove() }
            }
        }
    }

    override fun observeMembers(): Flow<List<LeagueMember>> {
        return observeInLeague(noLeague = emptyList<LeagueMember>()) { leagueId ->
            callbackFlow {
                val registration = firestore().collection("leagues").document(leagueId).collection("members")
                    .addSnapshotListener { snapshot, _ ->
                        val members = snapshot?.documents.orEmpty().map { document ->
                            SocialDocuments.toLeagueMember(uid = document.id, data = document.estimatedData())
                        }
                        trySend(members)
                    }
                awaitClose { registration.remove() }
            }
        }.map { members -> members.orEmpty() }
    }

    override fun observeActivities(): Flow<List<ActivityEvent>> {
        return observeInLeague(noLeague = emptyList<ActivityEvent>()) { leagueId ->
            callbackFlow {
                val registration = firestore().collection("leagues").document(leagueId).collection("activities")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .addSnapshotListener { snapshot, _ ->
                        val events = snapshot?.documents.orEmpty().mapNotNull { document ->
                            SocialDocuments.toActivityEvent(id = document.id, data = document.estimatedData())
                        }
                        trySend(events)
                    }
                awaitClose { registration.remove() }
            }
        }.map { events -> events.orEmpty() }
    }

    override suspend fun createLeague(leagueName: String, profile: SocialProfile): League {
        val uid = signInAnonymously()
        val firestore = firestore()
        val leagueRef = firestore.collection("leagues").document()
        val inviteCode = InviteCode.generate()
        val stats = currentStats()

        // セキュリティルールの isMember は既存の member doc を参照するため、
        // member doc を作るバッチと activities を作るバッチの 2 段階に分ける
        firestore.batch().apply {
            set(firestore.collection("inviteCodes").document(inviteCode), mapOf("leagueId" to leagueRef.id))
            set(
                leagueRef,
                mapOf(
                    "name" to leagueName,
                    "inviteCode" to inviteCode,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            set(leagueRef.collection("members").document(uid), SocialDocuments.memberDocument(profile, stats))
        }.commit().await()

        publishJoinActivities(leagueId = leagueRef.id, uid = uid, stats = stats)
        sessionStore.save(leagueId = leagueRef.id)

        return League(id = leagueRef.id, name = leagueName, inviteCode = inviteCode, createdBy = uid)
    }

    override suspend fun joinLeague(inviteCode: String, profile: SocialProfile): League {
        val uid = signInAnonymously()
        val firestore = firestore()
        val normalizedCode = inviteCode.trim().uppercase()
        val codeSnapshot = firestore.collection("inviteCodes").document(normalizedCode).get().await()
        val leagueId = codeSnapshot.getString("leagueId")
            ?: throw IllegalArgumentException("招待コードが見つかりません")
        val stats = currentStats()

        firestore.collection("leagues").document(leagueId).collection("members").document(uid)
            .set(SocialDocuments.memberDocument(profile, stats))
            .await()

        publishJoinActivities(leagueId = leagueId, uid = uid, stats = stats)
        sessionStore.save(leagueId = leagueId)

        val leagueSnapshot = firestore.collection("leagues").document(leagueId).get().await()

        return toLeague(leagueSnapshot)
    }

    override suspend fun leaveLeague() {
        val session = observeSession().first() ?: return
        val firestore = firestore()
        val leagueRef = firestore.collection("leagues").document(session.leagueId)
        val ownActivities = leagueRef.collection("activities")
            .whereEqualTo("uid", session.uid)
            .get()
            .await()

        // Firestore の 1 バッチ上限（500 書き込み）を超えないよう分割して削除する
        ownActivities.documents.chunked(MAX_BATCH_WRITES).forEach { chunk ->
            firestore.batch().apply {
                chunk.forEach { document -> delete(document.reference) }
            }.commit().await()
        }

        leagueRef.collection("members").document(session.uid).delete().await()
        sessionStore.clear()
    }

    override suspend fun publishFirstVisits(firstVisits: List<FirstVisit>) {
        try {
            val session = observeSession().first() ?: return
            val summaries = storeRepository.observeStoreSummaries().first()
            val summariesById = summaries.associateBy(StoreVisitSummary::id)
            val stats = SocialStats.from(summaries)
            val firestore = firestore()
            val activities = firestore.collection("leagues").document(session.leagueId).collection("activities")
            val members = firestore.collection("leagues").document(session.leagueId).collection("members")

            val batch = firestore.batch()

            when (val plan = VisitPublicationPlan.of(firstVisits)) {
                VisitPublicationPlan.None -> Unit
                is VisitPublicationPlan.Individual -> {
                    plan.visits.forEach { visit ->
                        val store = summariesById[visit.storeId] ?: return@forEach

                        batch.set(
                            activities.document(SocialDocuments.visitActivityId(uid = session.uid, storeId = visit.storeId)),
                            SocialDocuments.visitDocument(
                                uid = session.uid,
                                storeId = visit.storeId,
                                storeName = store.name,
                                prefecture = store.prefecture,
                                visitedOn = visit.visitedOn,
                            ),
                        )
                    }
                }
                is VisitPublicationPlan.Backfill -> {
                    batch.set(
                        activities.document(SocialDocuments.backfillActivityId(uid = session.uid, count = plan.count)),
                        SocialDocuments.backfillDocument(uid = session.uid, count = plan.count),
                    )
                }
            }

            batch.set(members.document(session.uid), SocialDocuments.statsUpdate(stats), SetOptions.merge())
            // await しない: オフラインでもローカルキューに積まれ、再接続時に自動送信される
            batch.commit()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Contract: a publish failure must not fail visit recording (see SocialRepository KDoc)
            Log.w(TAG, "Failed to publish first visits to the league", e)
        }
    }

    override suspend fun refreshOwnStats() {
        val session = observeSession().first() ?: return
        val stats = currentStats()

        firestore().collection("leagues").document(session.leagueId)
            .collection("members").document(session.uid)
            .set(SocialDocuments.statsUpdate(stats), SetOptions.merge())
            .await()
    }

    private fun publishJoinActivities(leagueId: String, uid: String, stats: SocialStats) {
        val activities = firestore().collection("leagues").document(leagueId).collection("activities")
        val batch = firestore().batch()

        batch.set(
            activities.document(SocialDocuments.memberJoinedActivityId(uid)),
            SocialDocuments.memberJoinedDocument(uid),
        )

        if (stats.visitedStoreCount > 0) {
            batch.set(
                activities.document(SocialDocuments.backfillActivityId(uid = uid, count = stats.visitedStoreCount)),
                SocialDocuments.backfillDocument(uid = uid, count = stats.visitedStoreCount),
            )
        }

        batch.commit()
    }

    private suspend fun currentStats(): SocialStats {
        return SocialStats.from(storeRepository.observeStoreSummaries().first())
    }

    private suspend fun signInAnonymously(): String {
        if (!isAvailable()) throw SocialUnavailableException()

        val auth = FirebaseAuth.getInstance()

        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
    }

    // 未参加（または Firebase 未構成）の間は noLeague を流し、参加中はリーグ配下のリスナーに切り替える
    private fun <T> observeInLeague(noLeague: T?, block: (leagueId: String) -> Flow<T?>): Flow<T?> {
        return sessionStore.leagueId.flatMapLatest { leagueId ->
            if (leagueId == null || !isAvailable()) flowOf(noLeague) else block(leagueId)
        }
    }

    private fun toLeague(snapshot: DocumentSnapshot): League {
        return League(
            id = snapshot.id,
            name = snapshot.getString("name") ?: "",
            inviteCode = snapshot.getString("inviteCode") ?: "",
            createdBy = snapshot.getString("createdBy") ?: "",
        )
    }

    // serverTimestamp が未確定のローカルエコーでは推定値を使い、createdAt が null にならないようにする
    private fun DocumentSnapshot.estimatedData(): Map<String, Any?> {
        return getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).orEmpty()
    }

    private fun firestore(): FirebaseFirestore {
        if (!isAvailable()) throw SocialUnavailableException()

        return FirebaseFirestore.getInstance()
    }

    private fun isAvailable(): Boolean = FirebaseInitializer.isAvailable(context)

    private companion object {
        private const val TAG = "FirestoreSocialRepo"

        // Firestore の 1 バッチ上限は 500 書き込み。余裕を持たせて分割する。
        private const val MAX_BATCH_WRITES = 450
    }
}
