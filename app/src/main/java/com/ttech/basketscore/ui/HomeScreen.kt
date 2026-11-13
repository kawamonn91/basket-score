package com.ttech.basketscore.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttech.basketscore.data.Backup
import com.ttech.basketscore.data.BackupPrefs
import com.ttech.basketscore.data.GameEntity
import com.ttech.basketscore.data.GameStatus
import com.ttech.basketscore.graph
import com.ttech.basketscore.ui.analysis.AnalysisTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** ホーム: 新しい試合・チーム・試合の一覧・バックアップ（docs/design.md の 3.1。F-1004・F-1006） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewGame: () -> Unit,
    onTeams: () -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenAnalysis: (Long, AnalysisTab) -> Unit,
    onOpenScoresheet: (Long) -> Unit,
) {
    val context = LocalContext.current
    val graph = context.graph
    val gamesFlow = remember { graph.games.observeGames() }
    val games by gamesFlow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var promptBackup by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var bulkDelete by remember { mutableStateOf(false) }
    var lastBackupAt by remember { mutableStateOf(graph.backupPrefs.lastBackupAt) }

    // バックアップを書き出す（1 つの JSON ファイル。保存先は利用者が選ぶ）
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = graph.backup.export()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("書き出せませんでした")
                }
            }
            if (result.isSuccess) {
                val now = System.currentTimeMillis()
                graph.backupPrefs.lastBackupAt = now
                lastBackupAt = now
                snackbar.showSnackbar("バックアップを書き出しました")
            } else {
                snackbar.showSnackbar("書き出せませんでした: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    fun startExport() = exportLauncher.launch(Backup.fileName(LocalDate.now().toString()))

    // バックアップから戻す（読み込んでから、置き換えてよいかを確かめる）
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
            val valid = text?.let { runCatching { Backup.decode(it) } }
            when {
                text == null -> snackbar.showSnackbar("ファイルを読めませんでした")
                valid?.isFailure == true -> snackbar.showSnackbar(valid.exceptionOrNull()?.message ?: "読み込めませんでした")
                else -> pendingRestore = text
            }
        }
    }

    // 前回のバックアップから日がたったら、起動したときに促す（F-1006）
    LaunchedEffect(Unit) {
        val oldest = graph.games.oldestGameAt()
        val prefs = graph.backupPrefs
        if (BackupPrefs.shouldPrompt(System.currentTimeMillis(), prefs.lastBackupAt, prefs.snoozedUntil, oldest)) promptBackup = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Basket Score") },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "メニュー") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("バックアップを書き出す") }, onClick = { menu = false; startExport() })
                            DropdownMenuItem(text = { Text("バックアップから戻す") }, onClick = { menu = false; importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) })
                            DropdownMenuItem(text = { Text("試合をまとめて消す") }, enabled = !games.isNullOrEmpty(), onClick = { menu = false; bulkDelete = true })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                    Button(onClick = onNewGame) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("新しい試合", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onTeams) {
                        Icon(Icons.Outlined.Groups, contentDescription = null)
                        Text("チーム", modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        lastBackupAt?.let { "前回のバックアップ: ${dateOf(it)}（${BackupPrefs.daysSince(System.currentTimeMillis(), it)} 日前）" } ?: "まだバックアップしていません",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("試合", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                val list = games
                when {
                    list == null -> Unit
                    list.isEmpty() -> Text(
                        "まだ試合がありません。「チーム」で名簿を作り、「新しい試合」から記録を始めます",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { game ->
                            GameRow(
                                game,
                                onClick = { onOpenGame(game.id) },
                                onAnalysis = { tab -> onOpenAnalysis(game.id, tab) },
                                onScoresheet = { onOpenScoresheet(game.id) },
                            )
                            HorizontalDivider()
                        }
                        item { Spacer(Modifier.padding(24.dp)) }
                    }
                }
            }
        }
    }

    if (promptBackup) {
        val since = lastBackupAt
        AlertDialog(
            onDismissRequest = { promptBackup = false },
            title = { Text("バックアップを書き出しますか") },
            text = {
                Text(
                    (if (since != null) "前回のバックアップから ${BackupPrefs.daysSince(System.currentTimeMillis(), since)} 日たちました。" else "まだバックアップしていません。") +
                        "端末が壊れたときに備えて、Google ドライブなどに書き出しておくと安心です",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    promptBackup = false
                    startExport()
                }) { Text("書き出す") }
            },
            dismissButton = {
                TextButton(onClick = {
                    promptBackup = false
                    graph.backupPrefs.snoozedUntil = System.currentTimeMillis() + BackupPrefs.SNOOZE_DAYS * BackupPrefs.DAY
                }) { Text("あとで") }
            },
        )
    }

    pendingRestore?.let { text ->
        val file = remember(text) { runCatching { Backup.decode(text) }.getOrNull() }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("バックアップから戻しますか") },
            text = {
                Text(
                    "今のチーム・試合・記録はすべて消え、バックアップの中身に置き換わります。" +
                        (file?.let { "\n\nバックアップ: ${dateOf(it.createdAt)} に作成、チーム ${it.teams.size}・試合 ${it.games.size}" } ?: ""),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val result = runCatching { graph.backup.restore(text) }
                        snackbar.showSnackbar(
                            result.fold(
                                { "戻しました（チーム ${it.teams.size}・試合 ${it.games.size}）" },
                                { "戻せませんでした: ${it.message}" },
                            ),
                        )
                    }
                }) { Text("置き換える") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("やめる") } },
        )
    }

    if (bulkDelete) {
        BulkDeleteDialog(
            games = games.orEmpty(),
            onDismiss = { bulkDelete = false },
            onDelete = { ids ->
                bulkDelete = false
                scope.launch {
                    graph.games.deleteGames(ids)
                    ids.forEach { graph.clocks.delete(it) }
                    snackbar.showSnackbar("${ids.size} 試合を消しました")
                }
            },
        )
    }
}

private fun dateOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** 試合をまとめて消す（F-1006）。確定した試合は、解除してから消す */
@Composable
private fun BulkDeleteDialog(games: List<GameEntity>, onDismiss: () -> Unit, onDelete: (List<Long>) -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirm by remember { mutableStateOf(false) }
    val deletable = games.filter { it.status != GameStatus.LOCKED }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirm) "${selected.size} 試合を消します" else "消す試合を選ぶ") },
        text = {
            if (confirm) {
                Text("選んだ試合の記録はすべて消え、元に戻せません（バックアップがあれば、そこから戻せます）")
            } else {
                Column {
                    val all = deletable.isNotEmpty() && selected.size == deletable.size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().toggleable(value = all, role = Role.Checkbox) { on ->
                            selected = if (on) deletable.map { it.id }.toSet() else emptySet()
                        },
                    ) {
                        Checkbox(checked = all, onCheckedChange = null)
                        Text("すべて選ぶ（確定した試合は除く）")
                    }
                    HorizontalDivider()
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(games, key = { it.id }) { g ->
                            val locked = g.status == GameStatus.LOCKED
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().toggleable(value = g.id in selected, enabled = !locked, role = Role.Checkbox) { on ->
                                    selected = if (on) selected + g.id else selected - g.id
                                },
                            ) {
                                Checkbox(checked = g.id in selected, enabled = !locked, onCheckedChange = null)
                                Text(
                                    "${g.date}　${g.teamAName} ${g.scoreA} - ${g.scoreB} ${g.teamBName}" + if (locked) "（確定済み）" else "",
                                    color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (confirm) {
                TextButton(onClick = { onDelete(selected.toList()) }) { Text("消す", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = { confirm = true }, enabled = selected.isNotEmpty()) { Text("次へ") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

@Composable
private fun GameRow(game: GameEntity, onClick: () -> Unit, onAnalysis: (AnalysisTab) -> Unit, onScoresheet: () -> Unit) {
    ListItem(
        leadingContent = { Icon(Icons.Outlined.SportsBasketball, contentDescription = null) },
        headlineContent = { Text("${game.teamAName}  ${game.scoreA} - ${game.scoreB}  ${game.teamBName}") },
        supportingContent = {
            Column {
                Text(listOf(game.date, game.competition).filter { it.isNotBlank() }.joinToString("  "))
                // 試合の一覧から直接開く（F-1005）
                if (game.status != GameStatus.READY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        AssistChip(onClick = { onAnalysis(AnalysisTab.FLOW) }, label = { Text("フロー") })
                        AssistChip(onClick = onScoresheet, label = { Text("ランニングスコア") })
                        AssistChip(onClick = { onAnalysis(AnalysisTab.BOX) }, label = { Text("ボックススコア") })
                        AssistChip(onClick = { onAnalysis(AnalysisTab.SHOTS) }, label = { Text("シュートチャート") })
                        AssistChip(onClick = { onAnalysis(AnalysisTab.RESULT) }, label = { Text("分析") })
                    }
                }
            }
        },
        trailingContent = {
            Text(
                game.status.label,
                color = if (game.status == GameStatus.LIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
