package blue.starry.onemorecoffee.feature.importer

import blue.starry.onemorecoffee.core.domain.repository.VisitImportResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImportResultMessageTest {
    @Test
    fun toImportCompletionMessage_returnsImportedCountWhenInsertedVisitsExist() {
        val result = VisitImportResult(inserted = 3, duplicated = 1, unknownStoreVisits = 2, failed = 0)

        assertThat(result.toImportCompletionMessage()).isEqualTo("3件インポートしました。")
    }

    @Test
    fun toImportCompletionMessage_returnsNoNewVisitsWhenInsertedVisitsAreZero() {
        val result = VisitImportResult(inserted = 0, duplicated = 1, unknownStoreVisits = 2, failed = 0)

        assertThat(result.toImportCompletionMessage()).isEqualTo("新しい訪問履歴はありません。")
    }
}
