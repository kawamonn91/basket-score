package com.ttech.basketscore.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttech.basketscore.data.LoadedGame
import com.ttech.basketscore.data.StoredEvent
import com.ttech.basketscore.game.EventText
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.GameState
import com.ttech.basketscore.game.Recording
import com.ttech.basketscore.game.RosterPlayer
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.graph
import com.ttech.basketscore.rules.Period
import com.ttech.basketscore.ui.common.NumberGridDialog
import com.ttech.basketscore.ui.common.toColor
import kotlinx.coroutines.delay

/** チームの色（決めていなければ A は赤、B は青） */
fun teamColor(setup: GameSetup, side: Side): Color =
    setup.team(side).color?.toColor() ?: if (side == Side.A) Color(0xFFD64545) else Color(0xFF1F5FD6)

/** 開いているダイアログ */
private sealed interface Dialog {
    data class Foul(val side: Side, val playerId: String?) : Dialog
    data class TimeoutMinute(val side: Side) : Dialog
    data object Substitution : Dialog
    data object JumpBall : Dialog
    data class Edit(val stored: StoredEvent) : Dialog
    data class AddPlayer(val side: Side) : Dialog
    data object EndGame : Dialog
    data object Exit : Dialog
}

/** 記録の画面（docs/design.md の 3.3。いまは公式の記録） */
@Composable
fun RecordScreen(gameId: Long, onBack: () -> Unit, onOpenPlayByPlay: () -> Unit) {
    val graph = LocalContext.current.graph
    val vm = viewModel(key = "record-$gameId") { RecordViewModel(graph.games, gameId) }
    val game by vm.game.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // 試合中は画面を消さない。戻る操作で誤って抜けないように確かめる
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { dialog = Dialog.Exit }

    val g = game ?: return
    val setup = g.setup
    val state = g.state
    val left = if (setup.teamA.benchLeft) Side.A else Side.B
    val right = left.other

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Scoreboard(
                g = g,
                left = left,
                onBack = { dialog = Dialog.Exit },
                onUndo = vm::undo,
                onList = onOpenPlayByPlay,
                onFlipArrow = vm::flipArrow,
                onEndPeriod = vm::endPeriod,
                onEndGame = { dialog = Dialog.EndGame },
            )
            Banner(vm.banner, onDismiss = vm::dismissBanner)
            message?.let { Banner(listOf(it), onDismiss = { message = null }) }
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamPanel(
                    g, left, vm.selected,
                    onSelect = { vm.selected = it },
                    onTimeout = { dialog = Dialog.TimeoutMinute(left) },
                    onSubstitution = { dialog = Dialog.Substitution },
                    onCoachFoul = { dialog = Dialog.Foul(left, null) },
                    onAddPlayer = { dialog = Dialog.AddPlayer(left) },
                    onEdit = { dialog = Dialog.Edit(it) },
                    modifier = Modifier.weight(1f),
                )
                CenterPanel(
                    g, vm.selected,
                    onStart = { dialog = Dialog.JumpBall },
                    onNextPeriod = vm::nextPeriod,
                    onEndGame = { dialog = Dialog.EndGame },
                    onScore = { sel, kind -> vm.score(sel.side, sel.playerId, kind) },
                    onFoul = { sel -> dialog = Dialog.Foul(sel.side, sel.playerId) },
                    onCancel = { vm.selected = null },
                    onList = onOpenPlayByPlay,
                    onHome = onBack,
                    modifier = Modifier.weight(1.3f),
                )
                TeamPanel(
                    g, right, vm.selected,
                    onSelect = { vm.selected = it },
                    onTimeout = { dialog = Dialog.TimeoutMinute(right) },
                    onSubstitution = { dialog = Dialog.Substitution },
                    onCoachFoul = { dialog = Dialog.Foul(right, null) },
                    onAddPlayer = { dialog = Dialog.AddPlayer(right) },
                    onEdit = { dialog = Dialog.Edit(it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (vm.halfTimeCheck) HalfTimeDialog(setup, state, onDismiss = { vm.halfTimeCheck = false })

    when (val d = dialog) {
        null -> Unit
        is Dialog.Foul -> FoulDialog(
            title = "${EventText.teamLabel(setup, d.side)} " + (d.playerId?.let { EventText.number(setup, d.side, it) } ?: "コーチ") + " のファウル",
            coach = d.playerId == null,
            manToMan = setup.rules.manToManPenalty,
            onDismiss = { dialog = null },
            onConfirm = { code, ft, c, of ->
                vm.foul(d.side, d.playerId, code, ft, c, of)
                dialog = null
            },
        )
        is Dialog.TimeoutMinute -> TimeoutMinuteDialog(
            title = "${EventText.teamLabel(setup, d.side)} のタイムアウト（残り ${state.timeoutsAvailable(d.side, Long.MAX_VALUE)} 回）",
            periodMinutes = (setup.rules.periodMillis(state.period.coerceAtLeast(1)) / 60_000).toInt(),
            onDismiss = { dialog = null },
            onConfirm = {
                vm.timeout(d.side, it)
                dialog = null
            },
        )
        Dialog.Substitution -> SubstitutionDialog(setup, state, onDismiss = { dialog = null }, onConfirm = {
            vm.substitute(it)
            dialog = null
        })
        Dialog.JumpBall -> JumpBallDialog(setup, onDismiss = { dialog = null }, onConfirm = {
            vm.startGame(it)
            dialog = null
        })
        is Dialog.Edit -> EditEventDialog(
            setup, d.stored,
            onDismiss = { dialog = null },
            onSave = {
                vm.update(d.stored, it)
                dialog = null
            },
            onDelete = {
                vm.delete(d.stored)
                dialog = null
            },
        )
        is Dialog.AddPlayer -> NumberGridDialog(
            title = "${EventText.teamLabel(setup, d.side)} に選手を足す（背番号を押す）",
            used = setup.team(d.side).players.map { it.number }.toSet(),
            single = true,
            onDismiss = { dialog = null },
            onConfirm = { numbers ->
                message = vm.addPlayer(d.side, numbers.first())
                dialog = null
            },
        )
        Dialog.EndGame -> ConfirmDialog(
            title = "試合を終える",
            text = "試合終了の時刻を記録し、最終スコアと勝者を決めます（あとから記録を直すこともできます）",
            confirm = "試合を終える",
            onDismiss = { dialog = null },
            onConfirm = {
                vm.endGame()
                dialog = null
            },
        )
        Dialog.Exit -> ConfirmDialog(
            title = "記録の画面を閉じますか",
            text = "記録はそのつど保存されています。試合の一覧から、いつでも続きを記録できます",
            confirm = "閉じる",
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                onBack()
            },
        )
    }
}

@Composable
private fun Banner(lines: List<String>, onDismiss: () -> Unit) {
    if (lines.isEmpty()) return
    LaunchedEffect(lines) {
        delay(6_000)
        onDismiss()
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
private fun Scoreboard(
    g: LoadedGame,
    left: Side,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onList: () -> Unit,
    onFlipArrow: () -> Unit,
    onEndPeriod: () -> Unit,
    onEndGame: () -> Unit,
) {
    val setup = g.setup
    val state = g.state
    var menu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
            Column(Modifier.width(96.dp)) {
                Text(if (state.period == 0) "試合前" else Period.label(state.period), style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        state.finished -> "試合終了"
                        state.period == 0 -> ""
                        state.periodRunning -> "記録中"
                        else -> "休み"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            ScoreSide(setup, state, left, Modifier.weight(1f))
            ArrowView(setup, state, left, onFlipArrow)
            ScoreSide(setup, state, left.other, Modifier.weight(1f), alignEnd = true)
            IconButton(onClick = onUndo, enabled = g.events.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "元に戻す") }
            IconButton(onClick = onList) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "記録の一覧") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "メニュー") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("記録の一覧（Play by Play）") }, onClick = { menu = false; onList() })
                    DropdownMenuItem(text = { Text("ジャンプボールシチュエーション（アローを反転）") }, enabled = state.arrow != null, onClick = { menu = false; onFlipArrow() })
                    DropdownMenuItem(text = { Text("${Period.label(state.period.coerceAtLeast(1))} を終える") }, enabled = state.periodRunning, onClick = { menu = false; onEndPeriod() })
                    DropdownMenuItem(text = { Text("試合を終える") }, enabled = state.period >= 1 && !state.finished, onClick = { menu = false; onEndGame() })
                }
            }
        }
    }
}

@Composable
private fun ScoreSide(setup: GameSetup, state: GameState, side: Side, modifier: Modifier, alignEnd: Boolean = false) {
    val team = state.team(side)
    val penalty = state.period >= 1 && state.inPenalty(side)
    val used = team.timeouts.count { mark ->
        when {
            Period.isOvertime(state.period) -> mark.period == state.period
            state.period <= 2 -> mark.period <= 2
            else -> mark.period in 3..4
        }
    }
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        if (alignEnd) ScoreNumber(team.score)
        Column(Modifier.padding(horizontal = 12.dp), horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(teamColor(setup, side), CircleShape))
                Text(
                    " ${setup.team(side).name}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "TF ${team.teamFoulsIn(state.period.coerceAtLeast(1))}" + (if (penalty) "（罰則）" else "") +
                    "　TO 使用 $used・残り ${state.timeoutsAvailable(side, Long.MAX_VALUE)}",
                style = MaterialTheme.typography.labelLarge,
                color = if (penalty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!alignEnd) ScoreNumber(team.score)
    }
}

@Composable
private fun ScoreNumber(score: Int) {
    Text("$score", fontSize = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
}

/** ポゼッションアロー。押すとジャンプボールシチュエーションとして反転する */
@Composable
private fun ArrowView(setup: GameSetup, state: GameState, left: Side, onFlip: () -> Unit) {
    val arrow = state.arrow
    Column(
        Modifier.width(120.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = arrow != null, onClick = onFlip).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("アロー", style = MaterialTheme.typography.labelSmall)
        when (arrow) {
            null -> Text("—")
            left -> Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "アロー ${EventText.teamLabel(setup, arrow)}", tint = teamColor(setup, arrow), modifier = Modifier.size(36.dp))
            else -> Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "アロー ${EventText.teamLabel(setup, arrow)}", tint = teamColor(setup, arrow), modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun TeamPanel(
    g: LoadedGame,
    side: Side,
    selected: Selection?,
    onSelect: (Selection) -> Unit,
    onTimeout: () -> Unit,
    onSubstitution: () -> Unit,
    onCoachFoul: () -> Unit,
    onAddPlayer: () -> Unit,
    onEdit: (StoredEvent) -> Unit,
    modifier: Modifier,
) {
    val setup = g.setup
    val state = g.state
    val team = state.team(side)
    val color = teamColor(setup, side)
    val active = state.period >= 1
    Column(modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(color, RoundedCornerShape(3.dp)))
        team.onCourt.forEach { id ->
            val p = setup.team(side).player(id) ?: return@forEach
            PlayerButton(
                number = p.number,
                name = p.name,
                fouls = team.fouls(id).size,
                out = team.isOut(id),
                selected = selected == Selection(side, id),
                color = color,
                enabled = active,
                onClick = { onSelect(Selection(side, id)) },
            )
        }
        Text("ベンチ", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            setup.team(side).players.filter { it.id !in team.onCourt }.forEach { p ->
                val sel = selected == Selection(side, p.id)
                OutlinedButton(
                    onClick = { onSelect(Selection(side, p.id)) },
                    enabled = active,
                    border = BorderStroke(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("#${p.number}" + if (team.fouls(p.id).isNotEmpty()) " ${team.fouls(p.id).size}F" else "")
                }
            }
            OutlinedButton(onClick = onAddPlayer, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Text("＋") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = onTimeout, enabled = active) { Text("タイムアウト") }
            FilledTonalButton(onClick = onSubstitution, enabled = active) { Text("交代") }
            FilledTonalButton(onClick = onCoachFoul, enabled = active) { Text("コーチ") }
            FilledTonalButton(
                onClick = { onSelect(Selection(side, RosterPlayer.UNKNOWN)) },
                enabled = active,
            ) { Text("？") }
        }
        HorizontalDivider()
        Text("直近のプレー（押すと直せる）", style = MaterialTheme.typography.labelMedium)
        val indexed = g.events.withIndex().map { IndexedValue(it.index, it.value.event) }
        Recording.recent(indexed, side).forEach { (i, e) ->
            val stored = g.events[i]
            Text(
                "${Period.label(e.period)}　${EventText.describe(setup, e)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (EventText.hasUnknownPlayer(e)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().clickable { onEdit(stored) }.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PlayerButton(number: String, name: String, fouls: Int, out: Boolean, selected: Boolean, color: Color, enabled: Boolean, onClick: () -> Unit) {
    val warn = fouls >= 4 || out
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (selected) 4.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else color),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(64.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { " " }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (out) "退場　" else "") + "ファウル " + "●".repeat(fouls.coerceAtMost(5)) + "○".repeat((5 - fouls).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CenterPanel(
    g: LoadedGame,
    selected: Selection?,
    onStart: () -> Unit,
    onNextPeriod: () -> Unit,
    onEndGame: () -> Unit,
    onScore: (Selection, ShotKind) -> Unit,
    onFoul: (Selection) -> Unit,
    onCancel: () -> Unit,
    onList: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier,
) {
    val setup = g.setup
    val state = g.state
    Surface(modifier.fillMaxHeight(), shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            when {
                state.finished -> {
                    Text("試合終了", style = MaterialTheme.typography.headlineMedium)
                    Text("${setup.teamA.name} ${state.a.score} - ${state.b.score} ${setup.teamB.name}", style = MaterialTheme.typography.titleLarge)
                    state.winner()?.let { Text("勝者: ${setup.team(it).name}") }
                    OutlinedButton(onClick = onList) { Text("記録の一覧") }
                    OutlinedButton(onClick = onHome) { Text("ホームへ") }
                }
                state.period == 0 -> {
                    Text("ジャンプボールのあと、試合を始めます", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onStart, modifier = Modifier.height(64.dp)) { Text("試合を始める", fontSize = 22.sp) }
                }
                !state.periodRunning -> {
                    Text("${Period.label(state.period)} 終了", style = MaterialTheme.typography.headlineSmall)
                    val overtime = Recording.needsOvertime(state)
                    if (state.period < 4 || overtime) {
                        Button(onClick = onNextPeriod, modifier = Modifier.height(64.dp)) {
                            Text("${Period.label(state.period + 1)} を始める", fontSize = 22.sp)
                        }
                    }
                    if (state.period >= 4) {
                        Button(onClick = onEndGame, modifier = Modifier.height(56.dp)) { Text("試合を終える", fontSize = 20.sp) }
                    }
                }
                selected != null -> {
                    val label = if (selected.playerId == RosterPlayer.UNKNOWN) "？（あとで直す）" else
                        EventText.number(setup, selected.side, selected.playerId) + " " + (setup.team(selected.side).player(selected.playerId)?.name ?: "")
                    Text("${EventText.teamLabel(setup, selected.side)}　$label", style = MaterialTheme.typography.titleLarge, color = teamColor(setup, selected.side))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShotKind.entries.forEach { kind ->
                            Button(onClick = { onScore(selected, kind) }, modifier = Modifier.size(width = 120.dp, height = 88.dp)) {
                                Text("${kind.points} 点", fontSize = 26.sp)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { onFoul(selected) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.size(width = 200.dp, height = 72.dp),
                        ) { Text("ファウル", fontSize = 24.sp) }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.size(width = 120.dp, height = 72.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Text("やめる")
                        }
                    }
                }
                else -> {
                    Text("選手を押して、得点やファウルを入れます", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "誰か分からないときは「？」で入れて、あとで直せます。交代・タイムアウト・コーチのファウルは各チームのボタンから",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("ピリオドを終えるときは、右上のメニューから", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
