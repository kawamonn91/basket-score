package com.ttech.basketscore.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttech.basketscore.data.StoredEvent
import com.ttech.basketscore.game.EventText
import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.FoulCode
import com.ttech.basketscore.game.GameEvent
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.GameState
import com.ttech.basketscore.game.Offender
import com.ttech.basketscore.game.Rebound
import com.ttech.basketscore.game.RosterPlayer
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.SubChange
import com.ttech.basketscore.game.Timeout
import com.ttech.basketscore.game.Turnover
import com.ttech.basketscore.rules.Period

/** ファウルの記号・フリースローの数・相殺を選ぶ。[playerId] が null ならヘッドコーチのファウル */
@Composable
fun FoulDialog(
    title: String,
    coach: Boolean,
    manToMan: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (code: FoulCode, freeThrows: Int, cancelled: Boolean, offensive: Boolean) -> Unit,
) {
    val codes = if (coach) listOfNotNull(FoulCode.C, FoulCode.B, FoulCode.M.takeIf { manToMan }, FoulCode.D, FoulCode.F)
    else listOf(FoulCode.P, FoulCode.T, FoulCode.U, FoulCode.D, FoulCode.F)
    var code by remember { mutableStateOf(codes.first()) }
    var offensive by remember { mutableStateOf(false) }
    var freeThrows by remember { mutableStateOf(0) }
    var cancelled by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("記号")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    codes.forEach { c ->
                        FilterChip(selected = code == c && !offensive, onClick = { code = c; offensive = false }, label = { Text(c.label) })
                    }
                    if (!coach) {
                        FilterChip(selected = offensive, onClick = { code = FoulCode.P; offensive = true; freeThrows = 0 }, label = { Text("OF") })
                    }
                }
                Text("フリースローの数")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { n ->
                        FilterChip(selected = freeThrows == n, onClick = { freeThrows = n }, label = { Text(if (n == 0) "なし" else "$n 本") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { cancelled = !cancelled }) {
                    Checkbox(checked = cancelled, onCheckedChange = { cancelled = it })
                    Text("罰則が相殺された（c）")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(code, freeThrows, cancelled, offensive) }) { Text("記録する") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

/** タイムアウトの欄に書く分（そのピリオドの経過時間）を選ぶ。時計を使うときは 5 段目で自動にする */
@Composable
fun TimeoutMinuteDialog(title: String, periodMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("そのピリオドの経過時間（分）を選びます。例: 10 分のクォーターで残り 4:32 → 6")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..periodMinutes).forEach { m ->
                        OutlinedButton(onClick = { onConfirm(m) }, modifier = Modifier.size(width = 72.dp, height = 52.dp)) { Text("$m") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

/** 試合の初め: ジャンプボールでボールを取ったチームを選ぶ（アローは相手に向く） */
@Composable
fun JumpBallDialog(setup: GameSetup, onDismiss: () -> Unit, onConfirm: (Side) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("試合を始める") },
        text = { Text("ジャンプボールでボールを取ったチームを選びます。ポゼッションアローは相手のチームに向きます") },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Side.entries.forEach { side -> OutlinedButton(onClick = { onConfirm(side) }) { Text(EventText.teamLabel(setup, side)) } }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

/** 交代（両チームまとめても可。F-307・F-308）。出る選手と入る選手を、押した順に組にする */
@Composable
fun SubstitutionDialog(setup: GameSetup, state: GameState, onDismiss: () -> Unit, onConfirm: (List<SubChange>) -> Unit) {
    val outs = remember { Side.entries.associateWith { mutableStateListOf<String>() } }
    val ins = remember { Side.entries.associateWith { mutableStateListOf<String>() } }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("交代") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("出る選手と入る選手を押します（押した順に組になります）。両チームまとめて交代できます")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Side.entries.forEach { side ->
                    val team = state.team(side)
                    Text(EventText.teamLabel(setup, side), style = MaterialTheme.typography.titleMedium)
                    Text("出る（コート上）")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        team.onCourt.forEach { id ->
                            val list = outs.getValue(side)
                            FilterChip(selected = id in list, onClick = { if (id in list) list.remove(id) else list.add(id) }, label = { Text(EventText.number(setup, side, id)) })
                        }
                    }
                    Text("入る（ベンチ）")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        setup.team(side).players.filter { it.id !in team.onCourt && !team.isOut(it.id) }.forEach { p ->
                            val list = ins.getValue(side)
                            FilterChip(selected = p.id in list, onClick = { if (p.id in list) list.remove(p.id) else list.add(p.id) }, label = { Text("#${p.number}") })
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val bad = Side.entries.firstOrNull { outs.getValue(it).size != ins.getValue(it).size }
                if (bad != null) {
                    error = "${EventText.teamLabel(setup, bad)}: 出る人数と入る人数をそろえてください"
                } else {
                    onConfirm(Side.entries.flatMap { side -> outs.getValue(side).zip(ins.getValue(side)) { o, i -> SubChange(side, o, i) } })
                }
            }) { Text("交代する") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

/** 前半の終わりの照合（F-311）: 選手とコーチのファウルの数、合計得点 */
@Composable
fun HalfTimeDialog(setup: GameSetup, state: GameState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("前半の照合") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("両チームのスコアシートと、ファウルの数・合計得点を照らし合わせます。後半の最初にスローインするチームも確かめます")
                Side.entries.forEach { side ->
                    val team = state.team(side)
                    Text("${EventText.teamLabel(setup, side)}　${team.score} 点", style = MaterialTheme.typography.titleMedium)
                    setup.team(side).players.filter { team.fouls(it.id).isNotEmpty() }.forEach { p ->
                        Text("#${p.number} ${p.name}　ファウル ${team.fouls(p.id).size}（${team.fouls(p.id).joinToString(" ") { it.text }}）")
                    }
                    if (team.coachFouls.isNotEmpty()) Text("コーチ　${team.coachFouls.joinToString(" ") { it.text }}")
                }
                state.arrow?.let { Text("アロー: ${EventText.teamLabel(setup, it)}") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("確認しました") } },
    )
}

/** 記録を直す・消す（F-312）。選手を直すのは「？」で仮に入力したものにも使う */
@Composable
fun EditEventDialog(setup: GameSetup, stored: StoredEvent, onDismiss: () -> Unit, onSave: (GameEvent) -> Unit, onDelete: () -> Unit) {
    var event by remember(stored) { mutableStateOf(stored.event) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Period.label(event.period)}　記録を直す") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(EventText.describe(setup, event), style = MaterialTheme.typography.titleMedium)
                val e = event
                val playerSide: Side? = when (e) {
                    is Shot -> e.side
                    is Foul -> e.side.takeIf { e.offender == Offender.PLAYER }
                    is Rebound -> e.side
                    is Turnover -> e.side
                    else -> null
                }
                if (playerSide != null) {
                    val currentId = when (e) {
                        is Shot -> e.playerId
                        is Foul -> e.playerId
                        is Rebound -> e.playerId
                        is Turnover -> e.playerId
                        else -> null
                    }
                    Text("選手")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (setup.team(playerSide).players.map { it.id to "#${it.number}" } + (RosterPlayer.UNKNOWN to "？")).forEach { (id, label) ->
                            FilterChip(selected = currentId == id, onClick = { event = withPlayer(e, id) }, label = { Text(label) })
                        }
                    }
                }
                when (e) {
                    is Shot -> {
                        Text("得点")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShotKind.entries.forEach { k -> FilterChip(selected = e.kind == k, onClick = { event = e.copy(kind = k) }, label = { Text("${k.points} 点") }) }
                        }
                    }
                    is Foul -> {
                        Text("記号・フリースロー")
                        val codes = if (e.offender == Offender.HEAD_COACH) listOf(FoulCode.C, FoulCode.B, FoulCode.M, FoulCode.D, FoulCode.F)
                        else listOf(FoulCode.P, FoulCode.T, FoulCode.U, FoulCode.D, FoulCode.F)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            codes.forEach { c -> FilterChip(selected = e.code == c, onClick = { event = e.copy(code = c, offensive = false) }, label = { Text(c.label) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (0..3).forEach { n -> FilterChip(selected = e.freeThrows == n, onClick = { event = e.copy(freeThrows = n) }, label = { Text("$n") }) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = e.cancelled, onCheckedChange = { event = e.copy(cancelled = it) })
                            Text("相殺（c）")
                            Checkbox(checked = e.offensive, onCheckedChange = { event = e.copy(offensive = it, code = FoulCode.P) })
                            Text("OF")
                        }
                    }
                    is Timeout -> {
                        Text("経過時間（分）")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..(setup.rules.periodMillis(e.period) / 60_000).toInt()).forEach { m ->
                                FilterChip(selected = e.minute == m, onClick = { event = e.copy(minute = m) }, label = { Text("$m") })
                            }
                        }
                    }
                    else -> Unit
                }
                HorizontalDivider()
                if (confirmDelete) {
                    Text("この記録を消します。よろしいですか", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDelete) { Text("消す") }
                        OutlinedButton(onClick = { confirmDelete = false }) { Text("やめる") }
                    }
                } else {
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 4.dp).width(160.dp)) { Text("この記録を消す") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(event) }, enabled = event != stored.event) { Text("直す") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

private fun withPlayer(event: GameEvent, playerId: String): GameEvent = when (event) {
    is Shot -> event.copy(playerId = playerId)
    is Foul -> event.copy(playerId = playerId)
    is Rebound -> event.copy(playerId = playerId)
    is Turnover -> event.copy(playerId = playerId)
    else -> event
}

/** 確かめ（試合を終える・画面を閉じるなど） */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

