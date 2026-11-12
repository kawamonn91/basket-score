package com.ttech.basketscore.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ttech.basketscore.data.LoadedGame
import com.ttech.basketscore.game.ArrowSet
import com.ttech.basketscore.game.ClockFormat
import com.ttech.basketscore.game.Deflection
import com.ttech.basketscore.game.EventText
import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.GameEnd
import com.ttech.basketscore.game.GameEvent
import com.ttech.basketscore.game.JumpBallSituation
import com.ttech.basketscore.game.PeriodEnd
import com.ttech.basketscore.game.PeriodStart
import com.ttech.basketscore.game.PossessionChange
import com.ttech.basketscore.game.Rebound
import com.ttech.basketscore.game.RosterPlayer
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.Substitution
import com.ttech.basketscore.game.Timeout
import com.ttech.basketscore.game.Turnover
import com.ttech.basketscore.rules.Period
import com.ttech.basketscore.stats.GameAnalysis
import com.ttech.basketscore.stats.StatLine
import com.ttech.basketscore.stats.TeamAdvanced
import java.io.File

/**
 * CSV（F-903）: 全記録・ボックススコア・集計の 3 つ。表計算で開けるよう、UTF-8（BOM 付き）で書く。
 * アプリのキャッシュの export/ に置き、FileProvider で共有する
 */
object CsvExport {

    /** 表計算ソフトが UTF-8 と分かるように、先頭に付ける印（BOM） */
    private val BOM = Char(0xFEFF).toString()

    /** 1 つの欄（カンマ・改行・ダブルクォートを含むときは囲む） */
    fun cell(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun line(vararg values: Any?): String = values.joinToString(",") { cell(it) }

    fun kind(e: GameEvent): String = when (e) {
        is PeriodStart -> "ピリオド開始"
        is PeriodEnd -> "ピリオド終了"
        is Shot -> "シュート"
        is Foul -> "ファウル"
        is Timeout -> "タイムアウト"
        is Substitution -> "交代"
        is ArrowSet -> "アロー"
        is JumpBallSituation -> "ジャンプボール"
        is Rebound -> "リバウンド"
        is Turnover -> "ターンオーバー"
        is Deflection -> "ディフレクション"
        is PossessionChange -> "攻守交代"
        is GameEnd -> "試合終了"
    }

    /** 全記録（Play by Play） */
    fun events(g: LoadedGame): String {
        val setup = g.setup
        var a = 0
        var b = 0
        val rows = g.events.mapIndexed { i, stored ->
            val e = stored.event
            if (e is Shot && e.made) if (e.side == Side.A) a += e.kind.points else b += e.kind.points
            val side = EventText.sideOf(e)
            line(
                i + 1, Period.label(e.period), e.clockMillis?.let { ClockFormat.stamp(it) } ?: "", kind(e),
                side?.let { setup.team(it).name } ?: "", EventText.describe(setup, e),
                (e as? Shot)?.x?.let { "%.3f".format(it) } ?: "", (e as? Shot)?.y?.let { "%.3f".format(it) } ?: "", a, b,
            )
        }
        return (listOf(line("番号", "ピリオド", "時刻（残り）", "種類", "チーム", "内容", "位置 x", "位置 y", "得点 A", "得点 B")) + rows).joinToString("\r\n")
    }

    /** ボックススコア（選手・チーム・合計） */
    fun boxScore(g: LoadedGame, a: GameAnalysis): String {
        val setup = g.setup
        val header = line(
            "チーム", "背番号", "氏名", "GS", "出場（分:秒）", "PTS", "FGM", "FGA", "2PM", "2PA", "3PM", "3PA", "FTM", "FTA",
            "OR", "DR", "REB", "AST", "STL", "BLK", "TO", "DEFL", "PF", "TF", "OF", "DK", "FO", "DQ", "+/-", "EFF",
        )
        fun row(team: String, number: String, name: String, l: StatLine) = line(
            team, number, name, l.gs, l.minutes?.let { "%d:%02d".format(it / 60_000, it / 1000 % 60) } ?: "", l.pts, l.fgm, l.fga,
            l.twoM, l.twoA, l.tpm, l.tpa, l.ftm, l.fta, l.or, l.dr, l.reb, l.ast, l.stl, l.blk, l.tov, l.defl, l.pf, l.tf, l.of, l.dk,
            l.fo, l.dq, l.plusMinus, l.eff,
        )
        val rows = Side.entries.flatMap { side ->
            val team = setup.team(side)
            val box = a.box.getValue(side)
            team.players.map { p -> row(team.name, p.number, p.name, box.player(p.id)) } +
                listOfNotNull(box.players[RosterPlayer.UNKNOWN]?.let { row(team.name, "?", "未確定", it) }) +
                row(team.name, "", "チーム", box.team) + row(team.name, "", "計", box.total)
        }
        return (listOf(header) + rows).joinToString("\r\n")
    }

    /** 集計（クォーターの得点とチームの指標） */
    fun summary(g: LoadedGame, a: GameAnalysis): String {
        val setup = g.setup
        val periods = (1..maxOf(g.state.period, 4)).toList()
        val lines = mutableListOf(line("項目", setup.teamA.name, setup.teamB.name))
        periods.forEach { p -> lines += line(Period.label(p), g.state.a.periodPoints[p] ?: 0, g.state.b.periodPoints[p] ?: 0) }
        lines += line("合計", g.state.a.score, g.state.b.score)
        fun adv(label: String, f: (TeamAdvanced) -> Any?) = line(label, f(a.advanced.getValue(Side.A)), f(a.advanced.getValue(Side.B)))
        fun d(v: Double?) = v?.let { "%.4f".format(it) } ?: ""
        lines += adv("攻撃回数") { it.possessions }
        lines += adv("PACE") { d(it.pace) }
        lines += adv("平均の攻撃時間（秒）") { it.averagePossessionMillis?.let { ms -> "%.1f".format(ms / 1000.0) } ?: "" }
        lines += adv("PPP") { d(it.ppp) }
        lines += adv("eFG%") { d(it.efg) }
        lines += adv("TS%") { d(it.ts) }
        lines += adv("TO%") { d(it.toRate) }
        lines += adv("FTR") { d(it.ftr) }
        lines += adv("ORB%") { d(it.orbPercent) }
        lines += adv("FTM/PTS") { d(it.ftmPerPts) }
        lines += adv("AST Rate") { d(it.astRate) }
        lines += adv("AST/TO") { d(it.astPerTo) }
        lines += adv("3PA/FGA") { d(it.threeRate) }
        lines += adv("セカンドチャンスポイント") { it.secondChancePoints }
        lines += adv("TO からの得点") { it.pointsOffTurnovers }
        a.runs.forEach { run -> lines += line("ラン", if (run.side == Side.A) "${run.points}-0（${Period.label(run.startPeriod)}）" else "", if (run.side == Side.B) "${run.points}-0（${Period.label(run.startPeriod)}）" else "") }
        return lines.joinToString("\r\n")
    }

    /** 3 つの CSV を書き出す */
    fun write(context: Context, g: LoadedGame, a: GameAnalysis): List<File> {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val base = "basket-score-${g.entity.id}"
        return listOf(
            "$base-events.csv" to events(g),
            "$base-boxscore.csv" to boxScore(g, a),
            "$base-summary.csv" to summary(g, a),
        ).map { (name, body) ->
            File(dir, name).apply { writeText(BOM + body + "\r\n", Charsets.UTF_8) }
        }
    }

    fun shareIntent(context: Context, files: List<File>, title: String): Intent {
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }
}
