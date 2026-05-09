package blue.starry.onemorecoffee.core.domain.repository

interface VisitRepository {
    suspend fun importStarbucksVisits(json: String): VisitImportResult

    suspend fun logoutImporter()
}

data class VisitImportResult(
    val inserted: Int,
    val duplicated: Int,
    val unknownStoreVisits: Int,
    val failed: Int,
)
