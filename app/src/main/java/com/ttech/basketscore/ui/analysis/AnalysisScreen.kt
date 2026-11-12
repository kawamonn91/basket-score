package com.ttech.basketscore.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttech.basketscore.data.LoadedGame
import com.ttech.basketscore.game.ClockFormat
import com.ttech.basketscore.game.EventText
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.RosterPlayer
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.graph
import com.ttech.basketscore.report.AnalysisPdf
import com.ttech.basketscore.report.CsvExport
import com.ttech.basketscore.report.Charts
import com.ttech.basketscore.rules.Period
import com.ttech.basketscore.stats.Advanced
import com.ttech.basketscore.stats.Expected
import com.ttech.basketscore.stats.GameAnalysis
import com.ttech.basketscore.stats.Glossary
import com.ttech.basketscore.stats.PlayTypeStats
import com.ttech.basketscore.stats.SegmentMode
import com.ttech.basketscore.stats.Segments
import com.ttech.basketscore.stats.StatLine
import com.ttech.basketscore.stats.TeamBox
import com.ttech.basketscore.stats.TypeLine
import com.ttech.basketscore.stats.Zones
import com.ttech.basketscore.ui.record.RecordViewModel
import com.ttech.basketscore.ui.record.teamColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 分析のタブ（試合の一覧から直接開くときの番号にも使う。F-1005） */
enum class AnalysisTab(val label: String) {
    RESULT("結果"),
    BOX("ボックススコア"),
    FLOW("ゲームフロー"),
    SHOTS("シュートチャート"),
    ZONES("エリア"),
    SEGMENTS("時間帯"),
    ADVANCED("アドバンスド"),
    LINEUPS("ラインナップ"),
    TYPES("種別"),
    SCORES("得点時系列"),
    GLOSSARY("用語解説"),
}

/** 試合の分析（docs/design.md の 3.4。F-601〜F-616） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(gameId: Long, initialTab: Int, onBack: () -> Unit, onOpenScoresheet: () -> Unit, onOpenPlayByPlay: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val vm = viewModel(key = "record-$gameId") { RecordViewModel(graph.games, gameId) }
    val game by vm.game.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, AnalysisTab.entries.size - 1)) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val g = game
    val analysis by produceState<GameAnalysis?>(null, g?.events, g?.setup) {
        value = g?.let { withContext(Dispatchers.Default) { GameAnalysis.build(it.setup, it.events.map { e -> e.event }, it.state) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(g?.let { "分析　${it.setup.teamA.name} ${it.state.a.score} - ${it.state.b.score} ${it.setup.teamB.name}" } ?: "分析", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
                actions = {
                    IconButton(onClick = onOpenScoresheet) { Icon(Icons.Filled.Description, contentDescription = "スコアシート") }
                    IconButton(onClick = onOpenPlayByPlay) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "記録の一覧") }
                    val a = analysis
                    IconButton(enabled = g != null && a != null && !busy, onClick = {
                        if (g == null || a == null) return@IconButton
                        busy = true
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { AnalysisPdf.write(context, g, a) }
                            context.startActivity(AnalysisPdf.shareIntent(context, file, "分析 ${g.setup.teamA.name} vs ${g.setup.teamB.name}"))
                            busy = false
                        }
                    }) { Icon(Icons.Filled.PictureAsPdf, contentDescription = "分析の PDF を共有") }
                    IconButton(enabled = g != null && a != null && !busy, onClick = {
                        if (g == null || a == null) return@IconButton
                        busy = true
                        scope.launch {
                            val files = withContext(Dispatchers.IO) { CsvExport.write(context, g, a) }
                            context.startActivity(CsvExport.shareIntent(context, files, "記録 ${g.setup.teamA.name} vs ${g.setup.teamB.name}"))
                            busy = false
                        }
                    }) { Icon(Icons.Filled.TableChart, contentDescription = "CSV を共有") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                AnalysisTab.entries.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.label) }) }
            }
            val a = analysis
            if (g == null || a == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 1400.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (AnalysisTab.entries[tab]) {
                        AnalysisTab.RESULT -> ResultTab(g, a)
                        AnalysisTab.BOX -> BoxTab(g, a)
                        AnalysisTab.FLOW -> FlowTab(g, a)
                        AnalysisTab.SHOTS -> ShotsTab(g, a)
                        AnalysisTab.ZONES -> ZonesTab(g, a)
                        AnalysisTab.SEGMENTS -> SegmentsTab(g)
                        AnalysisTab.ADVANCED -> AdvancedTab(g, a)
                        AnalysisTab.LINEUPS -> LineupsTab(g, a)
                        AnalysisTab.TYPES -> TypesTab(g)
                        AnalysisTab.SCORES -> ScoresTab(g, a)
                        AnalysisTab.GLOSSARY -> GlossaryTab()
                    }
                }
            }
        }
    }
}

private fun name(setup: GameSetup, side: Side): String = EventText.teamLabel(setup, side)

private fun numberOf(setup: GameSetup, side: Side, id: String): String =
    if (id == RosterPlayer.UNKNOWN) "？" else setup.team(side).player(id)?.number ?: "?"

@Composable
private fun SideChips(setup: GameSetup, side: Side, onChange: (Side) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Side.entries.forEach { s -> FilterChip(selected = side == s, onClick = { onChange(s) }, label = { Text(name(setup, s)) }) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

// --- 結果 ---

@Composable
private fun ResultTab(g: LoadedGame, a: GameAnalysis) {
    val setup = g.setup
    val state = g.state
    val periods = (1..maxOf(state.period, Period.QUARTERS)).toList()
    SectionTitle(if (state.finished) "最終結果" else "途中経過（${Period.label(state.period.coerceAtLeast(1))}）")
    DataTable(
        headers = listOf("チーム") + periods.map { Period.label(it) } + "計",
        rows = Side.entries.map { side ->
            val team = state.team(side)
            TableRow(listOf(name(setup, side)) + periods.map { "${team.periodPoints[it] ?: 0}" } + "${team.score}", bold = state.winner() == side, color = teamColor(setup, side))
        },
        widths = listOf(180.dp, 64.dp),
    )
    state.winner()?.let { Text("勝者: ${setup.team(it).name}", style = MaterialTheme.typography.titleMedium) }
    SectionTitle("チームの比較")
    val ta = a.box.getValue(Side.A).total
    val tb = a.box.getValue(Side.B).total
    val adv = a.advanced
    fun row(label: String, f: (StatLine) -> String) = TableRow(listOf(label, f(ta), f(tb)))
    fun advRow(label: String, f: (com.ttech.basketscore.stats.TeamAdvanced) -> String) = TableRow(listOf(label, f(adv.getValue(Side.A)), f(adv.getValue(Side.B))))
    val maxLead = Side.entries.associateWith { side -> a.scores.maxOfOrNull { if (side == Side.A) it.margin else -it.margin }?.coerceAtLeast(0) ?: 0 }
    DataTable(
        headers = listOf("", name(setup, Side.A), name(setup, Side.B)),
        rows = listOf(
            row("得点") { "${it.pts}" },
            row("FG") { "${ma(it.fgm, it.fga)}（${pct(rate(it.fgm, it.fga))}）" },
            row("2P") { "${ma(it.twoM, it.twoA)}（${pct(rate(it.twoM, it.twoA))}）" },
            row("3P") { "${ma(it.tpm, it.tpa)}（${pct(rate(it.tpm, it.tpa))}）" },
            row("FT") { "${ma(it.ftm, it.fta)}（${pct(rate(it.ftm, it.fta))}）" },
            row("リバウンド（OR / DR）") { "${it.reb}（${it.or} / ${it.dr}）" },
            row("アシスト") { "${it.ast}" },
            row("スティール") { "${it.stl}" },
            row("ブロック") { "${it.blk}" },
            row("ターンオーバー") { "${it.tov}" },
            row("ファウル") { "${it.pf}" },
            advRow("攻撃回数") { "${it.possessions}" },
            advRow("PPP") { dec(it.ppp) },
            advRow("平均の攻撃時間") { it.averagePossessionMillis?.let { ms -> "%.1f 秒".format(ms / 1000.0) } ?: "—" },
            advRow("セカンドチャンスポイント") { "${it.secondChancePoints}" },
            advRow("TO からの得点") { "${it.pointsOffTurnovers}" },
            TableRow(listOf("最大リード", "${maxLead[Side.A]}", "${maxLead[Side.B]}")),
        ),
        widths = listOf(220.dp, 200.dp),
    )
    if (a.runs.isNotEmpty()) {
        SectionTitle("ラン（8 点以上の連続得点）")
        a.runs.forEach { run ->
            Text(
                "${name(setup, run.side)} ${run.points}-0（${Period.label(run.startPeriod)}" + (if (run.endPeriod != run.startPeriod) "〜${Period.label(run.endPeriod)}" else "") + "）",
                color = teamColor(setup, run.side),
            )
        }
    }
}

// --- ボックススコア（F-601） ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxTab(g: LoadedGame, a: GameAnalysis) {
    val setup = g.setup
    var side by rememberSaveable { mutableStateOf(Side.A) }
    var period by rememberSaveable { mutableIntStateOf(0) }
    var showDnp by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        SideChips(setup, side) { side = it }
        Text("　")
        FilterChip(selected = period == 0, onClick = { period = 0 }, label = { Text("全体") })
        a.quarterBox.keys.forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(Period.label(p)) }) }
        Text("　")
        FilterChip(selected = showDnp, onClick = { showDnp = !showDnp }, label = { Text("出場しなかった選手（DNP）も") })
    }
    val box = if (period == 0) a.box.getValue(side) else a.quarterBox[period]?.getValue(side) ?: a.box.getValue(side)
    BoxTable(setup, side, box, showDnp)
}

@Composable
fun BoxTable(setup: GameSetup, side: Side, box: TeamBox, showDnp: Boolean) {
    val headers = listOf("選手", "GS", "MIN", "PTS", "2P", "3P", "FT", "FG%", "OR", "DR", "REB", "AST", "STL", "BLK", "TO", "DEF", "PF", "TF", "OF", "DK", "+/-", "EFF")
    fun cells(label: String, l: StatLine, total: Boolean = false) = listOf(
        label, if (!total && l.gs > 0) "○" else "", if (total) "" else minutes(l.minutes), "${l.pts}", ma(l.twoM, l.twoA), ma(l.tpm, l.tpa), ma(l.ftm, l.fta),
        pct(rate(l.fgm, l.fga)), "${l.or}", "${l.dr}", "${l.reb}", "${l.ast}", "${l.stl}", "${l.blk}", "${l.tov}", "${l.defl}",
        "${l.pf}", "${l.tf}", "${l.of}", "${l.dk}", if (l.plusMinus > 0) "+${l.plusMinus}" else "${l.plusMinus}", "${l.eff}",
    )
    val rows = setup.team(side).players.mapNotNull { p ->
        val l = box.player(p.id)
        if (!showDnp && !l.played && l == StatLine(gs = l.gs, minutes = l.minutes)) return@mapNotNull null
        val mark = when {
            l.dq > 0 -> "（DQ）"
            l.fo > 0 -> "（FO）"
            !l.played -> "（DNP）"
            else -> ""
        }
        TableRow(cells("#${p.number} ${p.name}$mark", l))
    } + listOfNotNull(box.players[RosterPlayer.UNKNOWN]?.let { TableRow(cells("？（未確定）", it)) }) +
        TableRow(cells("チーム", box.team, total = true).let { it.dropLast(2) + listOf("", "") }) +
        TableRow(cells("計", box.total, total = true).let { it.dropLast(2) + listOf("", "${box.total.eff}") }, bold = true)
    DataTable(headers, rows, widths = listOf(170.dp, 40.dp, 60.dp, 48.dp, 56.dp))
}

// --- ゲームフロー（F-602） ---

@Composable
private fun FlowTab(g: LoadedGame, a: GameAnalysis) {
    val setup = g.setup
    val colorA = teamColor(setup, Side.A).toArgb()
    val colorB = teamColor(setup, Side.B).toArgb()
    SectionTitle("点差の推移（上が ${name(setup, Side.A)} のリード）")
    if (!setup.useClock) Text("時計を使わない試合のため、横軸は得点の順です", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().aspectRatio(2.4f)) {
        drawIntoCanvas {
            Charts.flow(
                it.nativeCanvas, android.graphics.RectF(0f, 0f, size.width, size.height), setup, g.state.period,
                a.scores, a.possessions, a.markers, a.runs, colorA, colorB, labelSize = 13.dp.toPx(),
                nameA = name(setup, Side.A), nameB = name(setup, Side.B),
                endPeriod = g.state.period, endClock = if (g.state.finished || !g.state.periodRunning) 0 else g.state.lastClockMillis,
            )
        }
    }
    Text("上の帯: ボールを持っていたチーム　▼: タイムアウト　色の付いた区間: ラン", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// --- シュートチャート（F-603） ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShotsTab(g: LoadedGame, a: GameAnalysis) {
    val setup = g.setup
    var side by rememberSaveable { mutableStateOf(Side.A) }
    var player by rememberSaveable { mutableStateOf<String?>(null) }
    var scope by rememberSaveable { mutableIntStateOf(0) } // 0: 全体、-1: 前半、-2: 後半、1〜: ピリオド
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    val all = a.shots.filter { it.side == side && it.kind != com.ttech.basketscore.game.ShotKind.FT && it.x != null }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SideChips(setup, side) { side = it; player = null }
        Text("　")
        listOf(0 to "全体", -1 to "前半", -2 to "後半").forEach { (k, l) -> FilterChip(selected = scope == k, onClick = { scope = k }, label = { Text(l) }) }
        (1..maxOf(g.state.period, 1)).forEach { p -> FilterChip(selected = scope == p, onClick = { scope = p }, label = { Text(Period.label(p)) }) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = player == null, onClick = { player = null }, label = { Text("全員") })
        setup.team(side).players.filter { p -> all.any { it.playerId == p.id } }.forEach { p ->
            FilterChip(selected = player == p.id, onClick = { player = p.id }, label = { Text("#${p.number}") })
        }
        val types = all.mapNotNull { it.shotType }.distinct()
        if (types.isNotEmpty()) {
            Text("　")
            FilterChip(selected = type == null, onClick = { type = null }, label = { Text("種別すべて") })
            types.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }) }
        }
    }
    val shots = all.filter { s ->
        (player == null || s.playerId == player) && (type == null || s.shotType == type) &&
            when (scope) {
                0 -> true
                -1 -> s.period <= 2
                -2 -> s.period >= 3
                else -> s.period == scope
            }
    }
    val made = shots.count { it.made }
    Text("FG ${ma(made, shots.size)}（${pct(rate(made, shots.size))}）　○ 成功（背番号を丸で囲む）　背番号だけ: 失敗", style = MaterialTheme.typography.bodyMedium)
    val color = teamColor(setup, side).toArgb()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth(0.5f).aspectRatio(15f / 14f)) {
            drawIntoCanvas {
                Charts.shots(it.nativeCanvas, android.graphics.RectF(0f, 0f, size.width, size.height), shots, { s -> numberOf(setup, s.side, s.playerId) }, { color })
            }
        }
    }
    if (a.shots.none { it.x != null }) Text("位置のあるシュートがありません（スタッツで記録した試合だけ）", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// --- エリア（F-604・F-609 の期待値） ---

@Composable
private fun ZonesTab(g: LoadedGame, a: GameAnalysis) {
    val setup = g.setup
    var side by rememberSaveable { mutableStateOf(Side.A) }
    var heat by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SideChips(setup, side) { side = it }
        Text("　")
        FilterChip(selected = !heat, onClick = { heat = false }, label = { Text("成功率（22 区域）") })
        FilterChip(selected = heat, onClick = { heat = true }, label = { Text("ヒートマップ") })
    }
    val shots = a.shots.filter { it.side == side }
    val lines = Zones.compute(shots)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth(0.5f).aspectRatio(15f / 14f)) {
            drawIntoCanvas {
                Charts.zones(it.nativeCanvas, android.graphics.RectF(0f, 0f, size.width, size.height), lines, heat, labelSize = 12.dp.toPx())
            }
        }
    }
    Text(
        if (heat) "丸の大きさ: 本数　色: 成功率（青 35% 未満 → 黄 50% 前後 → 赤 65% 以上）" else "色: 成功率（青 35% 未満 → 黄 50% 前後 → 赤 65% 以上）",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SectionTitle("期待値（シュート 1 本あたりの得点）")
    DataTable(
        headers = listOf("分類", "成功 / 試投", "成功率", "期待値"),
        rows = Expected.byCategory(shots).map { TableRow(listOf(it.category.label, ma(it.made, it.attempts), pct(it.percent), dec(it.expected))) },
        widths = listOf(180.dp, 120.dp),
    )
    SectionTitle("区域ごと")
    DataTable(
        headers = listOf("区域", "成功 / 試投", "成功率", "期待値"),
        rows = lines.filter { it.attempts > 0 }.map { TableRow(listOf(it.zone.label, ma(it.made, it.attempts), pct(it.percent), dec(it.expected))) },
        widths = listOf(180.dp, 120.dp),
    )
}

// --- 時間帯（F-605） ---

@Composable
private fun SegmentsTab(g: LoadedGame) {
    val setup = g.setup
    var mode by rememberSaveable { mutableStateOf(SegmentMode.HALF_QUARTER) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
    }
    if (!setup.useClock && mode != SegmentMode.QUARTER) Text("時計を使わない試合のため、Q ごとに分けて見てください", color = MaterialTheme.colorScheme.onSurfaceVariant)
    val segments = remember(g.events, mode) { Segments.compute(setup, g.events.map { it.event }, mode) }
    val na = name(setup, Side.A)
    val nb = name(setup, Side.B)
    DataTable(
        headers = listOf("時間帯", "$na 得点", "$nb 得点", "差", "$na FG", "$nb FG", "$na REB", "$nb REB", "$na TO", "$nb TO", "$na PF", "$nb PF"),
        rows = segments.map { s ->
            val d = s.a.pts - s.b.pts
            TableRow(
                listOf(
                    s.label, "${s.a.pts}", "${s.b.pts}", if (d > 0) "+$d" else "$d",
                    "${ma(s.a.fgm, s.a.fga)} ${pct(rate(s.a.fgm, s.a.fga))}", "${ma(s.b.fgm, s.b.fga)} ${pct(rate(s.b.fgm, s.b.fga))}",
                    "${s.a.reb}", "${s.b.reb}", "${s.a.tov}", "${s.b.tov}", "${s.a.pf}", "${s.b.pf}",
                ),
                color = if (d != 0) teamColor(setup, if (d > 0) Side.A else Side.B) else null,
            )
        },
        widths = listOf(150.dp, 140.dp, 140.dp, 56.dp, 170.dp, 170.dp, 130.dp),
    )
}

// --- アドバンスド（F-606〜F-609） ---

@Composable
private fun AdvancedTab(g: LoadedGame, a: GameAnalysis) {
    val setup = g.setup
    SectionTitle("チーム")
    val ta = a.advanced.getValue(Side.A)
    val tb = a.advanced.getValue(Side.B)
    fun row(label: String, f: (com.ttech.basketscore.stats.TeamAdvanced) -> String) = TableRow(listOf(label, f(ta), f(tb)))
    DataTable(
        headers = listOf("指標", name(setup, Side.A), name(setup, Side.B)),
        rows = listOf(
            row("攻撃回数（推定）") { "${it.possessions}（${"%.1f".format(it.estimatedPossessions)}）" },
            row("PACE") { it.pace?.let { p -> "%.1f".format(p) } ?: "—" },
            row("平均の攻撃時間") { it.averagePossessionMillis?.let { ms -> "%.1f 秒".format(ms / 1000.0) } ?: "—" },
            row("PPP（得点効率）") { dec(it.ppp) },
            row("eFG%") { pct(it.efg) },
            row("TS%") { pct(it.ts) },
            row("TO%") { pct(it.toRate) },
            row("FTR") { dec(it.ftr) },
            row("ORB%") { pct(it.orbPercent) },
            row("FTM / PTS") { pct(it.ftmPerPts) },
            row("AST Rate") { pct(it.astRate) },
            row("AST/TO") { dec(it.astPerTo) },
            row("3PA/FGA") { pct(it.threeRate) },
            row("セカンドチャンスポイント") { "${it.secondChancePoints}" },
            row("TO からの得点") { "${it.pointsOffTurnovers}" },
        ),
        widths = listOf(220.dp, 160.dp),
    )
    Side.entries.forEach { side ->
        SectionTitle("選手（${name(setup, side)}）")
        val box = a.box.getValue(side)
        val total = box.total
        val teamMinutes = box.players.values.mapNotNull { it.minutes }.takeIf { it.isNotEmpty() }?.sum()
        DataTable(
            headers = listOf("選手", "MIN", "eFG%", "TS%", "USG%", "AST%", "AST/TO", "GmSc", "PTS/40", "FGA/40", "REB/40", "TO/40"),
            rows = setup.team(side).players.filter { box.player(it.id).played }.map { p ->
                val l = box.player(p.id)
                val adv = Advanced.player(setup, l, total, teamMinutes)
                TableRow(
                    listOf("#${p.number} ${p.name}", minutes(l.minutes), pct(adv.efg), pct(adv.ts), pct(adv.usage), pct(adv.astPercent), dec(adv.astPerTo), "%.1f".format(adv.gameScore)) +
                        (adv.per40?.map { "%.1f".format(it) } ?: List(4) { "—" }),
                )
            },
            widths = listOf(170.dp, 64.dp, 72.dp),
        )
    }
    Text("40 分あたりは、規定の試合の長さ（U15 は 32 分）にそろえた値です。指標の意味は「用語解説」に", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// --- ラインナップ（F-610） ---

@Composable
private fun LineupsTab(g: LoadedGame, a: GameAnalysis) {
    val setup = g.setup
    if (!setup.useClock) Text("時計を使わない試合のため、時間は数えません", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Side.entries.forEach { side ->
        SectionTitle("${name(setup, side)} の 5 人の組み合わせ")
        DataTable(
            headers = listOf("5 人", "時間", "得点", "失点", "+/-"),
            rows = a.lineups.filter { it.side == side }.map { l ->
                TableRow(
                    listOf(l.players.joinToString(" ") { "#" + numberOf(setup, side, it) }, minutes(l.millis), "${l.pointsFor}", "${l.pointsAgainst}", if (l.plusMinus > 0) "+${l.plusMinus}" else "${l.plusMinus}"),
                )
            },
            widths = listOf(260.dp, 80.dp),
        )
    }
}

// --- 種別（F-611） ---

@Composable
private fun TypesTab(g: LoadedGame) {
    val setup = g.setup
    val events = remember(g.events) { g.events.map { it.event } }
    var side by rememberSaveable { mutableStateOf(Side.A) }
    SideChips(setup, side) { side = it }
    @Composable
    fun table(title: String, lines: List<TypeLine>, pointsLabel: String) {
        SectionTitle(title)
        if (lines.isEmpty()) {
            Text("記録がありません（「公式＋スタッツ＋種別」で記録した試合だけ）", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        DataTable(
            headers = listOf("種別", "回数", pointsLabel, "PPP", "FG", "TO"),
            rows = lines.map { TableRow(listOf(it.type, "${it.plays}", "${it.points}", dec(it.ppp), "${ma(it.fgm, it.fga)} ${pct(rate(it.fgm, it.fga))}", "${it.turnovers}")) },
            widths = listOf(160.dp, 72.dp, 72.dp, 72.dp, 130.dp),
        )
    }
    table("オフェンスの種別", PlayTypeStats.offense(events, side), "得点")
    table("ディフェンスの種別（相手の攻撃）", PlayTypeStats.defense(events, side), "失点")
    table("シュートの種別", PlayTypeStats.shotTypes(events, side), "得点")
}

// --- 得点時系列（F-616） ---

@Composable
private fun ScoresTab(g: LoadedGame, a: GameAnalysis) {
    val setup = g.setup
    DataTable(
        headers = listOf("時刻", name(setup, Side.A), "スコア", name(setup, Side.B)),
        rows = a.scores.map { s ->
            val who = "#${numberOf(setup, s.side, s.playerId)} ${s.kind.label}（+${s.points}）"
            TableRow(
                listOf(ClockFormat.at(s.period, s.clock), if (s.side == Side.A) who else "", "${s.scoreA} - ${s.scoreB}", if (s.side == Side.B) who else ""),
                color = teamColor(setup, s.side),
            )
        },
        widths = listOf(110.dp, 200.dp, 110.dp, 200.dp),
    )
    if (a.scores.isEmpty()) Text("まだ得点がありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// --- 用語解説（F-615） ---

@Composable
private fun GlossaryTab() {
    Glossary.terms.forEach { t ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(t.meaning)
            Text("式: ${t.formula}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("目安: ${t.guide}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}

/** シュートの番号（PDF でも使う） */
fun shotNumber(setup: GameSetup, s: Shot): String = numberOf(setup, s.side, s.playerId)
