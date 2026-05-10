package blue.starry.onemorecoffee.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapClusterLabelTest {
    @Test
    fun buildClusterLabel_formatsExactCountBelowTen() {
        assertThat(buildClusterLabel(totalCount = 2, visitedCount = 0)).isEqualTo("2")
        assertThat(buildClusterLabel(totalCount = 2, visitedCount = 2)).isEqualTo("2 (2)")
    }

    @Test
    fun buildClusterLabel_bucketsCountByTen() {
        assertThat(buildClusterLabel(totalCount = 10, visitedCount = 0)).isEqualTo("10+")
        assertThat(buildClusterLabel(totalCount = 19, visitedCount = 3)).isEqualTo("10+ (3)")
        assertThat(buildClusterLabel(totalCount = 20, visitedCount = 14)).isEqualTo("20+ (14)")
        assertThat(buildClusterLabel(totalCount = 29, visitedCount = 14)).isEqualTo("20+ (14)")
    }

    @Test
    fun shouldReleaseClusterAtZoom_releasesFromConfiguredZoom() {
        assertThat(shouldReleaseClusterAtZoom(13.99f)).isFalse()
        assertThat(shouldReleaseClusterAtZoom(14f)).isTrue()
        assertThat(shouldReleaseClusterAtZoom(15f)).isTrue()
    }

    @Test
    fun clusterFillColor_usesRedWhenUnvisitedStoresAreMajority() {
        val defaultColor = clusterFillColor(totalCount = 4, visitedCount = 2)
        val unvisitedMajorityColor = clusterFillColor(totalCount = 5, visitedCount = 2)

        assertThat(unvisitedMajorityColor).isNotEqualTo(defaultColor)
    }
}
