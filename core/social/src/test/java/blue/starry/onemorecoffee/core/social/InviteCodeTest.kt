package blue.starry.onemorecoffee.core.social

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InviteCodeTest {
    @Test
    fun generate_returnsEightCharactersFromUnambiguousAlphabet() {
        repeat(100) {
            val code = InviteCode.generate()

            assertThat(code).hasLength(8)
            assertThat(code.all { it in InviteCode.ALPHABET }).isTrue()
        }
    }

    @Test
    fun alphabet_excludesAmbiguousCharacters() {
        for (ambiguous in listOf('0', 'O', '1', 'I', 'L')) {
            assertThat(InviteCode.ALPHABET).doesNotContain(ambiguous.toString())
        }
    }
}
