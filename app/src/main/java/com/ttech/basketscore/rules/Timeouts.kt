package com.ttech.basketscore.rules

import kotlin.math.ceil

/** 第4Q と各 OT の残り 2:00 以下（Last 2 Minutes） */
object LastTwoMinutes {
    const val MILLIS = 2 * 60 * 1000L

    fun contains(period: Int, remainingMillis: Long): Boolean = period >= Period.QUARTERS && remainingMillis <= MILLIS
}

/** 取ったタイムアウト 1 回（[period] とそのときのゲームクロックの残り） */
data class TakenTimeout(val period: Int, val remainingMillis: Long)

object Timeouts {

    /**
     * スコアシートのタイムアウトの欄に書く数字: そのピリオドの経過時間を分に切り上げたもの（最小 1）。
     * 例: 10 分の Q で残り 4:32 → 6、8 分の Q で残り 3:21 → 5、5 分の OT で残り 0:23 → 5（JBA の TO マニュアル）
     */
    fun sheetMinute(periodMillis: Long, remainingMillis: Long): Int {
        val elapsed = (periodMillis - remainingMillis).coerceIn(0, periodMillis)
        return ceil(elapsed / 60_000.0).toInt().coerceAtLeast(1)
    }

    /**
     * いま（[period] の残り [remainingMillis]）チームが取れるタイムアウトの数。[taken] はそのチームがこれまでに取ったもの。
     * 前半 2・後半 3（第4Q の L2M は 2 まで）・各 OT 1。使わなかった分は次に持ち越さない
     */
    fun available(rules: GameRules, period: Int, remainingMillis: Long, taken: List<TakenTimeout>): Int {
        val left = when {
            Period.isOvertime(period) -> rules.timeoutsPerOvertime - taken.count { it.period == period }
            period <= 2 -> rules.timeoutsFirstHalf - taken.count { it.period <= 2 }
            else -> {
                val secondHalf = rules.timeoutsSecondHalf - taken.count { it.period in 3..4 }
                if (LastTwoMinutes.contains(period, remainingMillis)) {
                    val inL2m = taken.count { it.period == 4 && LastTwoMinutes.contains(it.period, it.remainingMillis) }
                    minOf(secondHalf, rules.timeoutsInLastTwoMinutes - inL2m)
                } else {
                    secondHalf
                }
            }
        }
        return left.coerceAtLeast(0)
    }

    /**
     * 第4Q の L2M までに後半のタイムアウトを 1 回も取らなかったとき、後半の最初の枠に 2 本線を引く（使えなくなった枠）。
     * L2M に入った時点で呼ぶ
     */
    fun firstSecondHalfBoxLost(taken: List<TakenTimeout>): Boolean =
        taken.none { it.period == 3 || (it.period == 4 && !LastTwoMinutes.contains(it.period, it.remainingMillis)) }
}

object TeamFouls {
    /** そのクォーターのチームファウルの数から、罰則（次のファウルからフリースロー）になっているか */
    fun inPenalty(rules: GameRules, foulsInPeriod: Int): Boolean = foulsInPeriod >= rules.teamFoulLimit
}
