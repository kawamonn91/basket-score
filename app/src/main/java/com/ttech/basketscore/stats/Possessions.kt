package com.ttech.basketscore.stats

import com.ttech.basketscore.game.ArrowSet
import com.ttech.basketscore.game.ClockRules
import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.GameEnd
import com.ttech.basketscore.game.GameEvent
import com.ttech.basketscore.game.JumpBallSituation
import com.ttech.basketscore.game.PeriodEnd
import com.ttech.basketscore.game.PeriodStart
import com.ttech.basketscore.game.PossessionChange
import com.ttech.basketscore.game.Rebound
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.Turnover

/** 1 回の攻撃（ポゼッション） */
data class Possession(
    val side: Side,
    val period: Int,
    /** 始まった出来事の位置 */
    val startIndex: Int,
    /** 終わった出来事の位置（続いていれば null） */
    val endIndex: Int?,
    /** 始まったときと終わったときの時計の残り（時刻が無ければ null） */
    val startClock: Long?,
    val endClock: Long?,
    /** この攻撃で取った点 */
    val points: Int,
) {
    /** 攻撃時間（ミリ秒。F-505）。続いている攻撃は [now] までを数える */
    fun duration(now: Long? = null): Long? {
        val start = startClock ?: return null
        val end = endClock ?: now ?: return null
        return (start - end).coerceAtLeast(0)
    }
}

/**
 * 攻守の交代を、記録から自動で判断する（F-411）。
 * 成功したシュート・最後のフリースローの成功・ディフェンスリバウンド・ターンオーバー・オフェンスファウルで相手へ移る。
 * オフェンスリバウンドは同じ攻撃が続く。ピリオドの初めとジャンプボールシチュエーションはアローで決める。
 * 記録が足りずに食い違ったとき（攻めていないはずのチームの得点など）は、その記録のチームが攻めていたとみなして直す。
 * 判断できない場面は [PossessionChange] で手で直す
 */
object Possessions {

    fun compute(events: List<GameEvent>): List<Possession> {
        val result = mutableListOf<Possession>()
        var owner: Side? = null
        var start = 0
        var startClock: Long? = null
        var startPeriod = 0
        var points = 0
        var arrow: Side? = null
        var period = 0

        fun change(newOwner: Side?, index: Int, clock: Long?) {
            if (newOwner == owner) return
            owner?.let { result += Possession(it, startPeriod, start, index, startClock, clock, points) }
            owner = newOwner
            start = index
            startClock = clock
            startPeriod = period
            points = 0
        }

        events.forEachIndexed { i, e ->
            val clock = e.clockMillis
            if (e.period > period) period = e.period
            when (e) {
                is PeriodStart -> {
                    period = e.period
                    if (e.period >= 2) {
                        change(arrow, i, clock)
                        arrow = arrow?.other
                    }
                }
                is ArrowSet -> {
                    arrow = e.toward
                    // 試合の初めのジャンプボールを取ったチーム（アローの向きの反対）が攻める
                    change(e.toward.other, i, clock)
                }
                is JumpBallSituation -> {
                    change(arrow, i, clock)
                    arrow = arrow?.other
                }
                is Shot -> {
                    if (owner != e.side) change(e.side, i, clock)
                    if (e.made) points += e.kind.points
                    val ends = e.made && (e.kind != ShotKind.FT || ClockRules.isLastFreeThrow(events.subList(0, i), e))
                    if (ends) change(e.side.other, i, clock)
                }
                is Rebound -> if (e.offensive) {
                    if (owner != e.side) change(e.side, i, clock)
                } else {
                    change(e.side, i, clock)
                }
                is Turnover -> {
                    if (owner != e.side) change(e.side, i, clock)
                    change(e.side.other, i, clock)
                }
                is Foul -> if (e.offensive) {
                    if (owner != e.side) change(e.side, i, clock)
                    change(e.side.other, i, clock)
                }
                is PossessionChange -> change(e.side, i, clock)
                is PeriodEnd -> change(null, i, clock ?: 0)
                is GameEnd -> change(null, i, clock ?: 0)
                else -> Unit
            }
        }
        owner?.let { result += Possession(it, startPeriod, start, null, startClock, null, points) }
        return result
    }

    /** 今攻めているチームの攻撃（ピリオドの外なら null） */
    fun current(events: List<GameEvent>): Possession? = compute(events).lastOrNull()?.takeIf { it.endIndex == null }

    /** チームごとの攻撃回数 */
    fun count(possessions: List<Possession>, side: Side): Int = possessions.count { it.side == side }
}

/** スタッツの小さな決まり */
object StatsRules {
    /**
     * リバウンドがオフェンスかディフェンスか（F-403）。直前の外れたシュートが同じチームならオフェンス。
     * 外れたシュートが無ければディフェンスとみなす
     */
    fun reboundIsOffensive(events: List<GameEvent>, side: Side): Boolean {
        val last = events.lastOrNull { it is Shot && !it.made || it is Rebound || it is Turnover || it is PeriodStart }
        return last is Shot && last.side == side
    }

    /** 外れたあと、まだリバウンドが入っていないシュート（リバウンドを聞くため） */
    fun awaitingRebound(events: List<GameEvent>): Shot? {
        val index = events.indexOfLast { it is Shot || it is Rebound || it is Turnover || it is PeriodStart || it is PeriodEnd }
        val shot = events.getOrNull(index) as? Shot ?: return null
        if (shot.made) return null
        // フリースローは最後の 1 本が外れたときだけ
        if (shot.kind == ShotKind.FT && !ClockRules.isLastFreeThrow(events.subList(0, index), shot)) return null
        return shot
    }
}
