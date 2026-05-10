package blue.starry.onemorecoffee.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import blue.starry.onemorecoffee.core.data.database.OneMoreCoffeeDatabase
import blue.starry.onemorecoffee.core.data.database.entity.StoreEntity
import blue.starry.onemorecoffee.core.data.starbucks.CloudSearchResponse
import blue.starry.onemorecoffee.core.data.starbucks.StarbucksStoreDataSource
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshProgress
import blue.starry.onemorecoffee.core.domain.repository.StoreRefreshResult
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoreRepositoryImplTest {
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
    fun refreshStores_upsertsMappedStoresSkipsInvalidHitsAndDeletesMissingStores() = runTest {
        database.storeDao().upsertAll(
            listOf(
                store(id = "stale-store"),
            ),
        )
        val repository = StoreRepositoryImpl(
            storeDao = database.storeDao(),
            starbucksStoreDataSource = FakeStarbucksStoreDataSource(
                listOf(
                    hit("store-1", location = "35.0,139.0"),
                    hit("invalid-location", location = "not-a-location"),
                    hit("invalid-prefecture", location = "35.0,139.0", prefecture = null),
                ),
            ),
        )

        val result = repository.refreshStores()

        assertThat(result.upserted).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(2)
        assertThat(database.storeDao().observeAll().first().map { it.id }).containsExactly("store-1")
    }

    @Test
    fun refreshStores_deletesAllStoresWhenApiReturnsValidEmptyList() = runTest {
        database.storeDao().upsertAll(
            listOf(
                store(id = "stale-store"),
            ),
        )
        val repository = StoreRepositoryImpl(
            storeDao = database.storeDao(),
            starbucksStoreDataSource = FakeStarbucksStoreDataSource(emptyList()),
        )

        val result = repository.refreshStores()

        assertThat(result.upserted).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(database.storeDao().observeAll().first()).isEmpty()
    }

    @Test
    fun refreshStores_reportsProgressFromFetchingToFinished() = runTest {
        val repository = StoreRepositoryImpl(
            storeDao = database.storeDao(),
            starbucksStoreDataSource = FakeStarbucksStoreDataSource(
                hits = listOf(hit("store-1", location = "35.0,139.0")),
                progressEvents = listOf(StoreRefreshProgress.Fetching(fetched = 1, total = 2)),
            ),
        )
        val progress = mutableListOf<StoreRefreshProgress>()

        repository.refreshStores { event ->
            progress += event
        }

        assertThat(progress).containsExactly(
            StoreRefreshProgress.Connecting,
            StoreRefreshProgress.Fetching(fetched = 1, total = 2),
            StoreRefreshProgress.Saving,
            StoreRefreshProgress.Finished(StoreRefreshResult(upserted = 1, skipped = 0)),
        ).inOrder()
    }

    private fun store(id: String): StoreEntity {
        return StoreEntity(
            id = id,
            name = "古い店舗",
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

    private fun hit(
        id: String,
        location: String,
        prefecture: String? = "東京都",
    ): CloudSearchResponse.Hit {
        val fields = buildMap {
            put("store_id", array(id))
            put("name", array("丸の内店"))
            put("location", array(location))
            put("pref_code", array("13"))
            if (prefecture != null) {
                put("address_1", array(prefecture))
            }
            put("address_2", array("千代田区"))
            put("reserve_flg", array("0"))
        }

        return CloudSearchResponse.Hit(
            id = id,
            fields = JsonObject(fields),
        )
    }

    private fun array(value: String): JsonArray {
        return JsonArray(listOf(JsonPrimitive(value)))
    }

    private class FakeStarbucksStoreDataSource(
        private val hits: List<CloudSearchResponse.Hit>,
        private val progressEvents: List<StoreRefreshProgress.Fetching> = emptyList(),
    ) : StarbucksStoreDataSource {
        override suspend fun fetchAllStores(
            onProgress: (StoreRefreshProgress.Fetching) -> Unit,
        ): List<CloudSearchResponse.Hit> {
            progressEvents.forEach(onProgress)
            return hits
        }
    }
}
