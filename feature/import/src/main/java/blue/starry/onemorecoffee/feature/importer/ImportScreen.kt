package blue.starry.onemorecoffee.feature.importer

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val StarbucksStoreUrl = "https://www.starbucks.co.jp/mystarbucks/mystore/"
private const val BridgeName = "OneMoreCoffee"

private val ExtractStoreAllScript = """
    (function(){ if (window.Stamp && Array.isArray(window.Stamp.store_all)) { window.OneMoreCoffee.receiveStoreAll(JSON.stringify(window.Stamp.store_all)); } })();
""".trimIndent()

@Composable
fun ImportScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onImportCompleted: () -> Unit = onBackClick,
    viewModel: ImportScreenViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.returnToSettingsEvents.collect {
            onImportCompleted()
        }
    }

    ImportContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onJsonReceived = viewModel::importJson,
        modifier = modifier,
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ImportContent(
    uiState: ImportUiState,
    onBackClick: () -> Unit,
    onJsonReceived: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current
    DisposableEffect(Unit) {
        onDispose {
            webView?.removeJavascriptInterface(BridgeName)
            webView?.destroy()
            webView = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBackClick) {
                Text("戻る")
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = "My Starbucks の訪問履歴ページから店舗履歴だけを読み取り、この端末のデータベースへ取り込みます。",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        StatusMessage(uiState = uiState)

        AndroidView(
            factory = {
                WebView(context).apply {
                    val bridge = StarbucksImportBridge { json ->
                        post {
                            onJsonReceived(json)
                        }
                    }
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    addJavascriptInterface(bridge, BridgeName)
                    webViewClient = StarbucksWebViewClient()
                    loadUrl(StarbucksStoreUrl)
                }
            },
            update = {
                if (it.url == null) {
                    it.loadUrl(StarbucksStoreUrl)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun StatusMessage(
    uiState: ImportUiState,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        ImportUiState.Waiting -> Text(
            text = "My Starbucks にログインしてください",
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
        )

        ImportUiState.Importing -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Text(
                text = "訪問履歴をインポートしています",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is ImportUiState.Completed -> Text(
            text = uiState.result.toStatusMessage(),
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
        )

        is ImportUiState.Failed -> Text(
            text = uiState.message,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun blue.starry.onemorecoffee.core.domain.repository.VisitImportResult.toStatusMessage(): String {
    val baseMessage = "追加: $inserted / 重複: $duplicated / マスタ外: $unknownStoreVisits"
    return if (failed > 0) {
        "$baseMessage / 失敗: $failed"
    } else {
        baseMessage
    }
}

private class StarbucksWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val uri = request?.url ?: return true
        return !uri.isAllowedStarbucksUrl()
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)

        if (url?.contains("/mystarbucks/mystore/") == true) {
            view?.evaluateJavascript(ExtractStoreAllScript, null)
        }
    }
}

private fun Uri.isAllowedStarbucksUrl(): Boolean {
    val currentHost = host ?: return false
    return scheme == "https" &&
        (currentHost == "starbucks.co.jp" || currentHost.endsWith(".starbucks.co.jp"))
}
