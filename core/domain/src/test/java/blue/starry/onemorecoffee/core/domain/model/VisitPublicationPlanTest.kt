package blue.starry.onemorecoffee.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class VisitPublicationPlanTest {
    @Test
    fun of_emptyList_returnsNone() {
        assertThat(VisitPublicationPlan.of(emptyList())).isEqualTo(VisitPublicationPlan.None)
    }

    @Test
    fun of_atThreshold_returnsIndividual() {
        val visits = (1..5).map { FirstVisit(storeId = "$it", visitedOn = LocalDate.of(2026, 7, 1)) }

        val plan = VisitPublicationPlan.of(visits)

        assertThat(plan).isEqualTo(VisitPublicationPlan.Individual(visits))
    }

    @Test
    fun of_aboveThreshold_returnsBackfillWithCount() {
        val visits = (1..6).map { FirstVisit(storeId = "$it", visitedOn = LocalDate.of(2026, 7, 1)) }

        val plan = VisitPublicationPlan.of(visits)

        assertThat(plan).isEqualTo(VisitPublicationPlan.Backfill(count = 6))
    }
}
