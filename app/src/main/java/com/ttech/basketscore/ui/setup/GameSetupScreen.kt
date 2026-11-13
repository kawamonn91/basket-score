package com.ttech.basketscore.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttech.basketscore.data.FolderEntity
import com.ttech.basketscore.data.TeamWithPlayers
import com.ttech.basketscore.game.DetailLevel
import com.ttech.basketscore.game.GameType
import com.ttech.basketscore.game.RosterRules
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.graph
import com.ttech.basketscore.rules.Category
import com.ttech.basketscore.ui.FolderNameDialog
import com.ttech.basketscore.ui.common.Problems
import com.ttech.basketscore.ui.common.Section
import com.ttech.basketscore.ui.common.toColor
import kotlinx.coroutines.launch

private val STEP_TITLES = listOf("試合の情報", "チーム", "出場登録とスターター", "規則と記録の詳しさ")

/** 試合の準備（4 段。F-201〜F-206） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSetupScreen(initialFolderId: Long?, onBack: () -> Unit, onEditTeams: () -> Unit, onCreated: (Long) -> Unit) {
    val graph = LocalContext.current.graph
    val vm = viewModel { GameSetupViewModel(graph.games, graph.teams, initialFolderId) }
    val teamsFlow = remember { graph.teams.observeTeams() }
    val teamList by teamsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val teams = remember(teamList) { teamList.associateBy { it.team.id } }
    val foldersFlow = remember { graph.folders.observeFolders() }
    val folderList by foldersFlow.collectAsStateWithLifecycle(initialValue = null)
    val folders = folderList.orEmpty()
    val scope = rememberCoroutineScope()
    var problems by remember { mutableStateOf(emptyList<String>()) }

    // ホームで開いていた大会を選んでおく（大会名も入れる）
    LaunchedEffect(folderList) { folderList?.let(vm::applyInitialFolder) }

    fun goBack() {
        problems = emptyList()
        if (vm.step == 0) onBack() else vm.step--
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新しい試合（${vm.step + 1}/4）${STEP_TITLES[vm.step]}") },
                navigationIcon = { IconButton(onClick = ::goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = ::goBack) { Text(if (vm.step == 0) "やめる" else "戻る") }
                    Button(onClick = {
                        problems = vm.problems(vm.step, teams)
                        if (problems.isNotEmpty()) return@Button
                        if (vm.step < 3) {
                            vm.step++
                        } else {
                            scope.launch { vm.create(teams, folders)?.let(onCreated) }
                        }
                    }) { Text(if (vm.step < 3) "次へ" else "試合を作る") }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 1200.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Problems(problems)
                when (vm.step) {
                    0 -> InfoStep(
                        vm,
                        folders,
                        onCreateFolder = { name ->
                            scope.launch {
                                val id = graph.folders.create(name)
                                vm.chooseFolder(id, folders + FolderEntity(id, name.trim(), System.currentTimeMillis()))
                            }
                        },
                    )
                    1 -> TeamStep(vm, teamList, onEditTeams)
                    2 -> RosterStep(vm, onSaveDefault = { side -> scope.launch { vm.saveDefaultStarters(side) } })
                    else -> RulesStep(vm, teams)
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier)
}

@Composable
private fun InfoStep(vm: GameSetupViewModel, folders: List<FolderEntity>, onCreateFolder: (String) -> Unit) {
    Section("大会のフォルダ") {
        FolderPicker(vm, folders, onCreateFolder)
    }
    Section("試合の種類") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GameType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = vm.gameType == t,
                    onClick = { vm.gameType = t },
                    shape = SegmentedButtonDefaults.itemShape(i, GameType.entries.size),
                ) { Text(t.label) }
            }
        }
    }
    Section("試合（どれも空でもよい）") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(vm.competition, { vm.competition = it }, "大会名", Modifier.weight(2f))
            Field(vm.gameNo, { vm.gameNo = it }, "ゲーム番号", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(vm.date, { vm.date = it }, "日付（2026-11-12）", Modifier.weight(1f))
            Field(vm.startTime, { vm.startTime = it }, "開始時刻（24 時間制）", Modifier.weight(1f))
            Field(vm.venue, { vm.venue = it }, "会場", Modifier.weight(2f))
        }
    }
    Section("審判") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(vm.crewChief, { vm.crewChief = it }, "クルーチーフ", Modifier.weight(1f))
            Field(vm.umpire1, { vm.umpire1 = it }, "アンパイア 1", Modifier.weight(1f))
            Field(vm.umpire2, { vm.umpire2 = it }, "アンパイア 2", Modifier.weight(1f))
            Field(vm.umpire3, { vm.umpire3 = it }, "副審 3 人目", Modifier.weight(1f))
        }
    }
    Section("テーブルオフィシャルズ") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(vm.scorer, { vm.scorer = it }, "スコアラー", Modifier.weight(1f))
            Field(vm.assistantScorer, { vm.assistantScorer = it }, "A.スコアラー", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(vm.timer, { vm.timer = it }, "タイマー", Modifier.weight(1f))
            Field(vm.shotClockOperator, { vm.shotClockOperator = it }, "ショットクロックオペレーター", Modifier.weight(1f))
        }
    }
    Section("メモ") {
        OutlinedTextField(vm.memo, { vm.memo = it }, label = { Text("メモ（任意）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    }
}

/** 試合を入れる大会のフォルダを選ぶ（その場で作ることもできる） */
@Composable
private fun FolderPicker(vm: GameSetupViewModel, folders: List<FolderEntity>, onCreateFolder: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val current = folders.firstOrNull { it.id == vm.folderId }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            OutlinedButton(onClick = { open = true }) {
                Icon(if (current != null) Icons.Outlined.Folder else Icons.Outlined.Inbox, contentDescription = null)
                Text(current?.name ?: "未分類（フォルダに入れない）", modifier = Modifier.padding(horizontal = 8.dp))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("未分類（フォルダに入れない）") },
                    leadingIcon = { Icon(Icons.Outlined.Inbox, contentDescription = null) },
                    onClick = { open = false; vm.chooseFolder(null, folders) },
                )
                folders.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(f.name) },
                        leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        onClick = { open = false; vm.chooseFolder(f.id, folders) },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("新しい大会を作る") },
                    leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                    onClick = { open = false; creating = true },
                )
            }
        }
        Text(
            "ホームの試合の一覧で、大会ごとにまとめて見られます。大会名が空なら、フォルダの名前を入れます",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
    if (creating) {
        FolderNameDialog(
            title = "新しい大会",
            initial = "",
            folders = folders,
            onDismiss = { creating = false },
            onConfirm = { name -> creating = false; onCreateFolder(name) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamStep(vm: GameSetupViewModel, teams: List<TeamWithPlayers>, onEditTeams: () -> Unit) {
    if (teams.size < 2) {
        Text("チームが 2 つ以上要ります。先に名簿を作ってください", color = MaterialTheme.colorScheme.error)
    }
    OutlinedButton(onClick = onEditTeams) { Text("チームを作る・直す") }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Side.entries.forEach { side ->
            Section("チーム $side", Modifier.weight(1f)) {
                teams.forEach { team ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = vm.teamId(side) == team.team.id, onClick = { vm.chooseTeam(side, team) }),
                    ) {
                        RadioButton(selected = vm.teamId(side) == team.team.id, onClick = { vm.chooseTeam(side, team) })
                        Box(Modifier.size(16.dp).background((team.team.color ?: 0xFF9E9E9E).toColor(), CircleShape))
                        Text("${team.team.name}（${team.players.size} 人）", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    Section("ベンチの位置（記録係から見て）") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = vm.benchALeft, onClick = { vm.benchALeft = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("A が左・B が右")
            }
            SegmentedButton(selected = !vm.benchALeft, onClick = { vm.benchALeft = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text("B が左・A が右")
            }
        }
    }
    Section("前半に攻める向き") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = vm.attackOpponentBench, onClick = { vm.attackOpponentBench = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("相手のベンチ側のゴール")
            }
            SegmentedButton(selected = !vm.attackOpponentBench, onClick = { vm.attackOpponentBench = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text("自分のベンチ側のゴール")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RosterStep(vm: GameSetupViewModel, onSaveDefault: (Side) -> Unit) {
    Text("出場する選手に印を付け、スターター 5 人を選びます（出場登録は 18 人まで）")
    Section("使う背番号") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !vm.usePracticeNumbers, onClick = { vm.usePracticeNumbers = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("公式用")
            }
            SegmentedButton(selected = vm.usePracticeNumbers, onClick = { vm.usePracticeNumbers = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text("練習用（無い選手は公式用）")
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Side.entries.forEach { side -> RosterColumn(vm, side, onSaveDefault, Modifier.weight(1f)) }
    }
}

@Composable
private fun RosterColumn(vm: GameSetupViewModel, side: Side, onSaveDefault: (Side) -> Unit, modifier: Modifier) {
    val entries = vm.entries(side)
    val selected = entries.count { it.selected }
    val starters = entries.count { it.starter }
    Section("チーム $side　出場 $selected 人・スターター $starters/${RosterRules.STARTERS}", modifier) {
        entries.forEachIndexed { index, e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = e.selected, onCheckedChange = { vm.toggleSelected(side, index) })
                Text("#${vm.shownNumber(e)}", modifier = Modifier.width(48.dp), style = MaterialTheme.typography.titleMedium)
                Text(
                    e.name + (if (e.captain) "（CAP）" else "") + (if (e.temporary) "（この試合だけ）" else ""),
                    modifier = Modifier.weight(1f).clickable { vm.toggleSelected(side, index) },
                )
                FilterChip(selected = e.starter, onClick = { vm.toggleStarter(side, index) }, label = { Text("スターター") })
            }
        }
        var saved by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = {
                onSaveDefault(side)
                saved = true
            },
            enabled = starters == RosterRules.STARTERS && vm.teamId(side) != null,
        ) { Text(if (saved) "既定のスターターにしました" else "今のスターターを既定にする") }
        HorizontalDivider()
        var number by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                number, { number = it.filter(Char::isDigit).take(2) }, label = { Text("背番号") }, singleLine = true,
                modifier = Modifier.width(100.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(name, { name = it }, label = { Text("この試合だけの選手") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    vm.addTemporary(side, number, name)
                    number = ""
                    name = ""
                },
                enabled = number.isNotBlank(),
            ) { Text("足す") }
        }
    }
}

@Composable
private fun RulesStep(vm: GameSetupViewModel, teams: Map<Long, TeamWithPlayers>) {
    Section("大会の区分") {
        Category.entries.forEach { c ->
            val rules = c.defaultRules()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.selectable(vm.category == c, onClick = { vm.category = c })) {
                RadioButton(selected = vm.category == c, onClick = { vm.category = c })
                Text("${c.label}　（${rules.quarterSeconds / 60} 分 × 4・OT ${rules.overtimeSeconds / 60} 分" + (if (rules.manToManPenalty) "・M あり" else "") + "）")
            }
        }
    }
    Section("タイマー") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.useClock, onCheckedChange = { vm.useClock = it })
            Spacer(Modifier.width(12.dp))
            Text(if (vm.useClock) "ゲームクロックを使う（記録に時刻が付き、時間帯の分析ができる）" else "ゲームクロックを使わない（タイムアウトの分は手で選ぶ）")
        }
    }
    Section("記録の詳しさ") {
        DetailLevel.entries.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.selectable(vm.detail == d, onClick = { vm.detail = d })) {
                RadioButton(selected = vm.detail == d, onClick = { vm.detail = d })
                Text(d.label)
            }
        }
        if (vm.detail != DetailLevel.OFFICIAL) {
            HorizontalDivider()
            Text("スタッツを記録するチーム")
            Side.entries.forEach { side ->
                val name = vm.teamId(side)?.let { teams[it]?.team?.name } ?: "チーム $side"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = if (side == Side.A) vm.statsA else vm.statsB,
                        onCheckedChange = { if (side == Side.A) vm.statsA = it else vm.statsB = it },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(name)
                }
            }
        }
    }
}
