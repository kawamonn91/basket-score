package com.ttech.basketscore.stats

import com.ttech.basketscore.game.Deflection
import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.GameEvent
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.GameState
import com.ttech.basketscore.game.Offender
import com.ttech.basketscore.game.Rebound
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.Turnover

/** ボックススコアの 1 行（選手・チームの記録・合計） */
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
) {
    val reb: Int get() = or + dr

    /** 2P の成功と試投 */
    val twoM: Int get() = fgm - tpm
    val twoA: Int get() = fga - tpa

    operator fun plus(o: StatLine) = StatLine(
        pts + o.pts, fgm + o.fgm, fga + o.fga, tpm + o.tpm, tpa + o.tpa, ftm + o.ftm, fta + o.fta,
        or + o.or, dr + o.dr, ast + o.ast, stl + o.stl, blk + o.blk, tov + o.tov, pf + o.pf, defl + o.defl,
        if (minutes == null && o.minutes == null) null else (minutes ?: 0) + (o.minutes ?: 0),
    )
}

/** 1 チームのボックススコア */
data class TeamBox(
    /** 選手ごと（キーは試合の中の選手の ID。「？」の記録は [com.ttech.basketscore.game.RosterPlayer.UNKNOWN]） */
    val players: Map<String, StatLine>,
    /** チームの記録（チームリバウンド・チームのターンオーバー） */
    val team: StatLine,
) {
    val total: StatLine get() = players.values.fold(team) { acc, line -> acc + line.copy(minutes = null) }

    fun player(id: String): StatLine = players[id] ?: StatLine()
}

/** 記録からボックススコアを数える（F-410・F-601） */
object BoxScore {

    fun compute(setup: GameSetup, events: List<GameEvent>, state: GameState? = null, clock: Long? = null): Map<Side, TeamBox> {
        val lines = Side.entries.associateWith { mutableMapOf<String, StatLine>() }
        val teamLines = Side.entries.associateWith { StatLine() }.toMutableMap()

        fun bump(side: Side, playerId: String?, f: (StatLine) -> StatLine) {
            if (playerId == null) {
                teamLines[side] = f(teamLines.getValue(side))
            } else {
                val map = lines.getValue(side)
                map[playerId] = f(map[playerId] ?: StatLine())
            }
        }

        for (e in events) {
            when (e) {
                is Shot -> {
                    val pts = if (e.made) e.kind.points else 0
                    bump(e.side, e.playerId) {
                        when (e.kind) {
                            ShotKind.FT -> it.copy(pts = it.pts + pts, ftm = it.ftm + (if (e.made) 1 else 0), fta = it.fta + 1)
                            ShotKind.TWO -> it.copy(pts = it.pts + pts, fgm = it.fgm + (if (e.made) 1 else 0), fga = it.fga + 1)
                            ShotKind.THREE -> it.copy(
                                pts = it.pts + pts,
                                fgm = it.fgm + (if (e.made) 1 else 0), fga = it.fga + 1,
                                tpm = it.tpm + (if (e.made) 1 else 0), tpa = it.tpa + 1,
                            )
                        }
                    }
                    if (e.made) e.assistId?.let { a -> bump(e.side, a) { it.copy(ast = it.ast + 1) } }
                    if (!e.made) e.blockId?.let { b -> bump(e.side.other, b) { it.copy(blk = it.blk + 1) } }
                }
                is Rebound -> bump(e.side, e.playerId) { if (e.offensive) it.copy(or = it.or + 1) else it.copy(dr = it.dr + 1) }
                is Turnover -> {
                    bump(e.side, e.playerId) { it.copy(tov = it.tov + 1) }
                    e.stealById?.let { s -> bump(e.side.other, s) { it.copy(stl = it.stl + 1) } }
                }
                is Deflection -> bump(e.side, e.playerId) { it.copy(defl = it.defl + 1) }
                is Foul -> if (e.offender == Offender.PLAYER && e.playerId != null) {
                    // オフェンスファウルはターンオーバーにも数える
                    bump(e.side, e.playerId) { it.copy(pf = it.pf + 1, tov = it.tov + if (e.offensive) 1 else 0) }
                }
                else -> Unit
            }
        }

        return Side.entries.associateWith { side ->
            val map = lines.getValue(side)
            // 出場登録の全員を並べ、出場時間を入れる
            val ids = setup.team(side).players.map { it.id } + map.keys
            val players = ids.distinct().associateWith { id ->
                val line = map[id] ?: StatLine()
                val minutes = if (setup.useClock && state != null) state.team(side).playingTime(id, clock) else null
                line.copy(minutes = minutes)
            }
            TeamBox(players, teamLines.getValue(side))
        }
    }
}
