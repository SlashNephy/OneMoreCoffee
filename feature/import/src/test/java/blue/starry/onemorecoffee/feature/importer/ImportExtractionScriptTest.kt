package blue.starry.onemorecoffee.feature.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImportExtractionScriptTest {
    @Test
    fun storeAllExtractionScript_waitsForNonEmptyStoreAll() {
        val script = storeAllExtractionScript()

        assertThat(script).contains("storeAll.length === 0")
        assertThat(script).contains("setTimeout")
        assertThat(script).contains("receiveStoreAll(JSON.stringify(storeAll))")
    }
}
