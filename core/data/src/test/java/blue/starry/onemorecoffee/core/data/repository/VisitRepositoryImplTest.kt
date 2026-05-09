package blue.starry.onemorecoffee.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import blue.starry.onemorecoffee.core.data.database.OneMoreCoffeeDatabase
import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
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
}
