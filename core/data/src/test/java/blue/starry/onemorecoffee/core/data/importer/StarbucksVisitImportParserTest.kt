package blue.starry.onemorecoffee.core.data.importer

import blue.starry.onemorecoffee.core.domain.model.VisitSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class StarbucksVisitImportParserTest {
    @Test
    fun parse_deduplicatesSameFirstAndLastDate() {
        val result = StarbucksVisitImportParser.parse(
            """
            [
              {
                "store_id": "1369",
                "first_visit_date": "2026-05-09T10:00:00+09:00",
                "last_visit_date": "2026-05-09T20:00:00+09:00"
              }
            ]
            """.trimIndent(),
        )

        assertThat(result.rawCount).isEqualTo(1)
        assertThat(result.failed).isEqualTo(0)
        assertThat(result.visits).hasSize(1)
        assertThat(result.visits.single().storeId).isEqualTo("1369")
        assertThat(result.visits.single().visitedOn).isEqualTo(LocalDate.of(2026, 5, 9))
        assertThat(result.visits.single().source).isEqualTo(VisitSource.IMPORTED_STARBUCKS)
    }

    @Test
    fun parse_createsTwoVisitsForDifferentFirstAndLastDates() {
        val result = StarbucksVisitImportParser.parse(
            """
            [
              {
                "store_id": "2048",
                "first_visit_date": "2026-05-01T10:00:00+09:00",
                "last_visit_date": "2026-05-09T20:00:00+09:00"
              }
            ]
            """.trimIndent(),
        )

        assertThat(result.failed).isEqualTo(0)
        assertThat(result.visits.map { it.visitedOn }).containsExactly(
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 9),
        )
    }

    @Test
    fun parse_ignoresUnknownFields() {
        val result = StarbucksVisitImportParser.parse(
            """
            [
              {
                "store_id": "3000",
                "last_visit_date": "2026-05-09",
                "unknown": { "nested": true }
              }
            ]
            """.trimIndent(),
        )

        assertThat(result.rawCount).isEqualTo(1)
        assertThat(result.failed).isEqualTo(0)
        assertThat(result.visits.map { it.visitedOn }).containsExactly(LocalDate.of(2026, 5, 9))
    }

    @Test
    fun parse_usesPresentDateWhenOtherDateIsBlankOrMissing() {
        val result = StarbucksVisitImportParser.parse(
            """
            [
              {
                "store_id": "4000",
                "first_visit_date": "",
                "last_visit_date": "2026-05-09T20:00:00+09:00"
              },
              {
                "store_id": "4001",
                "first_visit_date": "2026-05-10T10:00:00+09:00",
                "last_visit_date": "   "
              },
              {
                "store_id": "4002"
              }
            ]
            """.trimIndent(),
        )

        assertThat(result.rawCount).isEqualTo(3)
        assertThat(result.failed).isEqualTo(1)
        assertThat(result.visits.map { it.storeId to it.visitedOn }).containsExactly(
            "4000" to LocalDate.of(2026, 5, 9),
            "4001" to LocalDate.of(2026, 5, 10),
        )
    }

    @Test
    fun parse_keepsValidRecordsWhenAnotherRecordHasInvalidDateAndIncrementsFailed() {
        val result = StarbucksVisitImportParser.parse(
            """
            [
              {
                "store_id": "5000",
                "first_visit_date": "not-a-date",
                "last_visit_date": "2026-05-09T20:00:00+09:00"
              },
              {
                "store_id": "5001",
                "first_visit_date": "2026-05-10T10:00:00+09:00"
              }
            ]
            """.trimIndent(),
        )

        assertThat(result.rawCount).isEqualTo(2)
        assertThat(result.failed).isEqualTo(1)
        assertThat(result.visits.map { it.storeId to it.visitedOn }).containsExactly(
            "5000" to LocalDate.of(2026, 5, 9),
            "5001" to LocalDate.of(2026, 5, 10),
        )
    }

    @Test
    fun parse_skipsBlankStoreIdAndIncrementsFailed() {
        val result = StarbucksVisitImportParser.parse(
            """
            [
              {
                "store_id": "   ",
                "first_visit_date": "2026-05-09T10:00:00+09:00"
              },
              {
                "store_id": "6000",
                "first_visit_date": "2026-05-10T10:00:00+09:00"
              }
            ]
            """.trimIndent(),
        )

        assertThat(result.rawCount).isEqualTo(2)
        assertThat(result.failed).isEqualTo(1)
        assertThat(result.visits.map { it.storeId to it.visitedOn }).containsExactly(
            "6000" to LocalDate.of(2026, 5, 10),
        )
    }
}
