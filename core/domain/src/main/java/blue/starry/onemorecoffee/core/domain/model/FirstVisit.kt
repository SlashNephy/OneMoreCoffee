package blue.starry.onemorecoffee.core.domain.model

import java.time.LocalDate

// このインポート/記録で「はじめて訪問済みになった」店舗。ソーシャル公開の入力になる。
data class FirstVisit(
    val storeId: String,
    val visitedOn: LocalDate,
)
