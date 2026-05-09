package blue.starry.onemorecoffee.core.data.repository

import android.webkit.CookieManager
import blue.starry.onemorecoffee.core.data.database.dao.StoreDao
import blue.starry.onemorecoffee.core.data.database.dao.VisitDao
import blue.starry.onemorecoffee.core.data.importer.StarbucksVisitImportParser
import blue.starry.onemorecoffee.core.domain.repository.VisitImportResult
import blue.starry.onemorecoffee.core.domain.repository.VisitRepository
import javax.inject.Inject

class VisitRepositoryImpl @Inject constructor(
    private val visitDao: VisitDao,
    private val storeDao: StoreDao,
) : VisitRepository {
    override suspend fun importStarbucksVisits(json: String): VisitImportResult {
        val parsed = StarbucksVisitImportParser.parse(json)
        val knownStoreIds = storeDao.ids().toSet()
        val insertResults = visitDao.insertIgnore(parsed.visits)
        val inserted = insertResults.count { id -> id != -1L }
        val unknownStoreVisits = parsed.visits
            .map { visit -> visit.storeId }
            .filterNot { storeId -> storeId in knownStoreIds }
            .distinct()
            .size

        return VisitImportResult(
            inserted = inserted,
            duplicated = parsed.visits.size - inserted,
            unknownStoreVisits = unknownStoreVisits,
            failed = parsed.failed,
        )
    }

    override suspend fun logoutImporter() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
