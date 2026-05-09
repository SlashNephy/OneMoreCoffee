package blue.starry.onemorecoffee.core.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoreDaoTest {
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
    fun observeSummaries_returnsStoresWithVisitCountAndLastVisitedOn() = runTest {
        database.storeDao().upsertAll(
            listOf(
                store(id = "tokyo-2", prefCode = "13", name = "B店"),
                store(id = "tokyo-1", prefCode = "13", name = "A店"),
                store(id = "osaka-1", prefCode = "27", name = "C店"),
            ),
        )
        database.visitDao().insertIgnore(
            listOf(
                visit(storeId = "tokyo-1", visitedOn = LocalDate.of(2026, 5, 1)),
                visit(storeId = "tokyo-1", visitedOn = LocalDate.of(2026, 5, 9)),
                visit(storeId = "missing-store", visitedOn = LocalDate.of(2026, 5, 10)),
            ),
        )

        val summaries = database.storeDao().observeSummaries().first()

        assertThat(summaries.map { it.id }).containsExactly("tokyo-1", "tokyo-2", "osaka-1").inOrder()
        assertThat(summaries[0].visitCount).isEqualTo(2)
        assertThat(summaries[0].lastVisitedOn).isEqualTo(LocalDate.of(2026, 5, 9))
        assertThat(summaries[1].visitCount).isEqualTo(0)
        assertThat(summaries[1].lastVisitedOn).isNull()
    }

    private fun store(
        id: String,
        prefCode: String,
        name: String,
    ): StoreEntity {
        return StoreEntity(
            id = id,
            name = name,
            nameEn = null,
            prefCode = prefCode,
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
