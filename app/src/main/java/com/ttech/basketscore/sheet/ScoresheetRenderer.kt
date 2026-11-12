package com.ttech.basketscore.sheet

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side

/** 押せる欄（スコアシートの欄から記録の修正へ移る。F-316） */
data class SheetHit(val rect: RectF, val eventIndex: Int)

/**
 * スコアシートを、公式の様式（A4 縦。reference/kyoto_highschool_score_sheets_2020.pdf）と同じ並びで描く。
 * 座標は PDF のポイント（幅 595・高さ 842）。画面では拡大して描く
 */
object ScoresheetRenderer {

    const val WIDTH = 595f
    const val HEIGHT = 842f

    private const val L = 24f
    private const val R = 571f
    private const val MID = 318f
    private const val RL = 322f

    private val red = Color.rgb(0xC6, 0x28, 0x28)
    private val dark = Color.rgb(0x15, 0x1B, 0x4D)
    private val printed = Color.rgb(0x55, 0x55, 0x55)
    private val shade = Color.rgb(0xDD, 0xDD, 0xDD)

    private fun color(ink: Ink) = if (ink == Ink.RED) red else dark

    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f; color = Color.BLACK }
    private val frame = Paint(thin).apply { strokeWidth = 1.2f }
    private fun stroke(c: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = w; color = c }
    private fun fill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = c }
    private fun text(size: Float, c: Int = Color.BLACK, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = c
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun Canvas.label(s: String, x: Float, y: Float, size: Float, c: Int = Color.BLACK, bold: Boolean = false) =
        drawText(s, x, y, text(size, c, bold))

    private fun Canvas.center(s: String, rect: RectF, size: Float, c: Int = Color.BLACK, bold: Boolean = false) {
        val p = text(size, c, bold).apply { textAlign = Paint.Align.CENTER }
        drawText(s, rect.centerX(), rect.centerY() + size * 0.36f, p)
    }

    /** 1 ページを描き、押せる欄を返す。[page] は 0 から（2 枚目はランニングスコアの 161〜320 点） */
    fun draw(canvas: Canvas, sheet: Scoresheet, page: Int = 0): List<SheetHit> {
        val hits = mutableListOf<SheetHit>()
        sheetFinished = sheet.finished
        canvas.drawRect(0f, 0f, WIDTH, HEIGHT, fill(Color.WHITE))
        canvas.center("OFFICIAL SCORESHEET" + if (page > 0) "（${page + 1} 枚目）" else "", RectF(0f, 18f, WIDTH, 40f), 13f, bold = true)
        header(canvas, sheet)
        hits += team(canvas, sheet.a, "A", 100f)
        hits += team(canvas, sheet.b, "B", 434f)
        officials(canvas, sheet)
        hits += running(canvas, sheet, page)
        summary(canvas, sheet)
        return hits
    }

    private fun header(c: Canvas, s: Scoresheet) {
        val h = s.header
        c.drawRect(L, 44f, R, 96f, frame)
        c.drawLine(L, 60f, R, 60f, thin)
        c.drawLine(L, 78f, R, 78f, thin)
        c.drawLine(296f, 44f, 296f, 60f, thin)
        c.label("チームA: ${s.a.name}", L + 3, 55f, 8f)
        c.label("チームB: ${s.b.name}", 299f, 55f, 8f)
        listOf(230f, 330f, 400f).forEach { c.drawLine(it, 60f, it, 78f, thin) }
        c.label("大会名 ${h.competition}", L + 3, 72f, 7f)
        c.label("日付 ${h.date}", 233f, 72f, 7f)
        c.label("時間 ${h.startTime}", 333f, 72f, 7f)
        c.label("クルーチーフ ${h.crewChief}", 403f, 72f, 7f)
        listOf(230f, 400f, 485f).forEach { c.drawLine(it, 78f, it, 96f, thin) }
        c.label("Game No. ${h.gameNo}", L + 3, 90f, 7f)
        c.label("場所 ${h.venue}", 233f, 90f, 7f)
        c.label("1st アンパイア ${h.umpire1}", 403f, 90f, 6.5f)
        c.label("2nd アンパイア ${h.umpire2}", 488f, 90f, 6.5f)
    }

    // チーム欄の列（選手の表）
    private const val C_NO = L
    private const val C_LIC = 38f
    private const val C_NAME = 62f
    private const val C_NUM = 170f
    private const val C_IN = 192f
    private const val C_FOUL = 214f
    private const val FOUL_W = (MID - C_FOUL) / 5f
    private const val ROW = 12.5f

    // コーチの行の列（肩書き・ライセンス番号・氏名）
    private const val C_COACH_LIC = 86f
    private const val C_COACH_NAME = 120f

    private fun team(c: Canvas, t: SheetTeam, label: String, top: Float): List<SheetHit> {
        val hits = mutableListOf<SheetHit>()
        val head = top + 64f
        val headR = head + 12f
        val coachY = headR + ROW * Scoresheet.PLAYER_ROWS
        val bottom = coachY + ROW * 2
        c.drawRect(L, top, MID, bottom, frame)
        c.label("チーム$label: ${t.name}", L + 3, top + 10f, 8f, bold = true)
        c.drawLine(L, top + 14f, MID, top + 14f, thin)

        // タイムアウト（前半 2・後半 3・OT 3）
        val box = 11f
        c.label("タイムアウト", L + 3, top + 22f, 5.5f, printed)
        listOf(t.firstHalfTimeouts, t.secondHalfTimeouts, t.overtimeTimeouts).forEachIndexed { row, boxes ->
            boxes.take(3).forEachIndexed { i, b ->
                val r = RectF(L + 4 + i * box, top + 25f + row * box, L + 4 + (i + 1) * box, top + 25f + (row + 1) * box)
                c.drawRect(r, thin)
                when (b) {
                    is TimeoutBox.Used -> {
                        c.center(b.minute, r, 7f, color(b.ink), bold = true)
                        hits += SheetHit(r, b.eventIndex)
                    }
                    TimeoutBox.Lost -> doubleLine(c, r, dark)
                    TimeoutBox.Empty -> if (sheetFinished) doubleLine(c, r, dark)
                }
            }
        }

        // チームファウル（クォーター 1〜4、各 4 枠）
        c.label("チームファウル Team fouls", 120f, top + 22f, 6f, printed)
        t.teamFouls.forEachIndexed { q, count ->
            val x0 = 90f + (q % 2) * 112f
            val y0 = top + 26f + (q / 2) * 13f
            c.label("クォーター ${"①②③④"[q]}", x0, y0 + 8.5f, 6.5f)
            (0 until 4).forEach { i ->
                val r = RectF(x0 + 46f + i * 13f, y0, x0 + 46f + (i + 1) * 13f, y0 + 11f)
                c.drawRect(r, thin)
                c.center("${i + 1}", r, 6.5f, printed)
                if (i < count) cross(c, r, color(inkFor(q + 1)))
            }
        }
        c.label("オーバータイム Over times（第4Q に続けて数える）", 90f, top + 60f, 6f, printed)

        // 選手の表
        c.drawLine(L, head, MID, head, thin)
        c.drawLine(L, headR, MID, headR, thin)
        c.label("No.", C_NO + 1, head + 8.5f, 6f)
        c.label("License", C_LIC + 1, head + 8.5f, 5f)
        c.label("選手氏名 Name of Players", C_NAME + 2, head + 8.5f, 6f)
        c.label("No.", C_NUM + 3, head + 8.5f, 6f)
        c.label("Player in", C_IN + 1, head + 8.5f, 4.5f)
        (1..5).forEach { c.center("$it", RectF(C_FOUL + (it - 1) * FOUL_W, head, C_FOUL + it * FOUL_W, headR), 6f) }
        listOf(C_LIC, C_NAME, C_NUM, C_IN).forEach { c.drawLine(it, head, it, coachY, thin) }
        c.drawLine(C_FOUL, head, C_FOUL, bottom, thin)
        // ファウルの枠: 選手は 5 つ、コーチは 3 つ
        (1 until 5).forEach { c.drawLine(C_FOUL + it * FOUL_W, head, C_FOUL + it * FOUL_W, if (it <= 3) bottom else coachY, thin) }
        listOf(C_COACH_LIC, C_COACH_NAME).forEach { c.drawLine(it, coachY, it, bottom, thin) }

        (0 until Scoresheet.PLAYER_ROWS).forEach { i ->
            val y = headR + i * ROW
            c.drawLine(L, y + ROW, MID, y + ROW, thin)
            c.center("${i + 1}", RectF(C_NO, y, C_LIC, y + ROW), 6.5f)
            val p = t.players.getOrNull(i) ?: return@forEach
            c.center(p.licenseNo, RectF(C_LIC, y, C_NAME, y + ROW), 6.5f)
            c.label(p.name + if (p.captain) "（CAP）" else "", C_NAME + 2, y + 9.5f, 7f)
            c.center(p.number, RectF(C_NUM, y, C_IN, y + ROW), 8f, bold = true)
            p.playerIn?.let { pin ->
                val r = RectF(C_IN, y, C_FOUL, y + ROW)
                c.center("×", r, 9f, color(pin.ink), bold = true)
                if (pin.starter) c.drawCircle(r.centerX(), r.centerY(), 5.2f, stroke(red, 0.9f))
            }
            p.fouls.take(5).forEachIndexed { k, f ->
                val r = RectF(C_FOUL + k * FOUL_W, y, C_FOUL + (k + 1) * FOUL_W, y + ROW)
                c.center(f.text, r, 7f, color(f.ink), bold = true)
                hits += SheetHit(r, f.eventIndex)
            }
            p.firstHalfFouls?.let { n ->
                val x = C_FOUL + n.coerceAtMost(5) * FOUL_W
                c.drawLine(x, y + 1, x, y + ROW - 1, stroke(dark, 1.6f))
            }
            // 失格・退場（GD）は、次のファウルの欄に書く
            val gd = p.disqualified && p.fouls.size < 5
            if (gd) c.center("GD", RectF(C_FOUL + p.fouls.size * FOUL_W, y, C_FOUL + (p.fouls.size + 1) * FOUL_W, y + ROW), 7f, red, bold = true)
            val used = p.fouls.size + if (gd) 1 else 0
            if (sheetFinished && used < 5) {
                val x0 = C_FOUL + used * FOUL_W
                c.drawLine(x0 + 1, y + ROW / 2, MID - 1, y + ROW / 2, stroke(dark, 0.8f))
            }
        }
        // コーチ・A.コーチ
        listOf("コーチ Coach" to t.coach, "A.コーチ A.Coach" to t.assistantCoach).forEachIndexed { k, (title, coach) ->
            val y = coachY + k * ROW
            c.drawLine(L, y + ROW, MID, y + ROW, thin)
            c.label(title, L + 2, y + 9f, 6f)
            coach ?: return@forEachIndexed
            c.center(coach.licenseNo, RectF(C_COACH_LIC, y, C_COACH_NAME, y + ROW), 6f)
            c.label(coach.name, C_COACH_NAME + 2, y + 9.5f, 7f)
            coach.fouls.take(3).forEachIndexed { j, f ->
                val r = RectF(C_FOUL + j * FOUL_W, y, C_FOUL + (j + 1) * FOUL_W, y + ROW)
                c.center(f.text, r, 7f, color(f.ink), bold = true)
                hits += SheetHit(r, f.eventIndex)
            }
            if (coach.disqualified && coach.fouls.size < 3) {
                c.center("GD", RectF(C_FOUL + coach.fouls.size * FOUL_W, y, C_FOUL + (coach.fouls.size + 1) * FOUL_W, y + ROW), 7f, red, bold = true)
            }
        }
        return hits
    }

    /** 試合が終わったか（描くあいだだけ持つ） */
    private var sheetFinished = false

    private fun officials(c: Canvas, s: Scoresheet) {
        val h = s.header
        val top = 770f
        c.drawRect(L, top, MID, 818f, frame)
        c.label("スコアラー Scorer　${h.scorer}", L + 3, top + 10f, 6.5f)
        c.label("A.スコアラー A.Scorer　${h.assistantScorer}", L + 3, top + 21f, 6.5f)
        c.label("タイマー Timer　${h.timer}", L + 3, top + 32f, 6.5f)
        c.label("ショットクロックオペレーター S.C.Operator　${h.shotClockOperator}", L + 3, top + 43f, 6f)
        c.label("クルーチーフ　${h.crewChief}", 172f, top + 10f, 6.5f)
        c.label("1st アンパイア　${h.umpire1}", 172f, top + 21f, 6.5f)
        c.label("2nd アンパイア　${h.umpire2}", 172f, top + 32f, 6.5f)
        if (h.umpire3.isNotBlank()) c.label("副審 3　${h.umpire3}", 172f, top + 43f, 6.5f)
    }

    // ランニングスコア
    private const val RUN_TOP = 100f
    private const val RUN_GRID = 124f
    private const val RUN_ROW = 15f
    private const val GROUP_W = (R - RL) / 4f
    private const val COL_W = GROUP_W / 4f

    private fun running(c: Canvas, s: Scoresheet, page: Int): List<SheetHit> {
        val hits = mutableListOf<SheetHit>()
        val bottom = RUN_GRID + RUN_ROW * 40
        c.drawRect(RL, RUN_TOP, R, bottom, frame)
        c.center("ランニングスコア RUNNING SCORE", RectF(RL, RUN_TOP, R, RUN_TOP + 12f), 7f, bold = true)
        c.drawLine(RL, RUN_TOP + 12f, R, RUN_TOP + 12f, thin)
        c.drawLine(RL, RUN_GRID, R, RUN_GRID, thin)
        val offset = page * Scoresheet.POINTS_PER_PAGE
        val lastA = s.runningA.keys.maxOrNull() ?: 0
        val lastB = s.runningB.keys.maxOrNull() ?: 0
        (0 until 4).forEach { g ->
            val gx = RL + g * GROUP_W
            c.center("A", RectF(gx, RUN_TOP + 12f, gx + 2 * COL_W, RUN_GRID), 7f)
            c.center("B", RectF(gx + 2 * COL_W, RUN_TOP + 12f, gx + GROUP_W, RUN_GRID), 7f)
            c.drawRect(gx + 2 * COL_W, RUN_GRID, gx + 3 * COL_W, bottom, fill(shade))
            // 見出しの A・B は 2 列ずつにまたがるので、その間の線は見出しの下から引く
            (0..4).forEach { k -> c.drawLine(gx + k * COL_W, if (k % 2 == 0) RUN_TOP + 12f else RUN_GRID, gx + k * COL_W, bottom, if (k == 0) frame else thin) }
            (0 until 40).forEach { r ->
                val total = offset + g * 40 + r + 1
                val y = RUN_GRID + r * RUN_ROW
                c.drawLine(gx, y + RUN_ROW, gx + GROUP_W, y + RUN_ROW, thin)
                listOf(Side.A, Side.B).forEach { side ->
                    val numCell = if (side == Side.A) RectF(gx, y, gx + COL_W, y + RUN_ROW) else RectF(gx + 3 * COL_W, y, gx + GROUP_W, y + RUN_ROW)
                    val ptsCell = if (side == Side.A) RectF(gx + COL_W, y, gx + 2 * COL_W, y + RUN_ROW) else RectF(gx + 2 * COL_W, y, gx + 3 * COL_W, y + RUN_ROW)
                    c.center("$total", ptsCell, 6f, printed)
                    val cell = s.running(side)[total]
                    if (cell != null) {
                        mark(c, cell, numCell, ptsCell)
                        hits += SheetHit(RectF(minOf(numCell.left, ptsCell.left), y, maxOf(numCell.right, ptsCell.right), y + RUN_ROW), cell.eventIndex)
                    } else if (s.finished && total > (if (side == Side.A) lastA else lastB)) {
                        // 試合の終わり: 残りの欄に斜線
                        c.drawLine(ptsCell.left + 2, ptsCell.bottom - 2, ptsCell.right - 2, ptsCell.top + 2, stroke(dark, 0.6f))
                    }
                }
            }
        }
        return hits
    }

    private fun mark(c: Canvas, cell: RunningCell, numCell: RectF, ptsCell: RectF) {
        val ink = color(cell.ink)
        when (cell.kind) {
            ShotKind.FT -> c.drawCircle(ptsCell.centerX(), ptsCell.centerY(), 3.2f, fill(ink))
            else -> c.drawLine(ptsCell.left + 1.5f, ptsCell.bottom - 1.5f, ptsCell.right - 1.5f, ptsCell.top + 1.5f, stroke(ink, 1.2f))
        }
        c.center(cell.number, numCell, 7.5f, ink, bold = true)
        if (cell.kind == ShotKind.THREE) c.drawCircle(numCell.centerX(), numCell.centerY(), 6.4f, stroke(ink, 0.9f))
        if (cell.periodEnd || cell.gameEnd) {
            c.drawCircle(ptsCell.centerX(), ptsCell.centerY(), 7f, stroke(ink, 1.6f))
            val left = minOf(numCell.left, ptsCell.left)
            val right = maxOf(numCell.right, ptsCell.right)
            c.drawLine(left, ptsCell.bottom, right, ptsCell.bottom, stroke(ink, 1.6f))
            if (cell.gameEnd) c.drawLine(left, ptsCell.bottom + 2.2f, right, ptsCell.bottom + 2.2f, stroke(ink, 1.6f))
        }
    }

    private fun summary(c: Canvas, s: Scoresheet) {
        val top = RUN_GRID + RUN_ROW * 40 + 4f
        c.drawRect(RL, top, R, 818f, frame)
        c.label("スコア Score", RL + 3, top + 10f, 6.5f)
        val labels = listOf("第1クォーター", "第2クォーター", "第3クォーター", "第4クォーター", "オーバータイム")
        val scores = s.quarterScores + listOf(s.overtime)
        labels.forEachIndexed { i, l ->
            val y = top + 20f + i * 11f
            c.label(l, RL + 6, y, 6.5f)
            scores[i]?.let { (a, b) ->
                c.label("A  $a", RL + 62, y, 7f, color(if (i == 0 || i == 2) Ink.RED else Ink.DARK), bold = true)
                c.label("B  $b", RL + 92, y, 7f, color(if (i == 0 || i == 2) Ink.RED else Ink.DARK), bold = true)
            } ?: run { if (i == 4 && s.finished) c.label("／", RL + 75, y, 7f, dark) }
        }
        val x = RL + 128
        c.drawLine(x - 4, top, x - 4, 818f, thin)
        c.label("最終スコア Final Score", x, top + 10f, 6.5f)
        s.finalScore?.let { (a, b) -> c.label("A  $a  －  $b  B", x + 4, top + 24f, 10f, dark, bold = true) }
        c.label("勝者チーム Name of Winning Team", x, top + 38f, 6f)
        s.winner?.let { c.label(it, x + 4, top + 50f, 8f, dark, bold = true) }
        c.label("試合終了時間 Game Ended at (hh:mm)　${s.endedAt ?: ""}", x, top + 66f, 6f)
    }

    private fun cross(c: Canvas, r: RectF, ink: Int) {
        val p = stroke(ink, 1.1f)
        c.drawLine(r.left + 1.5f, r.top + 1.5f, r.right - 1.5f, r.bottom - 1.5f, p)
        c.drawLine(r.left + 1.5f, r.bottom - 1.5f, r.right - 1.5f, r.top + 1.5f, p)
    }

    private fun doubleLine(c: Canvas, r: RectF, ink: Int) {
        val p = stroke(ink, 0.8f)
        c.drawLine(r.left + 1, r.centerY() - 1.5f, r.right - 1, r.centerY() - 1.5f, p)
        c.drawLine(r.left + 1, r.centerY() + 1.5f, r.right - 1, r.centerY() + 1.5f, p)
    }
}
