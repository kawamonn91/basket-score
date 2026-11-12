package com.ttech.basketscore.game

import com.ttech.basketscore.rules.GameRules
import com.ttech.basketscore.rules.Period
import com.ttech.basketscore.rules.TakenTimeout
import com.ttech.basketscore.rules.TeamFouls
import com.ttech.basketscore.rules.Timeouts

/** ファウルの欄の 1 つ（記号・フリースローの数・相殺・どのピリオドか（色）） */
data class FoulMark(
    val code: FoulCode,
    val freeThrows: Int,
    val cancelled: Boolean,
    val period: Int,
    /** もとの出来事の並びの位置（スコアシートの欄から修正画面へ移るため。-1 は不明） */
    val eventIndex: Int = -1,
) {
    /** スコアシートに書く形（"P2"・"Tc"・"U2"） */
    val text: String
        get() = code.label + (if (freeThrows > 0) freeThrows.toString() else "") + (if (cancelled) "c" else "")
}

/** タイムアウトの欄の 1 つ */
data class TimeoutMark(val period: Int, val remainingMillis: Long?, val sheetMinute: Int?, val eventIndex: Int = -1)

/** ランニングスコアの 1 つ（[total] 点目に、背番号と印を書く） */
data class ScoreMark(
    val side: Side,
    val total: Int,
    val playerId: String,
    val playerNumber: String,
    val kind: ShotKind,
    val period: Int,
    val eventIndex: Int = -1,
)

/** ピリオドの終わりの得点（ランニングスコアの太い○と線） */
data class PeriodEndMark(val period: Int, val scoreA: Int, val scoreB: Int)

/** 1 チームの状態 */
data class TeamState(
    val side: Side,
    val score: Int,
    /** ピリオドごとの得点 */
    val periodPoints: Map<Int, Int>,
    /** 選手ごとのファウルの欄 */
    val playerFouls: Map<String, List<FoulMark>>,
    /** ヘッドコーチの欄（C・B・M・D） */
    val coachFouls: List<FoulMark>,
    /** チームファウルの数（キーは 1〜4。OT は 4 に足す） */
    val teamFouls: Map<Int, Int>,
    val timeouts: List<TimeoutMark>,
    /** コート上の選手（入った順） */
    val onCourt: List<String>,
    /** 出場した選手と、最初に出たピリオド（スターターは 1） */
    val entered: Map<String, Int>,
    /** 5 個目の個人ファウルで退場した選手 */
    val fouledOut: Set<String>,
    /** 失格・退場（GD）した選手 */
    val disqualified: Set<String>,
    val coachDisqualified: Boolean,
    /** 出場時間（F-504）。コートから出るまでに終わった分の合計（ミリ秒） */
    val playingMillis: Map<String, Long> = emptyMap(),
    /** いまコートにいる選手が入ったときの時計の残り（ピリオドの途中だけ） */
    val onCourtSince: Map<String, Long> = emptyMap(),
) {
    fun fouls(playerId: String): List<FoulMark> = playerFouls[playerId].orEmpty()

    /** 出場時間（ミリ秒）。[clock] は今の時計の残り（コートにいる分も足す） */
    fun playingTime(playerId: String, clock: Long?): Long {
        val done = playingMillis[playerId] ?: 0
        val since = onCourtSince[playerId] ?: return done
        return done + if (clock != null) (since - clock).coerceAtLeast(0) else 0
    }

    fun teamFoulsIn(period: Int): Int = teamFouls[Period.teamFoulPeriod(period)] ?: 0

    /** その選手がもう出られないか（5 ファウルか GD） */
    fun isOut(playerId: String): Boolean = playerId in fouledOut || playerId in disqualified
}

/** 画面で知らせること（直前の出来事で起きたこと） */
sealed interface Alert {
    data class FifthFoul(val side: Side, val playerId: String) : Alert
    data class Disqualified(val side: Side, val playerId: String?) : Alert
    data class TeamFoulPenalty(val side: Side, val period: Int) : Alert
    data class TimeoutOverLimit(val side: Side) : Alert
    data object EnteredLastTwoMinutes : Alert
}

/** 記録に合わない点（記録は残すが、画面で確かめてもらう） */
sealed interface Warning {
    data class PlayerNotOnCourt(val side: Side, val playerId: String) : Warning
    data class UnknownPlayer(val side: Side, val playerId: String) : Warning
    data class BadSubstitution(val change: SubChange) : Warning
}

/** 試合の状態（出来事の並びから作る。docs/design.md の 2.4） */
data class GameState(
    val setup: GameSetup,
    /** 今のピリオド（まだ始まっていなければ 0） */
    val period: Int,
    /** ピリオドの途中か（PeriodStart のあと、PeriodEnd の前） */
    val periodRunning: Boolean,
    val finished: Boolean,
    val endedAt: String?,
    val a: TeamState,
    val b: TeamState,
    val runningScore: List<ScoreMark>,
    val periodEnds: List<PeriodEndMark>,
    /** ポゼッションアローの向き（次のジャンプボールシチュエーションでボールを得るチーム）。決める前は null */
    val arrow: Side?,
    /** 最後の出来事のゲームクロックの残り */
    val lastClockMillis: Long?,
    /** 第4Q の L2M に入ったか（第4Q が終わった・OT に入った場合も含む） */
    val reachedFourthQuarterLastTwoMinutes: Boolean,
    /** 最後の出来事で起きたこと */
    val alerts: List<Alert>,
    /** 出来事の番号（並びの中の位置）ごとの、記録に合わない点 */
    val warnings: Map<Int, List<Warning>>,
) {
    val rules: GameRules get() = setup.rules

    fun team(side: Side): TeamState = if (side == Side.A) a else b

    fun score(side: Side): Int = team(side).score

    /** そのチームが今（[remainingMillis] の時点で）取れるタイムアウトの数 */
    fun timeoutsAvailable(side: Side, remainingMillis: Long): Int =
        Timeouts.available(rules, period.coerceAtLeast(1), remainingMillis, team(side).timeouts.map { it.toTaken() })

    /** 後半の最初のタイムアウトの枠が使えなくなったか（第4Q の L2M までに後半のタイムアウトを取らなかった） */
    fun firstSecondHalfBoxLost(side: Side): Boolean =
        reachedFourthQuarterLastTwoMinutes && Timeouts.firstSecondHalfBoxLost(team(side).timeouts.map { it.toTaken() })

    fun inPenalty(side: Side): Boolean = TeamFouls.inPenalty(rules, team(side).teamFoulsIn(period.coerceAtLeast(1)))

    private fun TimeoutMark.toTaken() = TakenTimeout(period, remainingMillis ?: Long.MAX_VALUE)

    /** 各ピリオドの得点（A, B）。スコアシートの下の欄 */
    fun periodScores(): List<Triple<Int, Int, Int>> =
        (1..maxOf(period, Period.QUARTERS)).map { p -> Triple(p, a.periodPoints[p] ?: 0, b.periodPoints[p] ?: 0) }

    /** 勝ったチーム（同点・試合中は null） */
    fun winner(): Side? = when {
        !finished -> null
        a.score > b.score -> Side.A
        b.score > a.score -> Side.B
        else -> null
    }
}
