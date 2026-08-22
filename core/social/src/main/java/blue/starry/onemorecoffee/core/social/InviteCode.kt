package blue.starry.onemorecoffee.core.social

import kotlin.random.Random

// 読み間違えやすい 0/O/1/I/L を除いた 8 文字コード。
// 数人規模なので衝突確率（31^8 ≒ 8500 億分の N）は無視する。
object InviteCode {
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    fun generate(): String {
        return buildString {
            repeat(8) {
                append(ALPHABET[Random.nextInt(ALPHABET.length)])
            }
        }
    }
}
