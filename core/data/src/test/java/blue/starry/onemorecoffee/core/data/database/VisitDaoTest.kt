package blue.starry.onemorecoffee.core.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VisitDaoTest {
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
    fun insertIgnore_returnsMinusOneForDuplicatedStoreAndDate() = runTest {
        val visit = visit(storeId = "store-1", visitedOn = LocalDate.of(2026, 5, 9))

        val first = database.visitDao().insertIgnore(visit)
        val second = database.visitDao().insertIgnore(visit)

        assertThat(first).isNotEqualTo(-1)
        assertThat(second).isEqualTo(-1)
        assertThat(database.visitDao().count()).isEqualTo(1)
    }

    @Test
    fun insertIgnore_allowsSameStoreOnDifferentDates() = runTest {
        val visits = listOf(
            visit(storeId = "store-1", visitedOn = LocalDate.of(2026, 5, 9)),
            visit(storeId = "store-1", visitedOn = LocalDate.of(2026, 5, 10)),
        )

        val ids = database.visitDao().insertIgnore(visits)

        assertThat(ids).hasSize(2)
        assertThat(ids).doesNotContain(-1)
        assertThat(database.visitDao().count()).isEqualTo(2)
    }

    @Test
    fun insertIgnore_allowsDifferentStoresOnSameDate() = runTest {
        val visits = listOf(
            visit(storeId = "store-1", visitedOn = LocalDate.of(2026, 5, 9)),
            visit(storeId = "store-2", visitedOn = LocalDate.of(2026, 5, 9)),
        )

        val ids = database.visitDao().insertIgnore(visits)

        assertThat(ids).hasSize(2)
        assertThat(ids).doesNotContain(-1)
        assertThat(database.visitDao().count()).isEqualTo(2)
    }

    @Test
    fun visitedStoreIds_returnsDistinctStoreIds() = runTest {
        database.visitDao().insertIgnore(
            listOf(
                visit(storeId = "store-1", visitedOn = LocalDate.of(2026, 7, 1)),
                visit(storeId = "store-1", visitedOn = LocalDate.of(2026, 7, 2)),
                visit(storeId = "store-2", visitedOn = LocalDate.of(2026, 7, 1)),
            ),
        )

        assertThat(database.visitDao().visitedStoreIds()).containsExactly("store-1", "store-2")
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
