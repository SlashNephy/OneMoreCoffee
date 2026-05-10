package blue.starry.onemorecoffee

enum class Route(
    val label: String,
    val iconResId: Int,
) {
    Map("マップ", R.drawable.map_search),
    List("リスト", R.drawable.checklist),
    Stats("統計", R.drawable.summarize),
    Settings("設定", R.drawable.settings),
    ;

    companion object {
        val bottomTabs = entries.toList()
    }
}
