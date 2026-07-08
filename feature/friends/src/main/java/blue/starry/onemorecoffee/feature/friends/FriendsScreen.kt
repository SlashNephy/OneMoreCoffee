package blue.starry.onemorecoffee.feature.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant

@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    viewModel: FriendsScreenViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            confirmButton = {
                TextButton(onClick = viewModel::consumeError) {
                    Text("閉じる")
                }
            },
            text = { Text(message) },
        )
    }

    when (val state = uiState) {
        FriendsUiState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        FriendsUiState.NotJoined -> NotJoinedContent(
            isProcessing = isProcessing,
            onCreate = viewModel::createLeague,
            onJoin = viewModel::joinLeague,
            modifier = modifier,
        )
        is FriendsUiState.Joined -> JoinedContent(
            state = state,
            onLeave = viewModel::leaveLeague,
            modifier = modifier,
        )
    }
}

@Composable
private fun NotJoinedContent(
    isProcessing: Boolean,
    onCreate: (leagueName: String, displayName: String, emoji: String) -> Unit,
    onJoin: (inviteCode: String, displayName: String, emoji: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("☕") }
    var leagueName by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "フレンドとリーグを組んで、制覇の進み具合を共有しよう",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("表示名") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = emoji,
            onValueChange = { emoji = it },
            label = { Text("アイコン絵文字") },
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        OutlinedTextField(
            value = leagueName,
            onValueChange = { leagueName = it },
            label = { Text("新しいリーグの名前") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onCreate(leagueName, displayName, emoji) },
            enabled = !isProcessing && leagueName.isNotBlank() && displayName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("リーグを作成")
        }
        HorizontalDivider()
        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it },
            label = { Text("招待コード") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { onJoin(inviteCode, displayName, emoji) },
            enabled = !isProcessing && inviteCode.isNotBlank() && displayName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("招待コードで参加")
        }
    }
}

@Composable
private fun JoinedContent(
    state: FriendsUiState.Joined,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showsLeaveDialog by rememberSaveable { mutableStateOf(false) }

    if (showsLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showsLeaveDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showsLeaveDialog = false
                        onLeave()
                    },
                ) {
                    Text("退出する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showsLeaveDialog = false }) {
                    Text("キャンセル")
                }
            },
            text = { Text("リーグを退出すると、自分のアクティビティと統計はリーグから削除されます。") },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = state.league?.name ?: "リーグ",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "招待コード: ${state.league?.inviteCode ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "総制覇数ランキング",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.ranking.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${entry.rank}. ${entry.member.emoji} ${entry.member.displayName}" +
                                    if (entry.member.uid == state.myUid) "（自分）" else "",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${entry.member.visitedStoreCount} 店舗",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "アクティビティ",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        items(state.feed, key = { item -> item.event.id }) { item ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = feedItemText(
                        event = item.event,
                        memberName = item.member?.displayName ?: "元メンバー",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatRelativeTime(createdAt = item.event.createdAt, now = Instant.now()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            TextButton(onClick = { showsLeaveDialog = true }) {
                Text("リーグを退出", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
