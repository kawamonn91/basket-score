package com.ttech.basketscore.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttech.basketscore.data.Backup
import com.ttech.basketscore.data.BackupPrefs
import com.ttech.basketscore.data.FolderEntity
import com.ttech.basketscore.data.FolderSummary
import com.ttech.basketscore.data.Folders
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

/**
 * ホーム: 新しい試合・チーム・大会のフォルダと試合の一覧・バックアップ（docs/design.md の 3.1・6 章。F-1004・F-1006・F-1007）。
 * 横に広い画面（タブレットの横向き）では、左に大会の一覧、右にその試合を並べる
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewGame: (folderId: Long?) -> Unit,
    onTeams: () -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenAnalysis: (Long, AnalysisTab) -> Unit,
    onOpenScoresheet: (Long) -> Unit,
) {
    val context = LocalContext.current
    val graph = context.graph
    val gamesFlow = remember { graph.games.observeGames() }
    val games by gamesFlow.collectAsStateWithLifecycle(initialValue = null)
    val foldersFlow = remember { graph.folders.observeFolders() }
    val folders by foldersFlow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var promptBackup by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var bulkDelete by remember { mutableStateOf(false) }
    var lastBackupAt by remember { mutableStateOf(graph.backupPrefs.lastBackupAt) }

    // 見ているまとまり（すべて・未分類・大会のフォルダの ID）。ほかの画面から戻っても覚えておく
    var view by rememberSaveable { mutableLongStateOf(Folders.ALL) }
    var creatingFolder by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var bulkMove by remember { mutableStateOf(false) }
    var moveOne by remember { mutableStateOf<Long?>(null) }

    val allGames = games.orEmpty()
    val folderList = folders.orEmpty()
    val summaries = remember(folderList, allGames) { Folders.summaries(folderList, allGames) }
    val shown = remember(allGames, view) { Folders.gamesIn(allGames, view) }
    val currentFolder = folderList.firstOrNull { it.id == view }
    val folderNames = remember(folderList) { folderList.associate { it.id to it.name } }

    // 見ていた大会が無くなったら（消した・バックアップから戻した）、すべての試合に戻す
    LaunchedEffect(folders, view) {
        val list = folders ?: return@LaunchedEffect
        if (view != Folders.ALL && view != Folders.UNFILED && list.none { it.id == view }) view = Folders.ALL
    }

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

    fun moved(count: Int, name: String?) {
        scope.launch { snackbar.showSnackbar(if (name == null) "$count 試合を未分類に戻しました" else "$count 試合を「$name」に移しました") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Basket Score") },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "メニュー") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("大会を作る") }, onClick = { menu = false; creatingFolder = true })
                            DropdownMenuItem(text = { Text("試合をまとめて大会に移す") }, enabled = shown.isNotEmpty(), onClick = { menu = false; bulkMove = true })
                            DropdownMenuItem(text = { Text("試合をまとめて消す") }, enabled = shown.isNotEmpty(), onClick = { menu = false; bulkDelete = true })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("バックアップを書き出す") }, onClick = { menu = false; startExport() })
                            DropdownMenuItem(text = { Text("バックアップから戻す") }, onClick = { menu = false; importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 840.dp
            Column(Modifier.widthIn(max = 1280.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                    Button(onClick = { onNewGame(currentFolder?.id) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(if (currentFolder != null) "この大会に新しい試合" else "新しい試合", modifier = Modifier.padding(start = 8.dp))
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
                val pane: @Composable (Modifier) -> Unit = { modifier ->
                    GamesPane(
                        view = view,
                        folder = currentFolder,
                        period = summaries.firstOrNull { it.folder.id == view }?.period,
                        games = shown,
                        loaded = games != null,
                        anyGames = allGames.isNotEmpty(),
                        folderNames = folderNames,
                        onRename = { currentFolder?.let { renamingFolder = it } },
                        onDelete = { currentFolder?.let { deletingFolder = it } },
                        onOpenGame = onOpenGame,
                        onOpenAnalysis = onOpenAnalysis,
                        onOpenScoresheet = onOpenScoresheet,
                        onMove = { moveOne = it },
                        modifier = modifier,
                    )
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        FolderPane(
                            summaries = summaries,
                            games = allGames,
                            view = view,
                            onSelect = { view = it },
                            onCreate = { creatingFolder = true },
                            modifier = Modifier.width(300.dp).fillMaxHeight(),
                        )
                        VerticalDivider(Modifier.padding(horizontal = 16.dp))
                        pane(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    FolderSelector(summaries, allGames, view, onSelect = { view = it }, onCreate = { creatingFolder = true })
                    pane(Modifier.fillMaxSize())
                }
            }
        }
    }

    if (creatingFolder) {
        FolderNameDialog(
            title = "大会を作る",
            initial = "",
            folders = folderList,
            onDismiss = { creatingFolder = false },
            onConfirm = { name ->
                creatingFolder = false
                scope.launch { view = graph.folders.create(name) }
            },
        )
    }

    renamingFolder?.let { folder ->
        FolderNameDialog(
            title = "大会の名前を変える",
            initial = folder.name,
            folders = folderList,
            exceptId = folder.id,
            onDismiss = { renamingFolder = null },
            onConfirm = { name ->
                renamingFolder = null
                scope.launch { graph.folders.rename(folder, name) }
            },
        )
    }

    deletingFolder?.let { folder ->
        val count = allGames.count { it.folderId == folder.id }
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("「${folder.name}」を消しますか") },
            text = {
                Text(
                    if (count == 0) "この大会のフォルダを消します" else "フォルダだけを消します。中の $count 試合は消えずに「未分類」に移ります。" +
                        "試合も消すときは、先に「試合をまとめて消す」で消してください",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deletingFolder = null
                    scope.launch {
                        graph.folders.delete(folder.id)
                        snackbar.showSnackbar("「${folder.name}」を消しました")
                    }
                }) { Text("消す", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("やめる") } },
        )
    }

    if (bulkMove || moveOne != null) {
        val single = moveOne
        MoveGamesDialog(
            games = if (single != null) allGames else shown,
            folders = summaries.map { it.folder },
            preselected = single?.let { setOf(it) },
            onDismiss = { bulkMove = false; moveOne = null },
            onMove = { ids, folderId ->
                bulkMove = false
                moveOne = null
                scope.launch {
                    graph.games.moveGames(ids, folderId)
                    moved(ids.size, folderId?.let { folderNames[it] })
                }
            },
            onCreateAndMove = { ids, name ->
                bulkMove = false
                moveOne = null
                scope.launch {
                    val id = graph.folders.create(name)
                    graph.games.moveGames(ids, id)
                    moved(ids.size, name)
                }
            },
        )
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
                    "今のチーム・大会・試合・記録はすべて消え、バックアップの中身に置き換わります。" +
                        (file?.let { "\n\nバックアップ: ${dateOf(it.createdAt)} に作成、チーム ${it.teams.size}・大会 ${it.folders.size}・試合 ${it.games.size}" } ?: ""),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val result = runCatching { graph.backup.restore(text) }
                        snackbar.showSnackbar(
                            result.fold(
                                { "戻しました（チーム ${it.teams.size}・大会 ${it.folders.size}・試合 ${it.games.size}）" },
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
            games = shown,
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

/** 大会の一覧（横に広い画面の左側）。すべての試合・未分類・大会のフォルダ */
@Composable
private fun FolderPane(
    summaries: List<FolderSummary>,
    games: List<GameEntity>,
    view: Long,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        item { FolderItem(Icons.Outlined.AllInbox, "すべての試合", null, games.size, view == Folders.ALL) { onSelect(Folders.ALL) } }
        item { FolderItem(Icons.Outlined.Inbox, "未分類", null, games.count { it.folderId == null }, view == Folders.UNFILED) { onSelect(Folders.UNFILED) } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                Text("大会", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                TextButton(onClick = onCreate) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                    Text("大会を作る", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (summaries.isEmpty()) {
            item {
                Text(
                    "大会ごとにフォルダを作ると、試合をまとめて管理できます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        items(summaries, key = { it.folder.id }) { s ->
            val selected = view == s.folder.id
            FolderItem(if (selected) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, s.folder.name, s.period, s.games, selected) { onSelect(s.folder.id) }
        }
        item { Spacer(Modifier.padding(24.dp)) }
    }
}

@Composable
private fun FolderItem(icon: ImageVector, name: String, sub: String?, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics { this.selected = selected },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$count", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 大会を選ぶ（狭い画面。上に 1 つのボタンで出す） */
@Composable
private fun FolderSelector(summaries: List<FolderSummary>, games: List<GameEntity>, view: Long, onSelect: (Long) -> Unit, onCreate: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = summaries.firstOrNull { it.folder.id == view }?.folder
    Box(Modifier.padding(bottom = 4.dp)) {
        OutlinedButton(onClick = { open = true }) {
            Icon(
                when (view) {
                    Folders.ALL -> Icons.Outlined.AllInbox
                    Folders.UNFILED -> Icons.Outlined.Inbox
                    else -> Icons.Outlined.FolderOpen
                },
                contentDescription = null,
            )
            Text(Folders.title(view, listOfNotNull(current)), modifier = Modifier.padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "大会を選ぶ")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("すべての試合（${games.size}）") },
                leadingIcon = { Icon(Icons.Outlined.AllInbox, contentDescription = null) },
                onClick = { open = false; onSelect(Folders.ALL) },
            )
            DropdownMenuItem(
                text = { Text("未分類（${games.count { it.folderId == null }}）") },
                leadingIcon = { Icon(Icons.Outlined.Inbox, contentDescription = null) },
                onClick = { open = false; onSelect(Folders.UNFILED) },
            )
            if (summaries.isNotEmpty()) HorizontalDivider()
            summaries.forEach { s ->
                DropdownMenuItem(
                    text = { Text("${s.folder.name}（${s.games}）") },
                    leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    onClick = { open = false; onSelect(s.folder.id) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("大会を作る") },
                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                onClick = { open = false; onCreate() },
            )
        }
    }
}

/** 見ているまとまりの試合の一覧。大会なら、名前を変える・消すボタンも出す */
@Composable
private fun GamesPane(
    view: Long,
    folder: FolderEntity?,
    period: String?,
    games: List<GameEntity>,
    loaded: Boolean,
    anyGames: Boolean,
    folderNames: Map<Long, String>,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenAnalysis: (Long, AnalysisTab) -> Unit,
    onOpenScoresheet: (Long) -> Unit,
    onMove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(folder?.name ?: Folders.title(view, emptyList()), style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull("${games.size} 試合", period).joinToString("・"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (folder != null) {
                IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, contentDescription = "大会の名前を変える") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "大会を消す") }
            }
        }
        when {
            !loaded -> Unit
            !anyGames -> EmptyText("まだ試合がありません。「チーム」で名簿を作り、「新しい試合」から記録を始めます")
            games.isEmpty() && view == Folders.UNFILED -> EmptyText("未分類の試合はありません")
            games.isEmpty() -> EmptyText("この大会にはまだ試合がありません。「この大会に新しい試合」で記録を始めるか、ほかの試合の「大会に移す」でここに移します")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(games, key = { it.id }) { game ->
                    GameRow(
                        game,
                        // すべての試合を見ているときは、入っている大会も出す（大会名と同じなら省く）
                        folderName = if (view == Folders.ALL) game.folderId?.let { folderNames[it] }?.takeIf { it != game.competition } else null,
                        onClick = { onOpenGame(game.id) },
                        onAnalysis = { tab -> onOpenAnalysis(game.id, tab) },
                        onScoresheet = { onOpenScoresheet(game.id) },
                        onMove = { onMove(game.id) },
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
}

/** 試合をまとめて消す（F-1006）。確定した試合は、解除してから消す */
@Composable
private fun BulkDeleteDialog(games: List<GameEntity>, onDismiss: () -> Unit, onDelete: (List<Long>) -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirm) "${selected.size} 試合を消します" else "消す試合を選ぶ") },
        text = {
            if (confirm) {
                Text("選んだ試合の記録はすべて消え、元に戻せません（バックアップがあれば、そこから戻せます）")
            } else {
                GameChecklist(
                    games = games,
                    selected = selected,
                    onChange = { selected = it },
                    allLabel = "すべて選ぶ（確定した試合は除く）",
                    enabled = { it.status != GameStatus.LOCKED },
                    note = { if (it.status == GameStatus.LOCKED) "（確定済み）" else "" },
                )
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
private fun GameRow(
    game: GameEntity,
    folderName: String?,
    onClick: () -> Unit,
    onAnalysis: (AnalysisTab) -> Unit,
    onScoresheet: () -> Unit,
    onMove: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(Icons.Outlined.SportsBasketball, contentDescription = null) },
        headlineContent = { Text("${game.teamAName}  ${game.scoreA} - ${game.scoreB}  ${game.teamBName}") },
        supportingContent = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(listOf(game.date, game.competition).filter { it.isNotBlank() }.joinToString("  "))
                    if (folderName != null) {
                        Icon(Icons.Outlined.Folder, contentDescription = "大会", modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(16.dp))
                        Text(folderName)
                    }
                }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    game.status.label,
                    color = if (game.status == GameStatus.LIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onMove) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "大会に移す") }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
