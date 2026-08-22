package blue.starry.onemorecoffee.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapMarkerStyleTest {
    @Test
    fun markerStyleFor_treatsVisitedAndReserveAsIndependentAxes() {
        assertThat(markerStyleFor(isVisited = false, isReserve = false))
            .isEqualTo(StoreMarkerStyle(fill = MarkerFill.Hollow, hasReserveBadge = false))
        assertThat(markerStyleFor(isVisited = true, isReserve = false))
            .isEqualTo(StoreMarkerStyle(fill = MarkerFill.Filled, hasReserveBadge = false))
        assertThat(markerStyleFor(isVisited = false, isReserve = true))
            .isEqualTo(StoreMarkerStyle(fill = MarkerFill.Hollow, hasReserveBadge = true))
    }

    @Test
    fun markerStyleFor_keepsReserveBadgeOnVisitedStore() {
        assertThat(markerStyleFor(isVisited = true, isReserve = true))
            .isEqualTo(StoreMarkerStyle(fill = MarkerFill.Filled, hasReserveBadge = true))
    }
}
