package com.ttech.basketscore.stats

import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.GameEngine
import com.ttech.basketscore.game.GameEvent
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.Offender
import com.ttech.basketscore.game.PeriodEnd
import com.ttech.basketscore.game.PeriodStart
import com.ttech.basketscore.game.Rebound
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.Substitution
import com.ttech.basketscore.game.Timeout
import com.ttech.basketscore.game.Turnover
import com.ttech.basketscore.rules.Period
import kotlin.math.abs
import kotlin.math.atan2

// --- 得点の流れ（F-602・F-612・F-616） ---

/** 1 回の得点（得点時系列の 1 行） */
data class ScoreEvent(
    val index: Int,
    val period: Int,
    val clock: Long?,
    val side: Side,
    val playerId: String,
    val points: Int,
    val kind: ShotKind,
    val scoreA: Int,
    val scoreB: Int,
) {
    val margin: Int get() = scoreA - scoreB
}

/** 連続得点（ラン。例: 10-0） */
data class Run(val side: Side, val points: Int, val startIndex: Int, val endIndex: Int, val startPeriod: Int, val endPeriod: Int)

/** ゲームフローチャートに重ねる出来事（タイムアウト・交代） */
data class FlowMarker(val index: Int, val period: Int, val clock: Long?, val side: Side?, val kind: Kind) {
    enum class Kind { TIMEOUT, SUBSTITUTION }
}

object Flow {

    /** 得点時系列（F-616）と、ゲームフローの点（F-602） */
    fun scores(events: List<GameEvent>): List<ScoreEvent> {
        var a = 0
        var b = 0
        return events.mapIndexedNotNull { i, e ->
            if (e !is Shot || !e.made) return@mapIndexedNotNull null
            if (e.side == Side.A) a += e.kind.points else b += e.kind.points
            ScoreEvent(i, e.period, e.clockMillis, e.side, e.playerId, e.kind.points, e.kind, a, b)
        }
    }

    /** 相手に点を取られずに [minPoints] 点以上続けて取った区間（F-612。初期値は 8 点） */
    fun runs(scores: List<ScoreEvent>, minPoints: Int = 8): List<Run> {
        val result = mutableListOf<Run>()
        var i = 0
        while (i < scores.size) {
            var j = i
            var points = 0
            while (j < scores.size && scores[j].side == scores[i].side) {
                points += scores[j].points
                j++
            }
            if (points >= minPoints) result += Run(scores[i].side, points, scores[i].index, scores[j - 1].index, scores[i].period, scores[j - 1].period)
            i = j
        }
        return result
    }

    /** タイムアウトと交代の位置 */
    fun markers(events: List<GameEvent>): List<FlowMarker> = events.mapIndexedNotNull { i, e ->
        when (e) {
            is Timeout -> FlowMarker(i, e.period, e.clockMillis, e.side, FlowMarker.Kind.TIMEOUT)
            is Substitution -> FlowMarker(i, e.period, e.clockMillis, e.changes.map { it.side }.distinct().singleOrNull(), FlowMarker.Kind.SUBSTITUTION)
            else -> null
        }
    }

    /** 試合の経過時間（ミリ秒。グラフの横軸）。時刻が無ければ null */
    fun elapsed(setup: GameSetup, period: Int, clock: Long?): Long? {
        clock ?: return null
        val before = (1 until period).sumOf { setup.rules.periodMillis(it) }
        return before + (setup.rules.periodMillis(period) - clock).coerceAtLeast(0)
    }
}

// --- ラインナップ（F-610） ---

data class Lineup(
    val side: Side,
    /** 5 人の ID（並びをそろえたもの） */
    val players: List<String>,
    val millis: Long,
    val pointsFor: Int,
    val pointsAgainst: Int,
) {
    val plusMinus: Int get() = pointsFor - pointsAgainst
}

object Lineups {
    /** 5 人の組み合わせごとの出場時間・得点・失点（時刻の無い記録は時間に入れない） */
    fun compute(setup: GameSetup, events: List<GameEvent>): List<Lineup> {
        data class Acc(var millis: Long = 0, var pf: Int = 0, var pa: Int = 0)
        val acc = mutableMapOf<Pair<Side, List<String>>, Acc>()
        val timeline = GameEngine.timeline(setup, events)
        var last: Long? = null
        events.forEachIndexed { i, e ->
            val snap = timeline[i]
            val clock = e.clockMillis
            if (e is PeriodStart) {
                last = clock ?: setup.rules.periodMillis(e.period)
                return@forEachIndexed
            }
            if (snap.periodRunning) {
                val now = if (e is PeriodEnd) clock ?: 0 else clock
                if (now != null && last != null) {
                    val delta = (last!! - now).coerceAtLeast(0)
                    Side.entries.forEach { side -> acc.getOrPut(side to snap.onCourt(side).sorted()) { Acc() }.millis += delta }
                    last = now
                }
                if (e is Shot && e.made) {
                    acc.getOrPut(e.side to snap.onCourt(e.side).sorted()) { Acc() }.pf += e.kind.points
                    acc.getOrPut(e.side.other to snap.onCourt(e.side.other).sorted()) { Acc() }.pa += e.kind.points
                }
            }
            if (e is PeriodEnd) last = null
        }
        return acc.filterKeys { it.second.isNotEmpty() }
            .map { (k, v) -> Lineup(k.first, k.second, v.millis, v.pf, v.pa) }
            .sortedWith(compareBy<Lineup> { it.side }.thenByDescending { it.millis })
    }
}

// --- 時間帯（F-605） ---

/** 時間帯の分け方 */
enum class SegmentMode(val label: String) {
    HALF_QUARTER("各 Q の前半・後半"),
    TWO_MINUTES("2 分ごと"),
    QUARTER("Q ごと"),
}

/** 1 つの時間帯の、両チームの集計 */
data class Segment(val period: Int, val index: Int, val label: String, val a: StatLine, val b: StatLine) {
    fun line(side: Side): StatLine = if (side == Side.A) a else b
}

object Segments {
    fun compute(setup: GameSetup, events: List<GameEvent>, mode: SegmentMode = SegmentMode.HALF_QUARTER): List<Segment> {
        val acc = sortedMapOf<Pair<Int, Int>, MutableMap<Side, StatLine>>(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        val labels = mutableMapOf<Pair<Int, Int>, String>()
        val maxPeriod = events.maxOfOrNull { it.period } ?: 0
        // 区切りを先に並べる（記録の無い時間帯も 0 で出す）
        for (p in 1..maxOf(maxPeriod, 1)) {
            bounds(setup, p, mode).forEachIndexed { i, (_, label) ->
                acc[p to i] = mutableMapOf(Side.A to StatLine(), Side.B to StatLine())
                labels[p to i] = label
            }
        }
        for (e in events) {
            if (e.period < 1) continue
            val key = e.period to segmentOf(setup, e.period, e.clockMillis, mode)
            val map = acc.getOrPut(key) { mutableMapOf(Side.A to StatLine(), Side.B to StatLine()) }
            fun bump(side: Side, f: (StatLine) -> StatLine) {
                map[side] = f(map[side] ?: StatLine())
            }
            when (e) {
                is Shot -> {
                    val made = if (e.made) 1 else 0
                    bump(e.side) {
                        when (e.kind) {
                            ShotKind.FT -> it.copy(pts = it.pts + made, ftm = it.ftm + made, fta = it.fta + 1)
                            ShotKind.TWO -> it.copy(pts = it.pts + 2 * made, fgm = it.fgm + made, fga = it.fga + 1)
                            ShotKind.THREE -> it.copy(pts = it.pts + 3 * made, fgm = it.fgm + made, fga = it.fga + 1, tpm = it.tpm + made, tpa = it.tpa + 1)
                        }
                    }
                }
                is Rebound -> bump(e.side) { if (e.offensive) it.copy(or = it.or + 1) else it.copy(dr = it.dr + 1) }
                is Turnover -> bump(e.side) { it.copy(tov = it.tov + 1) }
                is Foul -> if (e.offender == Offender.PLAYER) {
                    bump(e.side) { it.copy(pf = it.pf + 1, tov = it.tov + if (e.offensive) 1 else 0) }
                }
                else -> Unit
            }
        }
        return acc.map { (k, v) ->
            Segment(k.first, k.second, labels[k] ?: Period.label(k.first), v[Side.A] ?: StatLine(), v[Side.B] ?: StatLine())
        }
    }

    /** そのピリオドの時間帯（区切りの時計の残りと表示）。時計の残りが大きい方から */
    fun bounds(setup: GameSetup, period: Int, mode: SegmentMode): List<Pair<Long, String>> {
        val full = setup.rules.periodMillis(period)
        val label = Period.label(period)
        return when {
            mode == SegmentMode.QUARTER || Period.isOvertime(period) -> listOf(full to label)
            mode == SegmentMode.HALF_QUARTER -> listOf(full to "$label 前半", full / 2 to "$label 後半")
            else -> (full downTo 1L step 120_000L).map { start ->
                val from = (full - start) / 60_000
                start to "$label ${from}〜${minOf(from + 2, full / 60_000)} 分"
            }
        }
    }

    /** 時計の残りから、何番目の時間帯か（時刻が無ければ 0） */
    fun segmentOf(setup: GameSetup, period: Int, clock: Long?, mode: SegmentMode): Int {
        clock ?: return 0
        val b = bounds(setup, period, mode)
        // 区切りちょうど（例: 残り 5:00）は後ろの時間帯に入れる
        return b.indexOfLast { (start, _) -> clock <= start }.coerceAtLeast(0)
    }
}

// --- 区域と期待値（F-604・F-609） ---

/** シュートの区域（ゴールからの距離の帯 6 つと角度で 22 区域） */
data class Zone(val band: Int, val sector: Int, val sectors: Int) {
    val id: Int get() = Zones.all.indexOf(this)

    /** 表示の名前（例: 「3P 左コーナー」「ゴール下」） */
    val label: String
        get() {
            val bandLabel = listOf("ゴール下", "ペイント", "ショート", "ミドル", "3P", "ロング 3P")[band]
            if (sectors == 1) return bandLabel
            val names = if (sectors == 3) listOf("左", "正面", "右") else listOf("左コーナー", "左 45°", "正面", "右 45°", "右コーナー")
            return "$bandLabel ${names[sector]}"
        }
}

/** 区域ごとの試投・成功・得点 */
data class ZoneLine(val zone: Zone, val made: Int, val attempts: Int, val points: Int) {
    val percent: Double? get() = if (attempts > 0) made.toDouble() / attempts else null
    val expected: Double? get() = if (attempts > 0) points.toDouble() / attempts else null
}

object Zones {
    /** 距離の帯の外側の境目（m）: 0〜2.45・〜3.83・〜5.21・〜6.75・〜7.85・それより遠く */
    val bandLimits = listOf(2.45, 3.83, 5.21, 6.75, 7.85)

    /** 帯ごとの角度の分け方（合計 22） */
    private val sectorsPerBand = listOf(1, 3, 5, 5, 5, 3)

    val all: List<Zone> = sectorsPerBand.flatMapIndexed { band, n -> (0 until n).map { Zone(band, it, n) } }

    /** そろえた座標のシュートの区域。3P のシュートは 3P の帯（5・6 つ目）、2P は 2P の帯に入れる */
    fun of(x: Double, y: Double, kind: ShotKind): Zone {
        val d = Court.distance(x, y)
        var band = bandLimits.indexOfFirst { d <= it }.let { if (it < 0) bandLimits.size else it }
        band = if (kind == ShotKind.THREE) band.coerceAtLeast(4) else band.coerceAtMost(3)
        val n = sectorsPerBand[band]
        // シューターから見た左右: y が小さい（奥のサイドライン）側がシューターの左
        val dx = (1 - x) * Court.LENGTH - Court.BASKET_FROM_BASELINE
        val dy = (y - 0.5) * Court.WIDTH
        val angle = Math.toDegrees(atan2(dy, dx.coerceAtLeast(0.0))).coerceIn(-90.0, 90.0)
        val sector = ((angle + 90) / (180.0 / n)).toInt().coerceIn(0, n - 1)
        return Zone(band, sector, n)
    }

    /** 区域ごとの成功率と期待値（位置のあるシュートだけ） */
    fun compute(shots: List<Shot>): List<ZoneLine> {
        val by = shots.filter { it.kind != ShotKind.FT && it.x != null && it.y != null }
            .groupBy { of(it.x!!.toDouble(), it.y!!.toDouble(), it.kind) }
        return all.map { z ->
            val list = by[z].orEmpty()
            ZoneLine(z, list.count { it.made }, list.size, list.filter { it.made }.sumOf { it.kind.points })
        }
    }

    /** ペイント（制限区域。幅 4.9 m・エンドラインから 5.8 m）の中か */
    fun inPaint(x: Double, y: Double): Boolean = (1 - x) * Court.LENGTH <= 5.8 && abs((y - 0.5) * Court.WIDTH) <= 2.45
}

/** 期待値の分類（F-609） */
enum class ShotCategory(val label: String) { FT("フリースロー"), PAINT("ペイント内の 2P"), MID("ミドルの 2P"), THREE("3P") }

data class CategoryLine(val category: ShotCategory, val made: Int, val attempts: Int, val points: Int) {
    val percent: Double? get() = if (attempts > 0) made.toDouble() / attempts else null
    val expected: Double? get() = if (attempts > 0) points.toDouble() / attempts else null
}

object Expected {
    fun categoryOf(shot: Shot): ShotCategory? = when {
        shot.kind == ShotKind.FT -> ShotCategory.FT
        shot.kind == ShotKind.THREE -> ShotCategory.THREE
        shot.x == null || shot.y == null -> null
        Zones.inPaint(shot.x.toDouble(), shot.y.toDouble()) -> ShotCategory.PAINT
        else -> ShotCategory.MID
    }

    fun byCategory(shots: List<Shot>): List<CategoryLine> {
        val by = shots.groupBy { categoryOf(it) }
        return ShotCategory.entries.map { c ->
            val list = by[c].orEmpty()
            CategoryLine(c, list.count { it.made }, list.size, list.filter { it.made }.sumOf { it.kind.points })
        }
    }
}

// --- 種別（F-611） ---

/** 種別ごとの攻撃（シュートとターンオーバー）と得点 */
data class TypeLine(val type: String, val plays: Int, val points: Int, val fgm: Int, val fga: Int, val turnovers: Int) {
    /** 1 回のプレーあたりの得点 */
    val ppp: Double? get() = if (plays > 0) points.toDouble() / plays else null
}

object PlayTypeStats {
    /** [side] のオフェンスの種別ごと（F-611） */
    fun offense(events: List<GameEvent>, side: Side): List<TypeLine> = group(events, side) { e ->
        when (e) {
            is Shot -> e.offenseType
            is Turnover -> e.offenseType
            else -> null
        }
    }

    /** [side] の相手の攻撃を、[side] のディフェンスの種別ごとに（失点を見る） */
    fun defense(events: List<GameEvent>, side: Side): List<TypeLine> = group(events, side.other) { e ->
        when (e) {
            is Shot -> e.defenseType
            is Turnover -> e.defenseType
            else -> null
        }
    }

    /** シュートの種別ごと（レイアップ・ダンクなど） */
    fun shotTypes(events: List<GameEvent>, side: Side): List<TypeLine> = group(events, side) { e -> (e as? Shot)?.shotType }

    private fun group(events: List<GameEvent>, side: Side, typeOf: (GameEvent) -> String?): List<TypeLine> {
        val acc = linkedMapOf<String, TypeLine>()
        for (e in events) {
            val type = typeOf(e) ?: continue
            val line = acc[type] ?: TypeLine(type, 0, 0, 0, 0, 0)
            acc[type] = when {
                e is Shot && e.side == side && e.kind != ShotKind.FT -> line.copy(
                    plays = line.plays + 1,
                    points = line.points + if (e.made) e.kind.points else 0,
                    fgm = line.fgm + if (e.made) 1 else 0,
                    fga = line.fga + 1,
                )
                e is Turnover && e.side == side -> line.copy(plays = line.plays + 1, turnovers = line.turnovers + 1)
                else -> line
            }
        }
        return acc.values.filter { it.plays > 0 }.sortedByDescending { it.plays }
    }
}

// --- チームの指標（F-606・F-607・F-609） ---

/** チームのアドバンスドスタッツ（付録 A の式） */
data class TeamAdvanced(
    val possessions: Int,
    /** 記録から数えた攻撃回数が無いときの推定（FGA + 0.44 × FTA − OR + TO） */
    val estimatedPossessions: Double,
    val averagePossessionMillis: Long?,
    val pace: Double?,
    val ppp: Double?,
    val efg: Double?,
    val ts: Double?,
    val toRate: Double?,
    val ftr: Double?,
    val orbPercent: Double?,
    val ftmPerPts: Double?,
    val astRate: Double?,
    val astPerTo: Double?,
    val threeRate: Double?,
    val secondChancePoints: Int,
    val pointsOffTurnovers: Int,
)

object Advanced {

    private fun ratio(a: Number, b: Number): Double? = if (b.toDouble() != 0.0) a.toDouble() / b.toDouble() else null

    /** 試合の分（規定の 4 Q と、行った OT） */
    fun gameMinutes(setup: GameSetup, periods: Int): Double =
        (1..maxOf(periods, Period.QUARTERS)).sumOf { setup.rules.periodMillis(it) } / 60_000.0

    fun team(
        setup: GameSetup,
        side: Side,
        own: StatLine,
        opponent: StatLine,
        possessions: List<Possession>,
        periods: Int,
    ): TeamAdvanced {
        val mine = possessions.filter { it.side == side }
        val count = mine.size
        val estimated = own.fga + 0.44 * own.fta - own.or + own.tov
        val poss = if (count > 0) count.toDouble() else estimated
        val durations = mine.mapNotNull { it.duration() }
        // 規定の試合の長さ（40 分など）あたりの攻撃回数
        val regulation = (1..Period.QUARTERS).sumOf { setup.rules.periodMillis(it) } / 60_000.0
        return TeamAdvanced(
            possessions = count,
            estimatedPossessions = estimated,
            averagePossessionMillis = if (durations.isNotEmpty()) durations.sum() / durations.size else null,
            pace = ratio(poss, gameMinutes(setup, periods))?.times(regulation),
            ppp = ratio(own.pts, poss),
            efg = ratio(own.fgm + 0.5 * own.tpm, own.fga),
            ts = ratio(own.pts, 2 * (own.fga + 0.44 * own.fta)),
            toRate = ratio(own.tov, own.fga + 0.44 * own.fta + own.tov),
            ftr = ratio(own.fta, own.fga),
            orbPercent = ratio(own.or, own.or + opponent.dr),
            ftmPerPts = ratio(own.ftm, own.pts),
            astRate = ratio(own.ast, own.fgm),
            astPerTo = ratio(own.ast, own.tov),
            threeRate = ratio(own.tpa, own.fga),
            secondChancePoints = mine.sumOf { it.secondChancePoints },
            pointsOffTurnovers = mine.filter { it.reason == StartReason.TURNOVER }.sumOf { it.points },
        )
    }

    /** 選手の指標（F-609） */
    data class PlayerAdvanced(
        val efg: Double?,
        val ts: Double?,
        val usage: Double?,
        val astPercent: Double?,
        val astPerTo: Double?,
        val gameScore: Double,
        /** 規定の試合の長さ（40 分など）あたりの得点・FGA・REB・TO */
        val per40: List<Double>?,
    )

    fun player(setup: GameSetup, line: StatLine, team: StatLine, teamMinutes: Long?): PlayerAdvanced {
        val min = line.minutes?.let { it / 60_000.0 }
        val teamMin = teamMinutes?.let { it / 60_000.0 }
        val usage = if (min != null && teamMin != null && min > 0) {
            ratio((line.fga + 0.44 * line.fta + line.tov) * (teamMin / 5), min * (team.fga + 0.44 * team.fta + team.tov))
        } else {
            null
        }
        val astPercent = if (min != null && teamMin != null && min > 0) {
            ratio(line.ast, (min / (teamMin / 5)) * team.fgm - line.fgm)
        } else {
            null
        }
        val regulation = (1..Period.QUARTERS).sumOf { setup.rules.periodMillis(it) } / 60_000.0
        val per40 = if (min != null && min > 0) {
            listOf(line.pts, line.fga, line.reb, line.tov).map { it / min * regulation }
        } else {
            null
        }
        val gameScore = line.pts + 0.4 * line.fgm - 0.7 * line.fga - 0.4 * (line.fta - line.ftm) +
            0.7 * line.or + 0.3 * line.dr + line.stl + 0.7 * line.ast + 0.7 * line.blk - 0.4 * line.pf - line.tov
        return PlayerAdvanced(
            efg = ratio(line.fgm + 0.5 * line.tpm, line.fga),
            ts = ratio(line.pts, 2 * (line.fga + 0.44 * line.fta)),
            usage = usage,
            astPercent = astPercent,
            astPerTo = ratio(line.ast, line.tov),
            gameScore = gameScore,
            per40 = per40,
        )
    }
}

/** 記録の分析をまとめて作る（画面と PDF・CSV で同じものを使う） */
data class GameAnalysis(
    val box: Map<Side, TeamBox>,
    val quarterBox: Map<Int, Map<Side, TeamBox>>,
    val possessions: List<Possession>,
    val advanced: Map<Side, TeamAdvanced>,
    val scores: List<ScoreEvent>,
    val runs: List<Run>,
    val markers: List<FlowMarker>,
    val lineups: List<Lineup>,
    val shots: List<Shot>,
) {
    companion object {
        fun build(setup: GameSetup, events: List<GameEvent>, state: com.ttech.basketscore.game.GameState, clock: Long? = null): GameAnalysis {
            val box = BoxScore.compute(setup, events, state, clock)
            val periods = (1..maxOf(state.period, 1)).toList()
            val quarterBox = periods.associateWith { p -> BoxScore.compute(setup, events, state, clock, setOf(p)) }
            val possessions = Possessions.compute(events)
            val advanced = Side.entries.associateWith { side ->
                Advanced.team(setup, side, box.getValue(side).total, box.getValue(side.other).total, possessions, state.period)
            }
            val scores = Flow.scores(events)
            return GameAnalysis(
                box = box,
                quarterBox = quarterBox,
                possessions = possessions,
                advanced = advanced,
                scores = scores,
                runs = Flow.runs(scores),
                markers = Flow.markers(events),
                lineups = Lineups.compute(setup, events),
                shots = events.filterIsInstance<Shot>(),
            )
        }
    }
}
