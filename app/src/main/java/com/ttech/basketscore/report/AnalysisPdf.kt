package com.ttech.basketscore.report

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import com.ttech.basketscore.data.LoadedGame
import com.ttech.basketscore.game.EventText
import com.ttech.basketscore.game.RosterPlayer
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.rules.Period
import com.ttech.basketscore.sheet.ScoresheetPdf
import com.ttech.basketscore.stats.Advanced
import com.ttech.basketscore.stats.Expected
import com.ttech.basketscore.stats.GameAnalysis
import com.ttech.basketscore.stats.SegmentMode
import com.ttech.basketscore.stats.Segments
import com.ttech.basketscore.stats.StatLine
import com.ttech.basketscore.stats.TeamAdvanced
import com.ttech.basketscore.stats.Zones
import com.ttech.basketscore.ui.analysis.dec
import com.ttech.basketscore.ui.analysis.ma
import com.ttech.basketscore.ui.analysis.minutes
import com.ttech.basketscore.ui.analysis.pct
import com.ttech.basketscore.ui.analysis.rate
import com.ttech.basketscore.ui.record.teamColor
import java.io.File
import java.io.FileOutputStream

/**
 * 分析の PDF（F-902）: ボックススコア・ゲームフローチャート・シュートチャート・時間帯の分析・アドバンスドスタッツ。
 * A4 横（842 × 595 pt）
 */
object AnalysisPdf {
    private const val W = 842f
    private const val H = 595f
    private const val M = 28f

    private fun text(size: Float, bold: Boolean = false, color: Int = 0xFF212121.toInt(), align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            textAlign = align
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private val line = Paint().apply {
        color = 0xFFBDBDBD.toInt()
        strokeWidth = 0.5f
    }

    /** 表を描いて、描き終えた下の y を返す */
    private fun table(c: Canvas, x: Float, y: Float, widths: List<Float>, headers: List<String>, rows: List<List<String>>, size: Float = 7f, boldLast: Boolean = false): Float {
        val rowH = size * 1.7f
        var cy = y
        fun drawRow(cells: List<String>, bold: Boolean, shade: Boolean) {
            if (shade) c.drawRect(x, cy, x + widths.sum(), cy + rowH, Paint().apply { color = 0xFFEEEEEE.toInt() })
            var cx = x
            cells.forEachIndexed { i, s ->
                val w = widths.getOrElse(i) { widths.last() }
                val p = text(size, bold, align = if (i == 0) Paint.Align.LEFT else Paint.Align.CENTER)
                val tx = if (i == 0) cx + 2f else cx + w / 2
                // 長すぎる文字は切る
                var t = s
                while (t.length > 1 && p.measureText(t) > w - 3f) t = t.dropLast(1)
                c.drawText(t, tx, cy + rowH - size * 0.5f, p)
                cx += w
            }
            cy += rowH
            c.drawLine(x, cy, x + widths.sum(), cy, line)
        }
        drawRow(headers, bold = true, shade = true)
        rows.forEachIndexed { i, r -> drawRow(r, bold = boldLast && i == rows.lastIndex, shade = false) }
        return cy
    }

    private fun title(c: Canvas, g: LoadedGame, subtitle: String) {
        val s = g.setup
        val h = s.header
        c.drawText("${s.teamA.name} ${g.state.a.score} - ${g.state.b.score} ${s.teamB.name}", M, M + 14f, text(14f, bold = true))
        c.drawText(listOf(h.competition, h.date, h.venue).filter { it.isNotBlank() }.joinToString("　"), M, M + 28f, text(8f, color = 0xFF616161.toInt()))
        c.drawText(subtitle, W - M, M + 14f, text(11f, bold = true, align = Paint.Align.RIGHT))
    }

    private fun boxRows(g: LoadedGame, side: Side, a: GameAnalysis): List<List<String>> {
        val box = a.box.getValue(side)
        fun cells(label: String, l: StatLine, total: Boolean = false) = listOf(
            label, if (!total && l.gs > 0) "○" else "", if (total) "" else minutes(l.minutes), "${l.pts}", ma(l.twoM, l.twoA), ma(l.tpm, l.tpa), ma(l.ftm, l.fta),
            pct(rate(l.fgm, l.fga)), "${l.or}", "${l.dr}", "${l.reb}", "${l.ast}", "${l.stl}", "${l.blk}", "${l.tov}", "${l.defl}",
            "${l.pf}", "${l.tf}", "${l.of}", "${l.dk}", if (l.plusMinus > 0) "+${l.plusMinus}" else "${l.plusMinus}", "${l.eff}",
        )
        return g.setup.team(side).players.map { p ->
            val l = box.player(p.id)
            val mark = when {
                l.dq > 0 -> " DQ"
                l.fo > 0 -> " FO"
                !l.played -> " DNP"
                else -> ""
            }
            cells("#${p.number} ${p.name}$mark", l)
        } + listOfNotNull(box.players[RosterPlayer.UNKNOWN]?.let { cells("？", it) }) + listOf(cells("チーム", box.team, total = true), cells("計", box.total, total = true))
    }

    private val boxHeaders = listOf("選手", "GS", "MIN", "PTS", "2P", "3P", "FT", "FG%", "OR", "DR", "REB", "AST", "STL", "BLK", "TO", "DEF", "PF", "TF", "OF", "DK", "+/-", "EFF")
    private val boxWidths = listOf(118f, 20f, 34f, 28f, 38f, 38f, 38f, 40f) + List(14) { 30f }

    fun write(context: Context, g: LoadedGame, a: GameAnalysis): File {
        val setup = g.setup
        val file = ScoresheetPdf.file(context, "analysis-${g.entity.id}.pdf")
        val doc = PdfDocument()
        var pageNo = 0
        fun page(draw: (Canvas) -> Unit) {
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), ++pageNo).create())
            p.canvas.drawColor(android.graphics.Color.WHITE)
            draw(p.canvas)
            p.canvas.drawText("Basket Score　$pageNo", W - M, H - 12f, text(7f, color = 0xFF9E9E9E.toInt(), align = Paint.Align.RIGHT))
            doc.finishPage(p)
        }
        val colorA = teamColor(setup, Side.A).toArgb()
        val colorB = teamColor(setup, Side.B).toArgb()
        try {
            // 1・2 枚目: 結果とボックススコア
            Side.entries.forEach { side ->
                page { c ->
                    title(c, g, "ボックススコア（${setup.team(side).name}）")
                    var y = M + 40f
                    if (side == Side.A) {
                        val periods = (1..maxOf(g.state.period, Period.QUARTERS)).toList()
                        y = table(
                            c, M, y, listOf(150f) + List(periods.size + 1) { 40f },
                            listOf("チーム") + periods.map { Period.label(it) } + "計",
                            Side.entries.map { s -> listOf(setup.team(s).name) + periods.map { "${g.state.team(s).periodPoints[it] ?: 0}" } + "${g.state.team(s).score}" },
                            size = 8f,
                        ) + 12f
                    }
                    table(c, M, y, boxWidths, boxHeaders, boxRows(g, side, a), size = 7f, boldLast = true)
                }
            }
            // 3 枚目: ゲームフローと時間帯
            page { c ->
                title(c, g, "ゲームフロー・時間帯")
                Charts.flow(
                    c, RectF(M, M + 40f, W - M, M + 250f), setup, g.state.period, a.scores, a.possessions, a.markers, a.runs, colorA, colorB,
                    labelSize = 8f, nameA = setup.teamA.name, nameB = setup.teamB.name,
                    endPeriod = g.state.period, endClock = if (g.state.finished || !g.state.periodRunning) 0 else g.state.lastClockMillis,
                )
                val segments = Segments.compute(setup, g.events.map { it.event }, if (setup.useClock) SegmentMode.HALF_QUARTER else SegmentMode.QUARTER)
                val na = EventText.teamLabel(setup, Side.A)
                val nb = EventText.teamLabel(setup, Side.B)
                table(
                    c, M, M + 265f, listOf(90f, 50f, 50f, 40f, 80f, 80f, 45f, 45f, 45f, 45f, 45f, 45f),
                    listOf("時間帯", "$na 得点", "$nb 得点", "差", "$na FG", "$nb FG", "$na REB", "$nb REB", "$na TO", "$nb TO", "$na PF", "$nb PF"),
                    segments.map { s ->
                        val d = s.a.pts - s.b.pts
                        listOf(
                            s.label, "${s.a.pts}", "${s.b.pts}", if (d > 0) "+$d" else "$d",
                            "${ma(s.a.fgm, s.a.fga)} ${pct(rate(s.a.fgm, s.a.fga))}", "${ma(s.b.fgm, s.b.fga)} ${pct(rate(s.b.fgm, s.b.fga))}",
                            "${s.a.reb}", "${s.b.reb}", "${s.a.tov}", "${s.b.tov}", "${s.a.pf}", "${s.b.pf}",
                        )
                    },
                    size = 7f,
                )
            }
            // 4 枚目: シュートチャートと区域
            if (a.shots.any { it.x != null }) {
                page { c ->
                    title(c, g, "シュートチャート・エリア別の成功率")
                    val half = (W - 2 * M) / 4
                    Side.entries.forEachIndexed { i, side ->
                        val shots = a.shots.filter { it.side == side && it.kind != ShotKind.FT }
                        val color = if (side == Side.A) colorA else colorB
                        val made = shots.count { it.made && it.x != null }
                        val att = shots.count { it.x != null }
                        val x0 = M + i * 2 * half
                        c.drawText("${setup.team(side).name}　FG ${ma(made, att)}（${pct(rate(made, att))}）", x0, M + 52f, text(9f, bold = true, color = color))
                        Charts.shots(c, RectF(x0, M + 60f, x0 + half - 6f, M + 60f + (half - 6f) * 14f / 15f), shots, { s -> setup.team(s.side).player(s.playerId)?.number ?: "?" }, { color })
                        Charts.zones(c, RectF(x0 + half, M + 60f, x0 + 2 * half - 6f, M + 60f + (half - 6f) * 14f / 15f), Zones.compute(shots), heat = false, labelSize = 6f)
                        val expected = Expected.byCategory(a.shots.filter { it.side == side })
                        table(
                            c, x0, M + 60f + half + 10f, listOf(90f, 60f, 50f, 50f), listOf("分類", "成功/試投", "成功率", "期待値"),
                            expected.map { listOf(it.category.label, ma(it.made, it.attempts), pct(it.percent), dec(it.expected)) },
                            size = 7f,
                        )
                    }
                }
            }
            // 5 枚目: アドバンスドスタッツ
            page { c ->
                title(c, g, "アドバンスドスタッツ")
                val ta = a.advanced.getValue(Side.A)
                val tb = a.advanced.getValue(Side.B)
                fun row(label: String, f: (TeamAdvanced) -> String) = listOf(label, f(ta), f(tb))
                val y = table(
                    c, M, M + 40f, listOf(130f, 90f, 90f), listOf("指標", setup.teamA.name, setup.teamB.name),
                    listOf(
                        row("攻撃回数") { "${it.possessions}" },
                        row("PACE") { it.pace?.let { p -> "%.1f".format(p) } ?: "—" },
                        row("平均の攻撃時間") { it.averagePossessionMillis?.let { ms -> "%.1f 秒".format(ms / 1000.0) } ?: "—" },
                        row("PPP") { dec(it.ppp) },
                        row("eFG%") { pct(it.efg) },
                        row("TS%") { pct(it.ts) },
                        row("TO%") { pct(it.toRate) },
                        row("FTR") { dec(it.ftr) },
                        row("ORB%") { pct(it.orbPercent) },
                        row("FTM/PTS") { pct(it.ftmPerPts) },
                        row("AST Rate") { pct(it.astRate) },
                        row("AST/TO") { dec(it.astPerTo) },
                        row("3PA/FGA") { pct(it.threeRate) },
                        row("2nd チャンス") { "${it.secondChancePoints}" },
                        row("TO からの得点") { "${it.pointsOffTurnovers}" },
                    ),
                    size = 7.5f,
                )
                var py = M + 40f
                Side.entries.forEach { side ->
                    val box = a.box.getValue(side)
                    val teamMinutes = box.players.values.mapNotNull { it.minutes }.takeIf { it.isNotEmpty() }?.sum()
                    py = table(
                        c, M + 330f, py, listOf(100f, 34f, 38f, 38f, 38f, 38f, 38f, 38f, 38f),
                        listOf(setup.team(side).name, "MIN", "eFG%", "TS%", "USG%", "AST%", "AST/TO", "GmSc", "PTS/40"),
                        setup.team(side).players.filter { box.player(it.id).played }.map { p ->
                            val l = box.player(p.id)
                            val adv = Advanced.player(setup, l, box.total, teamMinutes)
                            listOf("#${p.number} ${p.name}", minutes(l.minutes), pct(adv.efg), pct(adv.ts), pct(adv.usage), pct(adv.astPercent), dec(adv.astPerTo), "%.1f".format(adv.gameScore), adv.per40?.let { "%.1f".format(it[0]) } ?: "—")
                        },
                        size = 6.5f,
                    ) + 10f
                }
                c.drawText("式は docs/requirements.md の付録 A（アプリの「用語解説」にも記載）", M, maxOf(y, py) + 14f, text(7f, color = 0xFF757575.toInt()))
            }
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }

    fun shareIntent(context: Context, file: File, title: String): Intent = ScoresheetPdf.shareIntent(context, file, title)
}
