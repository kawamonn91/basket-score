package com.ttech.basketscore.stats

import com.ttech.basketscore.game.Deflection
import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.FoulCode
import com.ttech.basketscore.game.GameEngine
import com.ttech.basketscore.game.GameEvent
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.GameState
import com.ttech.basketscore.game.Offender
import com.ttech.basketscore.game.PlayTypes
import com.ttech.basketscore.game.Rebound
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.Turnover

/** ボックススコアの 1 行（選手・チームの記録・合計。F-601） */
data class StatLine(
    val pts: Int = 0,
    val fgm: Int = 0,
    val fga: Int = 0,
    val tpm: Int = 0,
    val tpa: Int = 0,
    val ftm: Int = 0,
    val fta: Int = 0,
    val or: Int = 0,
    val dr: Int = 0,
    val ast: Int = 0,
    val stl: Int = 0,
    val blk: Int = 0,
    val tov: Int = 0,
    val pf: Int = 0,
    val defl: Int = 0,
    /** 出場時間（ミリ秒。時計を使わない試合は null） */
    val minutes: Long? = null,
    /** 先発（1 か 0） */
    val gs: Int = 0,
    /** ダンクの成功 */
    val dk: Int = 0,
    /** テクニカルファウル（T） */
    val tf: Int = 0,
    /** オフェンスファウル */
    val of: Int = 0,
    /** ファウルアウト（5 個）・失格（GD）（1 か 0） */
    val fo: Int = 0,
    val dq: Int = 0,
    /** コートにいた間の得失点差（F-608） */
    val plusMinus: Int = 0,
    /** 出場したか（出場しなかった選手は DNP） */
    val played: Boolean = false,
) {
    val reb: Int get() = or + dr

    /** 2P の成功と試投 */
    val twoM: Int get() = fgm - tpm
    val twoA: Int get() = fga - tpa

    /** 貢献度（EFF。付録 A） */
    val eff: Int get() = pts + reb + ast + stl + blk - (fga - fgm) - (fta - ftm) - tov

    operator fun plus(o: StatLine) = StatLine(
        pts + o.pts, fgm + o.fgm, fga + o.fga, tpm + o.tpm, tpa + o.tpa, ftm + o.ftm, fta + o.fta,
        or + o.or, dr + o.dr, ast + o.ast, stl + o.stl, blk + o.blk, tov + o.tov, pf + o.pf, defl + o.defl,
        if (minutes == null && o.minutes == null) null else (minutes ?: 0) + (o.minutes ?: 0),
        gs + o.gs, dk + o.dk, tf + o.tf, of + o.of, fo + o.fo, dq + o.dq, plusMinus + o.plusMinus, played || o.played,
    )
}

/** 1 チームのボックススコア */
data class TeamBox(
    /** 選手ごと（キーは試合の中の選手の ID。「？」の記録は [com.ttech.basketscore.game.RosterPlayer.UNKNOWN]） */
    val players: Map<String, StatLine>,
    /** チームの記録（チームリバウンド・チームのターンオーバー） */
    val team: StatLine,
) {
    /** チームの合計（出場時間と +/- は足さない） */
    val total: StatLine get() = players.values.fold(team) { acc, line -> acc + line.copy(minutes = null, plusMinus = 0) }

    fun player(id: String): StatLine = players[id] ?: StatLine()
}

/** 記録からボックススコアを数える（F-410・F-601） */
object BoxScore {

    /**
     * @param state 試合の状態（出場時間・ファウルアウト・失格・出場したかに使う）
     * @param clock 今の時計の残り（コートにいる選手の出場時間に足す）
     * @param periods 数えるピリオド（null なら全体。クォーターごとのボックススコア）
     */
    fun compute(
        setup: GameSetup,
        events: List<GameEvent>,
        state: GameState? = null,
        clock: Long? = null,
        periods: Set<Int>? = null,
    ): Map<Side, TeamBox> {
        val lines = Side.entries.associateWith { mutableMapOf<String, StatLine>() }
        val teamLines = Side.entries.associateWith { StatLine() }.toMutableMap()
        val timeline = GameEngine.timeline(setup, events)

        fun bump(side: Side, playerId: String?, f: (StatLine) -> StatLine) {
            if (playerId == null) {
                teamLines[side] = f(teamLines.getValue(side))
            } else {
                val map = lines.getValue(side)
                map[playerId] = f(map[playerId] ?: StatLine())
            }
        }

        events.forEachIndexed { index, e ->
            if (periods != null && e.period !in periods) return@forEachIndexed
            when (e) {
                is Shot -> {
                    val made = if (e.made) 1 else 0
                    val pts = e.kind.points * made
                    bump(e.side, e.playerId) {
                        when (e.kind) {
                            ShotKind.FT -> it.copy(pts = it.pts + pts, ftm = it.ftm + made, fta = it.fta + 1)
                            ShotKind.TWO -> it.copy(
                                pts = it.pts + pts, fgm = it.fgm + made, fga = it.fga + 1,
                                dk = it.dk + if (e.made && e.shotType == PlayTypes.DUNK) 1 else 0,
                            )
                            ShotKind.THREE -> it.copy(pts = it.pts + pts, fgm = it.fgm + made, fga = it.fga + 1, tpm = it.tpm + made, tpa = it.tpa + 1)
                        }
                    }
                    if (e.made) e.assistId?.let { a -> bump(e.side, a) { it.copy(ast = it.ast + 1) } }
                    if (!e.made) e.blockId?.let { b -> bump(e.side.other, b) { it.copy(blk = it.blk + 1) } }
                    // +/-: シュートのときにコートにいた選手（F-608）
                    if (pts > 0) {
                        val snap = timeline[index]
                        snap.onCourt(e.side).forEach { id -> bump(e.side, id) { it.copy(plusMinus = it.plusMinus + pts) } }
                        snap.onCourt(e.side.other).forEach { id -> bump(e.side.other, id) { it.copy(plusMinus = it.plusMinus - pts) } }
                    }
                }
                is Rebound -> bump(e.side, e.playerId) { if (e.offensive) it.copy(or = it.or + 1) else it.copy(dr = it.dr + 1) }
                is Turnover -> {
                    bump(e.side, e.playerId) { it.copy(tov = it.tov + 1) }
                    e.stealById?.let { s -> bump(e.side.other, s) { it.copy(stl = it.stl + 1) } }
                }
                is Deflection -> bump(e.side, e.playerId) { it.copy(defl = it.defl + 1) }
                is Foul -> if (e.offender == Offender.PLAYER && e.playerId != null) {
                    // オフェンスファウルはターンオーバーにも数える
                    bump(e.side, e.playerId) {
                        it.copy(
                            pf = it.pf + 1,
                            tov = it.tov + if (e.offensive) 1 else 0,
                            of = it.of + if (e.offensive) 1 else 0,
                            tf = it.tf + if (e.code == FoulCode.T) 1 else 0,
                        )
                    }
                }
                else -> Unit
            }
        }

        return Side.entries.associateWith { side ->
            val map = lines.getValue(side)
            val roster = setup.team(side).players
            // 出場登録の全員を並べ、先発・出場時間・退場を入れる
            val ids = roster.map { it.id } + map.keys
            val teamState = state?.team(side)
            val players = ids.distinct().associateWith { id ->
                val line = map[id] ?: StatLine()
                val minutes = when {
                    !setup.useClock || teamState == null -> null
                    periods == null -> teamState.playingTime(id, clock)
                    else -> {
                        val done = periods.sumOf { teamState.playingByPeriod[id]?.get(it) ?: 0L }
                        val ongoing = if (state.period in periods && clock != null) {
                            teamState.onCourtSince[id]?.let { (it - clock).coerceAtLeast(0) } ?: 0L
                        } else {
                            0L
                        }
                        done + ongoing
                    }
                }
                line.copy(
                    minutes = minutes,
                    gs = if (roster.any { it.id == id && it.starter }) 1 else 0,
                    fo = if (teamState != null && id in teamState.fouledOut) 1 else 0,
                    dq = if (teamState != null && id in teamState.disqualified) 1 else 0,
                    played = teamState?.entered?.containsKey(id) ?: (line != StatLine()),
                )
            }
            TeamBox(players, teamLines.getValue(side))
        }
    }
}
