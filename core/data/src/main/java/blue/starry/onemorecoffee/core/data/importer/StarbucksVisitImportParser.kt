package blue.starry.onemorecoffee.core.data.importer

import blue.starry.onemorecoffee.core.data.database.entity.VisitEntity
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json

data class StarbucksVisitImportParseResult(
    val visits: List<VisitEntity>,
    val rawCount: Int,
    val failed: Int = 0,
)

object StarbucksVisitImportParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(rawJson: String): StarbucksVisitImportParseResult {
        val items = json.decodeFromString<List<StarbucksVisitDto>>(rawJson)
        val visits = mutableListOf<VisitEntity>()
        var failed = 0

        for (item in items) {
            val storeId = item.storeId.trim()
            if (storeId.isBlank()) {
                failed++
                continue
            }

            val firstDate = parseDate(item.firstVisitDate)
            val lastDate = parseDate(item.lastVisitDate)
            val dates = listOfNotNull(firstDate.date, lastDate.date).distinct()

            if (firstDate.failed || lastDate.failed || dates.isEmpty()) {
                failed++
            }

            for (date in dates) {
                visits += VisitEntity(
                    storeId = storeId,
                    visitedOn = date,
                    source = VisitSource.IMPORTED_STARBUCKS,
                )
            }
        }

        return StarbucksVisitImportParseResult(
            visits = visits,
            rawCount = items.size,
            failed = failed,
        )
    }

    private fun parseDate(value: String?): ParsedDate {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            return ParsedDate(date = null, failed = false)
        }

        return try {
            ParsedDate(
                date = LocalDate.parse(normalized.take(10), DateTimeFormatter.ISO_LOCAL_DATE),
                failed = false,
            )
        } catch (_: DateTimeParseException) {
            ParsedDate(date = null, failed = true)
        }
    }

    private data class ParsedDate(
        val date: LocalDate?,
        val failed: Boolean,
    )
}
