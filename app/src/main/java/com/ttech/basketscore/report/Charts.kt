package com.ttech.basketscore.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.withClip
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.rules.Period
import com.ttech.basketscore.stats.Court
import com.ttech.basketscore.stats.Flow
import com.ttech.basketscore.stats.FlowMarker
import com.ttech.basketscore.stats.Possession
import com.ttech.basketscore.stats.Run
import com.ttech.basketscore.stats.ScoreEvent
import com.ttech.basketscore.stats.ZoneLine
import com.ttech.basketscore.stats.Zones
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 分析のグラフ（ゲームフロー・シュートチャート・22 区域）。画面（Compose の drawIntoCanvas）と PDF の両方で同じ描き方を使う。
 * 座標は [RectF] の中に収める
 */
object Charts {

    private const val LINE = 0xFF5A4630.toInt()
    private const val FLOOR = 0xFFF3E2C4.toInt()
    private const val GRID = 0xFFBDBDBD.toInt()
    private const val TEXT = 0xFF212121.toInt()

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
        strokeWidth = width
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun text(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        textAlign = align
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    /** 透明度を付けた色 */
    private fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

    // --- ゲームフロー（F-602） ---

    /**
     * 点差（A − B）の推移。ボールを持っていたチームの帯・タイムアウトの印・ランの区間を重ねる。
     * 時刻のある試合は経過時間、無い試合は得点の順を横軸にする
     */
    fun flow(
        c: Canvas,
        r: RectF,
        setup: GameSetup,
        periods: Int,
        scores: List<ScoreEvent>,
        possessions: List<Possession>,
        markers: List<FlowMarker>,
        runs: List<Run>,
        colorA: Int,
        colorB: Int,
        labelSize: Float = 10f,
        nameA: String = "A",
        nameB: String = "B",
        /** 線を伸ばす終わり（今のピリオドと時計の残り） */
        endPeriod: Int = periods,
        endClock: Long? = null,
    ) {
        val plot = RectF(r.left + labelSize * 2.6f, r.top + labelSize * 1.8f, r.right - 4f, r.bottom - labelSize * 1.6f)
        val total = (1..max(periods, Period.QUARTERS)).sumOf { setup.rules.periodMillis(it) }.toFloat()
        val timed = setup.useClock && scores.all { it.clock != null }
        fun xOf(period: Int, clock: Long?, order: Int): Float =
            if (timed) {
                plot.left + plot.width() * ((Flow.elapsed(setup, period, clock) ?: 0L) / total)
            } else {
                plot.left + plot.width() * (order + 1) / (scores.size + 1f)
            }
        val maxMargin = max(5, scores.maxOfOrNull { abs(it.margin) } ?: 0).let { (it + 4) / 5 * 5 }
        fun yOf(margin: Int): Float = plot.centerY() - plot.height() / 2 * margin / maxMargin

        // 目盛りとピリオドの区切り
        c.drawRect(plot, stroke(GRID, 1f))
        listOf(maxMargin, maxMargin / 2, 0, -maxMargin / 2, -maxMargin).distinct().forEach { m ->
            val y = yOf(m)
            c.drawLine(plot.left, y, plot.right, y, stroke(if (m == 0) LINE else GRID, if (m == 0) 1.5f else 0.6f))
            c.drawText(if (m > 0) "+$m" else "$m", plot.left - 4f, y + labelSize / 3, text(TEXT, labelSize * 0.9f, align = Paint.Align.RIGHT))
        }
        if (timed) {
            var acc = 0L
            for (p in 1..max(periods, Period.QUARTERS)) {
                val x0 = plot.left + plot.width() * acc / total
                acc += setup.rules.periodMillis(p)
                val x1 = plot.left + plot.width() * acc / total
                if (p > 1) c.drawLine(x0, plot.top, x0, plot.bottom, stroke(GRID, 1f))
                c.drawText(Period.label(p), (x0 + x1) / 2, plot.bottom + labelSize * 1.2f, text(TEXT, labelSize, align = Paint.Align.CENTER))
            }
            // ボールを持っていたチームの帯（上の細い帯）
            possessions.forEach { p ->
                val start = p.startClock ?: return@forEach
                val end = p.endClock ?: return@forEach
                val x0 = xOf(p.period, start, 0)
                val x1 = xOf(p.period, end, 0)
                c.drawRect(x0, plot.top - labelSize * 0.9f, x1, plot.top - labelSize * 0.2f, fill(if (p.side == Side.A) colorA else colorB))
            }
            // ランの区間
            runs.forEach { run ->
                val first = scores.firstOrNull { it.index == run.startIndex } ?: return@forEach
                val last = scores.firstOrNull { it.index == run.endIndex } ?: return@forEach
                val x0 = xOf(first.period, first.clock, 0)
                val x1 = xOf(last.period, last.clock, 0)
                c.drawRect(x0 - 2f, plot.top, x1 + 2f, plot.bottom, fill(alpha(if (run.side == Side.A) colorA else colorB, 0x22)))
                c.drawText("${run.points}-0", (x0 + x1) / 2, plot.top + labelSize * 1.1f, text(if (run.side == Side.A) colorA else colorB, labelSize * 0.9f, bold = true, align = Paint.Align.CENTER))
            }
            // タイムアウト（下向きの三角）
            markers.filter { it.kind == FlowMarker.Kind.TIMEOUT && it.clock != null }.forEach { m ->
                val x = xOf(m.period, m.clock, 0)
                val path = Path().apply {
                    moveTo(x - labelSize * 0.4f, plot.bottom - labelSize * 0.8f)
                    lineTo(x + labelSize * 0.4f, plot.bottom - labelSize * 0.8f)
                    lineTo(x, plot.bottom - labelSize * 0.1f)
                    close()
                }
                c.drawPath(path, fill(if (m.side == Side.B) colorB else colorA))
            }
        }
        // 点差の線（得点のたびに段を付ける）
        val path = Path().apply { moveTo(plot.left, yOf(0)) }
        var lastY = yOf(0)
        scores.forEachIndexed { i, s ->
            val x = xOf(s.period, s.clock, i)
            path.lineTo(x, lastY)
            lastY = yOf(s.margin)
            path.lineTo(x, lastY)
        }
        val endX = if (timed) xOf(endPeriod.coerceAtLeast(1), endClock ?: 0, 0) else plot.right
        path.lineTo(maxOf(endX, scores.lastOrNull()?.let { xOf(it.period, it.clock, scores.lastIndex) } ?: plot.left), lastY)
        c.drawPath(path, stroke(TEXT, 2f))
        // 凡例
        c.drawText("▲ $nameA がリード", plot.left + 4f, plot.top + labelSize * 2.2f, text(colorA, labelSize, bold = true))
        c.drawText("▼ $nameB がリード", plot.left + 4f, plot.bottom - labelSize * 1.2f, text(colorB, labelSize, bold = true))
    }

    // --- ハーフコート（シュートチャート・区域） ---

    /** ハーフコートの図（ゴールが上）。そろえた座標（x: 0〜1、y: 0〜1）を描く位置に変える関数を返す */
    private fun halfCourt(c: Canvas, r: RectF): (Double, Double) -> Pair<Float, Float> {
        // 幅 15 m × 奥行き 14 m を、縦横比を保って収める
        val scale = minOf(r.width() / 15f, r.height() / 14f)
        val w = 15f * scale
        val h = 14f * scale
        val left = r.left + (r.width() - w) / 2
        val top = r.top + (r.height() - h) / 2
        fun px(fromBaseline: Float, lateral: Float) = (left + lateral * scale) to (top + fromBaseline * scale)
        c.drawRect(left, top, left + w, top + h, fill(FLOOR))
        val line = stroke(LINE, 1.2f)
        c.drawRect(left, top, left + w, top + h, line)
        val basket = Court.BASKET_FROM_BASELINE.toFloat()
        val r3 = Court.THREE_RADIUS.toFloat()
        val corner = Court.THREE_CORNER.toFloat()
        val depth = kotlin.math.sqrt(r3 * r3 - corner * corner)
        // 3P ライン
        val (cx, cy) = px(basket, 7.5f)
        c.drawLine(left + 0.9f * scale, top, left + 0.9f * scale, top + (basket + depth) * scale, line)
        c.drawLine(left + w - 0.9f * scale, top, left + w - 0.9f * scale, top + (basket + depth) * scale, line)
        val sweep = Math.toDegrees(2 * kotlin.math.asin((corner / r3).toDouble())).toFloat()
        c.drawArc(RectF(cx - r3 * scale, cy - r3 * scale, cx + r3 * scale, cy + r3 * scale), 90f - sweep / 2, sweep, false, line)
        // 制限区域とフリースローの円
        c.drawRect(left + (7.5f - 2.45f) * scale, top, left + (7.5f + 2.45f) * scale, top + 5.8f * scale, line)
        c.drawCircle(cx, top + 5.8f * scale, 1.8f * scale, line)
        c.drawCircle(cx, cy, 1.25f * scale, stroke(LINE, 0.8f))
        c.drawCircle(cx, cy, 0.25f * scale, stroke(0xFFE65100.toInt(), 1.5f))
        c.drawLine(cx - 0.9f * scale, top + 1.2f * scale, cx + 0.9f * scale, top + 1.2f * scale, stroke(LINE, 2f))
        // そろえた座標: x はエンドラインから（1 が攻めるゴール側）、y はシューターの左（0）から右（1）
        return { x, y ->
            val fromBaseline = ((1 - x) * Court.LENGTH).toFloat().coerceIn(0f, 14f)
            val lateral = (y * Court.WIDTH).toFloat()
            px(fromBaseline, lateral)
        }
    }

    /** シュートチャート（F-603）: 成功は背番号を○で囲み、失敗は背番号だけ */
    fun shots(c: Canvas, r: RectF, shots: List<Shot>, numberOf: (Shot) -> String, colorOf: (Shot) -> Int) {
        val at = halfCourt(c, r)
        val scale = minOf(r.width() / 15f, r.height() / 14f)
        val size = scale * 0.55f
        shots.filter { it.kind != ShotKind.FT && it.x != null && it.y != null }.forEach { s ->
            val (x, y) = at(s.x!!.toDouble(), s.y!!.toDouble())
            val color = colorOf(s)
            if (s.made) {
                c.drawCircle(x, y, size * 1.05f, fill(Color.WHITE))
                c.drawCircle(x, y, size * 1.05f, stroke(color, 1.5f))
            }
            c.drawText(numberOf(s), x, y + size * 0.35f, text(if (s.made) color else alpha(color, 0xAA), size * 0.95f, bold = s.made, align = Paint.Align.CENTER))
        }
    }

    /** 区域ごとの成功率（F-604）。[heat] なら丸の大きさが本数、色が成功率のヒートマップ */
    fun zones(c: Canvas, r: RectF, lines: List<ZoneLine>, heat: Boolean, labelSize: Float = 9f) {
        val at = halfCourt(c, r)
        val scale = minOf(r.width() / 15f, r.height() / 14f)
        val bands = listOf(0.0) + Zones.bandLimits + listOf(14.5)
        val maxAttempts = lines.maxOfOrNull { it.attempts }?.coerceAtLeast(1) ?: 1
        lines.forEach { line ->
            val z = line.zone
            val r0 = bands[z.band]
            val r1 = bands[z.band + 1]
            // 角度: -90°（シューターの左・エンドライン沿い）から 90°（右）まで
            val a0 = -90.0 + 180.0 / z.sectors * z.sector
            val a1 = a0 + 180.0 / z.sectors
            fun point(dist: Double, deg: Double): Pair<Float, Float> {
                val rad = Math.toRadians(deg)
                val fromBaseline = Court.BASKET_FROM_BASELINE + dist * cos(rad)
                val lateral = 7.5 + dist * sin(rad)
                return at(1 - fromBaseline / Court.LENGTH, lateral / Court.WIDTH)
            }
            val color = percentColor(line.percent)
            val mid = point((r0 + r1) / 2, (a0 + a1) / 2)
            if (!heat) {
                val path = Path()
                val steps = 12
                (0..steps).forEach { i ->
                    val (x, y) = point(r1, a0 + (a1 - a0) * i / steps)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                (steps downTo 0).forEach { i ->
                    val (x, y) = point(r0, a0 + (a1 - a0) * i / steps)
                    path.lineTo(x, y)
                }
                path.close()
                c.withClip(at(1.0, 0.0).first, at(1.0, 0.0).second, at(0.5, 1.0).first, at(0.5, 1.0).second) {
                    drawPath(path, fill(if (line.attempts > 0) alpha(color, 0x99) else 0x00000000))
                    drawPath(path, stroke(0x66FFFFFF, 1f))
                }
                if (line.attempts > 0) {
                    c.drawText("${line.made}/${line.attempts}", mid.first, mid.second, text(TEXT, labelSize, bold = true, align = Paint.Align.CENTER))
                    c.drawText("${(line.percent!! * 100).toInt()}%", mid.first, mid.second + labelSize, text(TEXT, labelSize * 0.85f, align = Paint.Align.CENTER))
                }
            } else if (line.attempts > 0) {
                val radius = scale * (0.35f + 0.9f * line.attempts / maxAttempts)
                c.drawCircle(mid.first, mid.second, radius, fill(alpha(color, 0xCC)))
                c.drawText("${line.attempts}", mid.first, mid.second + labelSize / 3, text(TEXT, labelSize, bold = true, align = Paint.Align.CENTER))
            }
        }
    }

    /** 成功率の色（低い: 青 → 50%: 黄 → 高い: 赤） */
    fun percentColor(p: Double?): Int {
        p ?: return 0xFFBDBDBD.toInt()
        val t = p.coerceIn(0.0, 1.0)
        return when {
            t < 0.35 -> 0xFF5C8DD6.toInt()
            t < 0.45 -> 0xFF9EC5E8.toInt()
            t < 0.55 -> 0xFFF2D46B.toInt()
            t < 0.65 -> 0xFFF0A04B.toInt()
            else -> 0xFFE0563A.toInt()
        }
    }
}
