package blue.starry.onemorecoffee.core.social

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SocialSessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun saveAndObserve_roundTrips() = runTest {
        val store = SocialSessionStore(context)

        assertThat(store.leagueId.first()).isNull()

        store.save(leagueId = "league1")

        assertThat(store.leagueId.first()).isEqualTo("league1")

        store.clear()

        assertThat(store.leagueId.first()).isNull()
    }
}
