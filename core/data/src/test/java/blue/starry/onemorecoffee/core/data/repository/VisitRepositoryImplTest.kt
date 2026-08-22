package blue.starry.onemorecoffee.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import blue.starry.onemorecoffee.core.data.database.OneMoreCoffeeDatabase
import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity
import blue.starry.onemorecoffee.core.domain.model.ActivityEvent
import blue.starry.onemorecoffee.core.domain.model.FirstVisit
import blue.starry.onemorecoffee.core.domain.model.League
import blue.starry.onemorecoffee.core.domain.model.LeagueMember
import blue.starry.onemorecoffee.core.domain.model.SocialProfile
import blue.starry.onemorecoffee.core.domain.model.SocialSession
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VisitRepositoryImplTest {
    private lateinit var database: OneMoreCoffeeDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OneMoreCoffeeDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importStarbucksVisits_insertsVisitsAndReportsDuplicatesUnknownDistinctStoresAndParseFailures() = runTest {
        database.storeDao().upsertAll(listOf(store("known-store")))
        database.visitDao().insertIgnore(
            visit(storeId = "known-store", visitedOn = LocalDate.of(2026, 5, 1)),
        )
        val repository = VisitRepositoryImpl(
            visitDao = database.visitDao(),
            storeDao = database.storeDao(),
            socialRepository = FakeSocialRepository(),
        )

        val result = repository.importStarbucksVisits(
            """
            [
              {
                "store_id": "known-store",
                "first_visit_date": "2026-05-01T10:00:00+09:00",
                "last_visit_date": "2026-05-09T10:00:00+09:00"
              },
              {
                "store_id": "unknown-store",
                "first_visit_date": "2026-05-02T10:00:00+09:00",
                "last_visit_date": "2026-05-10T10:00:00+09:00"
              },
              {
                "store_id": "broken-store",
                "first_visit_date": "not-a-date"
              }
            ]
            """.trimIndent(),
        )

        assertThat(result.inserted).isEqualTo(3)
        assertThat(result.duplicated).isEqualTo(1)
        assertThat(result.unknownStoreVisits).isEqualTo(1)
        assertThat(result.failed).isEqualTo(1)
        assertThat(database.visitDao().count()).isEqualTo(4)
    }

    @Test
    fun importStarbucksVisits_publishesOnlyFirstVisitsOfKnownStores() = runTest {
        database.storeDao().upsertAll(listOf(store("known-store"), store("new-store")))
        database.visitDao().insertIgnore(
            visit(storeId = "known-store", visitedOn = LocalDate.of(2026, 5, 1)),
        )
        val socialRepository = FakeSocialRepository()
        val repository = VisitRepositoryImpl(
            visitDao = database.visitDao(),
            storeDao = database.storeDao(),
            socialRepository = socialRepository,
        )

        repository.importStarbucksVisits(
            """
            [
              {
                "store_id": "known-store",
                "first_visit_date": "2026-05-01T10:00:00+09:00",
                "last_visit_date": "2026-07-01T10:00:00+09:00"
              },
              {
                "store_id": "new-store",
                "first_visit_date": "2026-07-02T10:00:00+09:00",
                "last_visit_date": "2026-07-05T10:00:00+09:00"
              },
              {
                "store_id": "unknown-store",
                "first_visit_date": "2026-07-03T10:00:00+09:00",
                "last_visit_date": "2026-07-03T10:00:00+09:00"
              }
            ]
            """.trimIndent(),
        )

        // 公開対象は「マスタに存在し、今回はじめて訪問済みになった」new-store のみ。
        // known-store は再訪、unknown-store はマスタ未知のため対象外
        assertThat(socialRepository.publishedFirstVisits).hasSize(1)
        val published = socialRepository.publishedFirstVisits.single()
        assertThat(published.map(FirstVisit::storeId)).containsExactly("new-store")
        // 同一店舗で複数の訪問が挿入された場合は最古の訪問日を採用する
        assertThat(published.single().visitedOn).isEqualTo(LocalDate.of(2026, 7, 2))
    }

    @Test
    fun importStarbucksVisits_publishFailure_doesNotAffectImportResult() = runTest {
        database.storeDao().upsertAll(listOf(store("new-store")))
        val socialRepository = FakeSocialRepository().apply { shouldFail = true }
        val repository = VisitRepositoryImpl(
            visitDao = database.visitDao(),
            storeDao = database.storeDao(),
            socialRepository = socialRepository,
        )

        val result = repository.importStarbucksVisits(
            """
            [
              {
                "store_id": "new-store",
                "first_visit_date": "2026-07-02T10:00:00+09:00",
                "last_visit_date": "2026-07-02T10:00:00+09:00"
              }
            ]
            """.trimIndent(),
        )

        assertThat(result.inserted).isEqualTo(1)
        assertThat(result.failed).isEqualTo(0)
    }

    private fun store(id: String): StoreEntity {
        return StoreEntity(
            id = id,
            name = "丸の内店",
            nameEn = null,
            prefCode = "13",
            prefecture = "東京都",
            fullAddress = "東京都千代田区",
            latitude = 35.0,
            longitude = 139.0,
            isReserve = false,
            rawJson = "{}",
            lastSeenAt = Instant.parse("2026-05-09T00:00:00Z"),
        )
    }

    private fun visit(
        storeId: String,
        visitedOn: LocalDate,
    ): VisitEntity {
        return VisitEntity(
            storeId = storeId,
            visitedOn = visitedOn,
            source = VisitSource.IMPORTED_STARBUCKS,
        )
    }

    private class FakeSocialRepository : SocialRepository {
        val publishedFirstVisits = mutableListOf<List<FirstVisit>>()
        var shouldFail = false

        override suspend fun publishFirstVisits(firstVisits: List<FirstVisit>) {
            if (shouldFail) throw IllegalStateException("publish failed")
            publishedFirstVisits.add(firstVisits)
        }

        override fun observeSession(): Flow<SocialSession?> = flowOf(null)
        override fun observeLeague(): Flow<League?> = flowOf(null)
        override fun observeMembers(): Flow<List<LeagueMember>> = flowOf(emptyList())
        override fun observeActivities(): Flow<List<ActivityEvent>> = flowOf(emptyList())
        override suspend fun createLeague(leagueName: String, profile: SocialProfile): League = error("unused")
        override suspend fun joinLeague(inviteCode: String, profile: SocialProfile): League = error("unused")
        override suspend fun leaveLeague() = Unit
        override suspend fun refreshOwnStats() = Unit
    }
}
