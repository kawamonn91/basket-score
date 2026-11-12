package com.ttech.basketscore.game

import com.ttech.basketscore.rules.LastTwoMinutes
import com.ttech.basketscore.rules.Period
import com.ttech.basketscore.rules.TakenTimeout
import com.ttech.basketscore.rules.TeamFouls
import com.ttech.basketscore.rules.Timeouts

/**
 * 出来事の並びを先頭から順に適用して、試合の状態を作る（docs/design.md の 1 章）。
 * 記録に合わない出来事（コートにいない選手の得点など）も捨てずに適用し、[GameState.warnings] で知らせる
 */
object GameEngine {

    fun replay(setup: GameSetup, events: List<GameEvent>): GameState {
        val builder = Builder(setup)
        events.forEachIndexed { index, event -> builder.apply(index, event) }
        return builder.build()
    }

    private class TeamBuilder(val side: Side, val setup: TeamSetup) {
        var score = 0
        val periodPoints = mutableMapOf<Int, Int>()
        val playerFouls = mutableMapOf<String, MutableList<FoulMark>>()
        val coachFouls = mutableListOf<FoulMark>()
        val teamFouls = mutableMapOf<Int, Int>()
        val timeouts = mutableListOf<TimeoutMark>()
        val onCourt = setup.starters.map { it.id }.toMutableList()
        val entered = setup.starters.associate { it.id to 1 }.toMutableMap()
        val fouledOut = mutableSetOf<String>()
        val disqualified = mutableSetOf<String>()
        var coachDisqualified = false

        fun build() = TeamState(
            side = side,
            score = score,
            periodPoints = periodPoints.toMap(),
            playerFouls = playerFouls.mapValues { it.value.toList() },
            coachFouls = coachFouls.toList(),
            teamFouls = teamFouls.toMap(),
            timeouts = timeouts.toList(),
            onCourt = onCourt.toList(),
            entered = entered.toMap(),
            fouledOut = fouledOut.toSet(),
            disqualified = disqualified.toSet(),
            coachDisqualified = coachDisqualified,
        )
    }

    private class Builder(val setup: GameSetup) {
        val a = TeamBuilder(Side.A, setup.teamA)
        val b = TeamBuilder(Side.B, setup.teamB)
        var period = 0
        var periodRunning = false
        var finished = false
        var endedAt: String? = null
        val runningScore = mutableListOf<ScoreMark>()
        val periodEnds = mutableListOf<PeriodEndMark>()
        var arrow: Side? = null
        var lastClock: Long? = null
        var inL2m = false
        var reachedQ4L2m = false
        var alerts = mutableListOf<Alert>()
        val warnings = mutableMapOf<Int, MutableList<Warning>>()

        fun team(side: Side) = if (side == Side.A) a else b

        fun warn(index: Int, warning: Warning) {
            warnings.getOrPut(index) { mutableListOf() } += warning
        }

        fun apply(index: Int, event: GameEvent) {
            alerts = mutableListOf()
            if (event.period > period) period = event.period
            event.clockMillis?.let { lastClock = it }
            when (event) {
                is PeriodStart -> {
                    period = event.period
                    periodRunning = true
                    inL2m = false
                    // 第2Q 以降と OT の初めのスローインは、アローの向きで決めて反転する（JBA の TO マニュアル）
                    if (event.period >= 2) arrow = arrow?.other
                }
                is PeriodEnd -> {
                    periodRunning = false
                    periodEnds += PeriodEndMark(event.period, a.score, b.score)
                    if (event.period >= Period.QUARTERS) reachedQ4L2m = true
                }
                is Shot -> applyShot(index, event)
                is Foul -> applyFoul(index, event)
                is Timeout -> applyTimeout(event)
                is Substitution -> applySubstitution(index, event)
                is ArrowSet -> arrow = event.toward
                is JumpBallSituation -> arrow = arrow?.other
                is Rebound -> event.playerId?.let { checkOnCourt(index, event.side, it) }
                is Turnover -> {
                    event.playerId?.let { checkOnCourt(index, event.side, it) }
                    event.stealById?.let { checkOnCourt(index, event.side.other, it) }
                }
                is Deflection -> checkOnCourt(index, event.side, event.playerId)
                is PossessionChange -> Unit
                is GameEnd -> {
                    finished = true
                    periodRunning = false
                    endedAt = event.endedAt
                    if (periodEnds.none { it.period == event.period }) {
                        periodEnds += PeriodEndMark(event.period, a.score, b.score)
                    }
                }
            }
            val clock = event.clockMillis
            if (!inL2m && clock != null && LastTwoMinutes.contains(period, clock) && event !is PeriodEnd && event !is GameEnd) {
                inL2m = true
                if (period == Period.QUARTERS) alerts += Alert.EnteredLastTwoMinutes
            }
            if (period > Period.QUARTERS || (period == Period.QUARTERS && inL2m)) reachedQ4L2m = true
        }

        fun checkOnCourt(index: Int, side: Side, playerId: String) {
            if (playerId == RosterPlayer.UNKNOWN) return
            val team = team(side)
            if (team.setup.player(playerId) == null) {
                warn(index, Warning.UnknownPlayer(side, playerId))
            } else if (playerId !in team.onCourt) {
                warn(index, Warning.PlayerNotOnCourt(side, playerId))
            }
        }

        fun applyShot(index: Int, shot: Shot) {
            checkOnCourt(index, shot.side, shot.playerId)
            shot.assistId?.let { checkOnCourt(index, shot.side, it) }
            shot.blockId?.let { checkOnCourt(index, shot.side.other, it) }
            if (!shot.made) return
            val team = team(shot.side)
            team.score += shot.kind.points
            team.periodPoints.merge(shot.period, shot.kind.points, Int::plus)
            val number = team.setup.player(shot.playerId)?.number ?: "?"
            runningScore += ScoreMark(shot.side, team.score, shot.playerId, number, shot.kind, shot.period)
        }

        fun applyFoul(index: Int, foul: Foul) {
            val team = team(foul.side)
            val mark = FoulMark(foul.code, foul.freeThrows, foul.cancelled, foul.period)
            when (foul.offender) {
                Offender.PLAYER -> {
                    val playerId = foul.playerId
                    if (playerId == null) {
                        return
                    }
                    if (playerId != RosterPlayer.UNKNOWN && team.setup.player(playerId) == null) {
                        warn(index, Warning.UnknownPlayer(foul.side, playerId))
                    }
                    val marks = team.playerFouls.getOrPut(playerId) { mutableListOf() }
                    marks += mark
                    // 選手のファウルはどの記号もチームファウルに数える（コーチのファウルは数えない）
                    val tfPeriod = Period.teamFoulPeriod(foul.period)
                    val before = team.teamFouls[tfPeriod] ?: 0
                    team.teamFouls[tfPeriod] = before + 1
                    if (!TeamFouls.inPenalty(setup.rules, before) && TeamFouls.inPenalty(setup.rules, before + 1)) {
                        alerts += Alert.TeamFoulPenalty(foul.side, foul.period)
                    }
                    if (playerId == RosterPlayer.UNKNOWN) {
                        // 「？」のファウルは、選手に直すまで退場の判定をしない
                    } else if (playerId !in team.disqualified && playerDisqualified(marks)) {
                        team.disqualified += playerId
                        team.onCourt.remove(playerId)
                        alerts += Alert.Disqualified(foul.side, playerId)
                    } else if (playerId !in team.fouledOut && marks.size >= setup.rules.playerFoulLimit) {
                        team.fouledOut += playerId
                        alerts += Alert.FifthFoul(foul.side, playerId)
                    }
                }
                Offender.HEAD_COACH -> {
                    team.coachFouls += mark
                    if (!team.coachDisqualified && coachDisqualified(team.coachFouls)) {
                        team.coachDisqualified = true
                        alerts += Alert.Disqualified(foul.side, null)
                    }
                }
            }
        }

        /** 選手の失格・退場: T 2 個・U 2 個・T と U が 1 個ずつ、D か F 1 個 */
        fun playerDisqualified(marks: List<FoulMark>): Boolean {
            val t = marks.count { it.code == FoulCode.T }
            val u = marks.count { it.code == FoulCode.U }
            val d = marks.count { it.code == FoulCode.D || it.code == FoulCode.F }
            return d >= 1 || t >= 2 || u >= 2 || (t >= 1 && u >= 1)
        }

        /** ヘッドコーチの失格・退場: C 2 個・B 3 個・B 2 個と C 1 個・D か F 1 個・M 2 個（U15） */
        fun coachDisqualified(marks: List<FoulMark>): Boolean {
            val c = marks.count { it.code == FoulCode.C }
            val bench = marks.count { it.code == FoulCode.B }
            val d = marks.count { it.code == FoulCode.D || it.code == FoulCode.F }
            val m = marks.count { it.code == FoulCode.M }
            return d >= 1 || c >= 2 || bench >= 3 || (bench >= 2 && c >= 1) ||
                (setup.rules.manToManPenalty && m >= 2)
        }

        fun applyTimeout(timeout: Timeout) {
            val team = team(timeout.side)
            val clock = timeout.clockMillis
            val available = Timeouts.available(
                setup.rules,
                timeout.period,
                clock ?: Long.MAX_VALUE,
                team.timeouts.map { TakenTimeout(it.period, it.remainingMillis ?: Long.MAX_VALUE) },
            )
            if (available <= 0) alerts += Alert.TimeoutOverLimit(timeout.side)
            val minute = timeout.minute ?: clock?.let { Timeouts.sheetMinute(setup.rules.periodMillis(timeout.period), it) }
            team.timeouts += TimeoutMark(timeout.period, clock, minute)
        }

        fun applySubstitution(index: Int, sub: Substitution) {
            for (change in sub.changes) {
                val team = team(change.side)
                val outIndex = team.onCourt.indexOf(change.outId)
                val known = team.setup.player(change.inId) != null
                if (outIndex < 0 || !known || change.inId in team.onCourt) {
                    warn(index, Warning.BadSubstitution(change))
                    continue
                }
                team.onCourt[outIndex] = change.inId
                team.entered.putIfAbsent(change.inId, sub.period)
            }
        }

        fun build() = GameState(
            setup = setup,
            period = period,
            periodRunning = periodRunning,
            finished = finished,
            endedAt = endedAt,
            a = a.build(),
            b = b.build(),
            runningScore = runningScore.toList(),
            periodEnds = periodEnds.toList(),
            arrow = arrow,
            lastClockMillis = lastClock,
            reachedFourthQuarterLastTwoMinutes = reachedQ4L2m,
            alerts = alerts.toList(),
            warnings = warnings.mapValues { it.value.toList() },
        )
    }
}
