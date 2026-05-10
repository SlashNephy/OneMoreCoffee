package blue.starry.onemorecoffee.feature.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary

@Composable
fun StoreListScreen(
    modifier: Modifier = Modifier,
    viewModel: StoreListScreenViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StoreListContent(
        uiState = uiState,
        onQueryChange = viewModel::updateQuery,
        onVisitedFilterChange = viewModel::updateVisitedFilter,
        modifier = modifier,
    )
}

@Composable
private fun StoreListContent(
    uiState: StoreListUiState,
    onQueryChange: (String) -> Unit,
    onVisitedFilterChange: (VisitedFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text("店舗名・住所で検索")
            },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VisitedFilter.entries.forEach { filter ->
                FilterChip(
                    selected = uiState.visitedFilter == filter,
                    onClick = {
                        onVisitedFilterChange(filter)
                    },
                    label = {
                        Text(filter.label)
                    },
                )
            }
        }

        if (uiState.isEmpty) {
            EmptyStoreList(modifier = Modifier.fillMaxWidth())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = uiState.stores,
                    key = StoreVisitSummary::id,
                ) { store ->
                    StoreRow(store = store)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EmptyStoreList(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "条件に一致する店舗がありません",
        modifier = modifier.padding(vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StoreRow(
    store: StoreVisitSummary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = store.name,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = store.fullAddress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = store.visitStatusText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun StoreVisitSummary.visitStatusText(): String {
    return if (lastVisitedOn == null) {
        "未訪問"
    } else {
        "最終訪問: $lastVisitedOn / 訪問回数: $visitCount"
    }
}
