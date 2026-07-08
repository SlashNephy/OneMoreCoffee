package blue.starry.onemorecoffee.core.data.repository

import android.util.Log
import android.webkit.CookieManager
import blue.starry.onemorecoffee.core.data.database.dao.StoreDao
import blue.starry.onemorecoffee.core.data.database.dao.VisitDao
import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity
import blue.starry.onemorecoffee.core.data.importer.StarbucksVisitImportParser
import blue.starry.onemorecoffee.core.domain.model.FirstVisit
import blue.starry.onemorecoffee.core.domain.repository.SocialRepository
import blue.starry.onemorecoffee.core.domain.repository.VisitImportResult
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class VisitRepositoryImpl @Inject constructor(
    private val visitDao: VisitDao,
    private val storeDao: StoreDao,
    private val socialRepository: SocialRepository,
) : VisitRepository {
    override suspend fun importStarbucksVisits(json: String): VisitImportResult {
        val parsed = StarbucksVisitImportParser.parse(json)
        val knownStoreIds = storeDao.ids().toSet()
        val previouslyVisitedStoreIds = visitDao.visitedStoreIds().toSet()
        val insertResults = visitDao.insertIgnore(parsed.visits)
        val inserted = insertResults.count { id -> id != -1L }
        val unknownStoreVisits = parsed.visits
            .map { visit -> visit.storeId }
            .filterNot { storeId -> storeId in knownStoreIds }
            .distinct()
            .size

        publishFirstVisits(
            insertedVisits = parsed.visits.zip(insertResults)
                .filter { (_, rowId) -> rowId != -1L }
                .map { (visit, _) -> visit },
            knownStoreIds = knownStoreIds,
            previouslyVisitedStoreIds = previouslyVisitedStoreIds,
        )

        return VisitImportResult(
            inserted = inserted,
            duplicated = parsed.visits.size - inserted,
            unknownStoreVisits = unknownStoreVisits,
            failed = parsed.failed,
        )
    }

    private suspend fun publishFirstVisits(
        insertedVisits: List<VisitEntity>,
        knownStoreIds: Set<String>,
        previouslyVisitedStoreIds: Set<String>,
    ) {
        val firstVisits = insertedVisits
            // マスタ未知の店舗(閉店等)は名前解決できないため公開対象外
            .filter { visit -> visit.storeId in knownStoreIds && visit.storeId !in previouslyVisitedStoreIds }
            .groupBy { visit -> visit.storeId }
            .map { (storeId, visits) ->
                FirstVisit(storeId = storeId, visitedOn = visits.minOf { visit -> visit.visitedOn })
            }

        // ソーシャル公開の失敗はインポート自体の成否に影響させない(コルーチンのキャンセルだけは伝播させる)
        try {
            socialRepository.publishFirstVisits(firstVisits)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VisitRepository", "Failed to publish first visits to social league", e)
        }
    }

    override suspend fun logoutImporter() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
