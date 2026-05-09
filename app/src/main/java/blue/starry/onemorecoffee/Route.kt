package blue.starry.onemorecoffee

enum class Route(
    val label: String,
) {
    Map("マップ"),
    List("リスト"),
    Stats("統計"),
    Settings("設定"),
    ;

    companion object {
        val bottomTabs = entries.toList()
    }
}
