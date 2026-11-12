package com.ttech.basketscore.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.basketscore.data.LoadedGame
import com.ttech.basketscore.game.EventText
import com.ttech.basketscore.game.PlayTypes
import com.ttech.basketscore.game.RosterPlayer
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.stats.Court
import com.ttech.basketscore.stats.Sides
import com.ttech.basketscore.stats.StatLine
import com.ttech.basketscore.stats.TeamBox

/** 中央のスタッツ入力（コートの図・成功 / 失敗・続けて聞くこと・選んだ選手の操作） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsCenter(
    g: LoadedGame,
    stats: StatsInput,
    selected: Selection?,
    onScore: (Selection, ShotKind) -> Unit,
    onFoul: (Selection) -> Unit,
    onCancelSelection: () -> Unit,
) {
    val setup = g.setup
    val state = g.state
    val period = state.period.coerceAtLeast(1)
    val rightSide = Sides.attackerOfRightBasket(setup, period)
    val pending = stats.pending
    // 今のピリオドのシュートと、作りかけのシュートの印
    val marks = remember(g.events, pending) {
        val shots = g.events.map { it.event }.filterIsInstance<Shot>()
            .filter { it.period == period && it.kind != ShotKind.FT && it.x != null && it.y != null }
            .map { s ->
                val (x, y) = Court.toScreen(s.x!!.toDouble(), s.y!!.toDouble(), Sides.attacksRight(setup, s.side, period))
                CourtMark(x, y, teamColor(setup, s.side), s.made)
            }
        val p = pending?.let { pend ->
            val pos = when {
                pend.screenX != null && pend.screenY != null -> pend.screenX to pend.screenY
                pend.underBasket && pend.side != null ->
                    Court.toScreen(Court.UNDER_BASKET_X, Court.UNDER_BASKET_Y, Sides.attacksRight(setup, pend.side, period))
                else -> null
            }
            pos?.let { (x, y) -> CourtMark(x, y, Color.Black, made = false, pending = true) }
        }
        shots + listOfNotNull(p)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text("◀ ${EventText.teamLabel(setup, rightSide.other)} が攻める", color = teamColor(setup, rightSide.other), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${EventText.teamLabel(setup, rightSide)} が攻める ▶", color = teamColor(setup, rightSide), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        CourtView(
            marks = marks,
            leftColor = teamColor(setup, rightSide.other),
            rightColor = teamColor(setup, rightSide),
            onTap = stats::tapCourt,
        )
        // シュートの入力（どの順番でもよい）
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { stats.setMade(true) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.height(56.dp).width(108.dp),
            ) { Text("成功", fontSize = 20.sp) }
            Button(
                onClick = { stats.setMade(false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D4C41)),
                modifier = Modifier.height(56.dp).width(108.dp),
            ) { Text("失敗", fontSize = 20.sp) }
            FilledTonalButton(onClick = { stats.quick(PlayTypes.LAYUP) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("レイアップ") }
            FilledTonalButton(onClick = { stats.quick(PlayTypes.DUNK) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("ダンク") }
            FilterChip(selected = pending?.offensiveRebound == true, onClick = stats::toggleOffensiveRebound, label = { Text("OR から") })
        }
        when {
            pending != null -> PendingView(g, stats, pending)
            stats.prompt != null -> PromptView(g, stats, stats.prompt!!)
            selected != null -> PlayerActions(g, stats, selected, onScore, onFoul, onCancelSelection)
            else -> Text(
                "コートを押す・成功 / 失敗・選手を押す の 3 つを、どの順番でも入れるとシュートを記録します。" +
                    "選手を押すと、ファウル・リバウンド・ターンオーバーなども入れられます",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 作りかけのシュート */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingView(g: LoadedGame, stats: StatsInput, p: PendingShot) {
    val setup = g.setup
    val resolved = stats.resolved(p)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val who = if (p.side != null && p.playerId != null) {
                "${EventText.teamLabel(setup, p.side)} ${EventText.number(setup, p.side, p.playerId)}"
            } else {
                "選手 —（選手のボタンを押す）"
            }
            val where = when {
                p.underBasket -> "ゴール下（${p.shotType ?: ""}）"
                p.hasLocation -> "位置 ✓"
                else -> "位置 —（コートを押す）"
            }
            val result = when (p.made) {
                true -> "成功"
                false -> "失敗"
                null -> "成否 —"
            }
            Text("シュート: $who ／ $where ／ $result", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(ShotKind.TWO, ShotKind.THREE).forEach { k ->
                    FilterChip(selected = resolved?.third == k, onClick = { stats.setKind(k) }, label = { Text(k.label) })
                }
                Text(if (p.kind == null) "（位置から自動）" else "（手で選んだ）", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = stats::cancel) { Text("やめる") }
            }
            if (setup.recordsTypes) {
                TypeRow("シュート", PlayTypes.shot, p.shotType, stats::setShotType)
                TypeRow("オフェンス", PlayTypes.offense, p.offenseType, stats::setOffenseType)
                TypeRow("ディフェンス", PlayTypes.defense, p.defenseType, stats::setDefenseType)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeRow(label: String, options: List<String>, value: String?, onChange: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
        options.forEach { o -> FilterChip(selected = value == o, onClick = { onChange(if (value == o) null else o) }, label = { Text(o) }) }
    }
}

/** 記録のあとに続けて聞くこと（選手のボタンを押しても答えられる） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptView(g: LoadedGame, stats: StatsInput, prompt: Prompt) {
    val setup = g.setup
    val state = g.state
    fun players(side: Side) = state.team(side).onCourt.mapNotNull { id -> setup.team(side).player(id)?.let { id to "#${it.number}" } }

    @Composable
    fun Choices(side: Side, exclude: String? = null, team: String? = null, onPick: (String?) -> Unit) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            players(side).filter { it.first != exclude }.forEach { (id, label) ->
                OutlinedButton(onClick = { onPick(id) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(label, color = teamColor(setup, side), fontWeight = FontWeight.Bold) }
            }
            team?.let { OutlinedButton(onClick = { onPick(null) }) { Text(it) } }
        }
    }

    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (prompt) {
                is Prompt.Assist -> {
                    Text("アシストは？（${EventText.describe(setup, prompt.shot)}）", style = MaterialTheme.typography.titleMedium)
                    Choices(prompt.shot.side, exclude = prompt.shot.playerId, onPick = stats::answerAssist)
                }
                is Prompt.Rebound -> {
                    Text("リバウンドは？（${EventText.describe(setup, prompt.shot)}）", style = MaterialTheme.typography.titleMedium)
                    Side.entries.forEach { side ->
                        Choices(side, team = "${EventText.teamLabel(setup, side)} チーム") { id -> stats.answerRebound(side, id) }
                    }
                    if (prompt.shot.kind != ShotKind.FT) {
                        HorizontalDivider()
                        Text("ブロック" + (prompt.blockId?.let { "（${EventText.number(setup, prompt.shot.side.other, it)}）" } ?: ""), style = MaterialTheme.typography.labelLarge)
                        Choices(prompt.shot.side.other, onPick = stats::answerBlock)
                    }
                }
                is Prompt.FreeThrows -> {
                    val shooter = prompt.shooterId?.let { EventText.number(setup, prompt.side, it) } ?: "—（選手を押す）"
                    Text(
                        "${EventText.teamLabel(setup, prompt.side)} のフリースロー ${prompt.results.size + 1} / ${prompt.total} 本目　打つ人: $shooter",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (prompt.shooterId == null) {
                        Choices(prompt.side) { id -> if (id != null) stats.tapPlayer(prompt.side, id) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { stats.freeThrow(true) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("成功", fontSize = 18.sp) }
                            Button(onClick = { stats.freeThrow(false) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D4C41))) { Text("失敗", fontSize = 18.sp) }
                        }
                    }
                    if (prompt.results.isNotEmpty()) Text("ここまで: " + prompt.results.joinToString(" ") { if (it) "○" else "×" })
                }
                is Prompt.TurnoverType -> {
                    val who = prompt.playerId?.let { EventText.number(setup, prompt.side, it) } ?: "チーム"
                    Text("${EventText.teamLabel(setup, prompt.side)} $who のターンオーバーの種類は？", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlayTypes.turnover.forEach { t -> OutlinedButton(onClick = { stats.answerTurnoverType(t) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(t) } }
                        OutlinedButton(onClick = { stats.answerTurnoverType(null) }) { Text("選ばない") }
                    }
                }
                is Prompt.Steal -> {
                    Text("スティールした選手は？", style = MaterialTheme.typography.titleMedium)
                    Choices(prompt.side.other, onPick = stats::answerSteal)
                }
                is Prompt.LostBy -> {
                    Text("${EventText.number(setup, prompt.stealSide, prompt.stealerId)} のスティール。ボールを失った選手は？", style = MaterialTheme.typography.titleMedium)
                    Choices(prompt.stealSide.other, team = "分からない（チーム）", onPick = stats::answerLostBy)
                }
            }
            OutlinedButton(onClick = { if (prompt is Prompt.Steal) stats.answerSteal(null) else stats.skipPrompt() }) {
                Text(
                    when (prompt) {
                        is Prompt.Steal -> "スティールなし"
                        is Prompt.TurnoverType, is Prompt.LostBy -> "やめる"
                        else -> "飛ばす"
                    },
                )
            }
        }
    }
}

/** 選んだ選手の操作（公式の得点・ファウルと、スタッツ） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerActions(
    g: LoadedGame,
    stats: StatsInput,
    sel: Selection,
    onScore: (Selection, ShotKind) -> Unit,
    onFoul: (Selection) -> Unit,
    onCancel: () -> Unit,
) {
    val setup = g.setup
    val known = sel.playerId != RosterPlayer.UNKNOWN
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${EventText.teamLabel(setup, sel.side)} ${EventText.number(setup, sel.side, sel.playerId)}　コートを押すとシュート",
            style = MaterialTheme.typography.titleMedium,
            color = teamColor(setup, sel.side),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ShotKind.entries.forEach { k -> FilledTonalButton(onClick = { onScore(sel, k) }) { Text("${k.points} 点（位置なし）") } }
            Button(onClick = { onFoul(sel) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("ファウル") }
            FilledTonalButton(onClick = { stats.rebound(sel.side, sel.playerId); onCancel() }) { Text("リバウンド") }
            FilledTonalButton(onClick = { stats.startTurnover(sel.side, sel.playerId); onCancel() }) { Text("ターンオーバー") }
            if (known) {
                FilledTonalButton(onClick = { stats.startSteal(sel.side, sel.playerId); onCancel() }) { Text("スティール") }
                FilledTonalButton(onClick = { stats.deflection(sel.side, sel.playerId); onCancel() }) { Text("ディフレクション") }
            }
            OutlinedButton(onClick = onCancel) { Text("やめる") }
        }
    }
}

/** ミニボックススコア（F-410） */
@Composable
fun MiniBox(g: LoadedGame, side: Side, box: TeamBox) {
    val setup = g.setup
    val headers = listOf("#", "PTS", "FG", "3P", "FT", "REB", "AST", "STL", "BLK", "TO", "PF")
    fun cells(label: String, l: StatLine) = listOf(
        label, "${l.pts}", "${l.fgm}/${l.fga}", "${l.tpm}/${l.tpa}", "${l.ftm}/${l.fta}",
        "${l.reb}", "${l.ast}", "${l.stl}", "${l.blk}", "${l.tov}", "${l.pf}",
    )
    val rows = setup.team(side).players.map { p -> cells(p.number, box.player(p.id)) } +
        listOfNotNull(box.players[RosterPlayer.UNKNOWN]?.let { cells("？", it) }) +
        listOf(cells("チーム", box.team), cells("計", box.total))
    Column(Modifier.fillMaxWidth()) {
        Text("ボックススコア", style = MaterialTheme.typography.labelMedium)
        (listOf(headers) + rows).forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth()) {
                r.forEachIndexed { j, c ->
                    Text(
                        c,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (i == 0 || j == 0 || i == rows.size) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(if (j == 0) 1.4f else if (j in 2..4) 1.3f else 1f),
                    )
                }
            }
        }
    }
}
