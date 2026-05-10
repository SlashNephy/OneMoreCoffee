package blue.starry.onemorecoffee.core.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary

private const val TAG = "StoreDetailSheet"

@Composable
fun StoreDetailSheet(
    store: StoreVisitSummary,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = store.name,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = store.fullAddress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = store.visitStatusText(),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                context.openNavigation(store)
            },
        ) {
            Text(text = "経路検索")
        }
    }
}

private fun StoreVisitSummary.visitStatusText(): String {
    return if (isVisited) {
        "訪問回数: $visitCount / 最終訪問: $lastVisitedOn"
    } else {
        "未訪問"
    }
}

private fun Context.openNavigation(store: StoreVisitSummary) {
    val navigationUri = Uri.parse("google.navigation:q=${store.latitude},${store.longitude}")
    val navigationIntent = Intent(Intent.ACTION_VIEW, navigationUri).apply {
        addNewTaskFlagIfNeeded(this@openNavigation)
    }

    try {
        startActivity(navigationIntent)
        return
    } catch (exception: ActivityNotFoundException) {
        Log.w(TAG, "Navigation app is not available, trying geo fallback.", exception)
    }

    val label = Uri.encode(store.name)
    val geoUri = Uri.parse("geo:0,0?q=${store.latitude},${store.longitude}($label)")
    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        addNewTaskFlagIfNeeded(this@openNavigation)
    }

    try {
        startActivity(geoIntent)
    } catch (exception: ActivityNotFoundException) {
        Log.w(TAG, "Map app is not available.", exception)
    }
}

private fun Intent.addNewTaskFlagIfNeeded(context: Context) {
    if (context !is Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
