package blue.starry.onemorecoffee.feature.importer

import android.webkit.JavascriptInterface

class StarbucksImportBridge(
    private val onJsonReceived: (String) -> Unit,
) {
    @JavascriptInterface
    fun receiveStoreAll(json: String) {
        onJsonReceived(json)
    }
}
