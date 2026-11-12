package com.ttech.basketscore.sheet

import com.ttech.basketscore.game.GameHeader
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.GameState
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.rules.Period

/** スコアシートの色: 第1Q・第3Q は赤、第2Q・第4Q・OT は濃色（JBA の TO マニュアル） */
enum class Ink { RED, DARK }

fun inkFor(period: Int): Ink = if (period == 1 || period == 3) Ink.RED else Ink.DARK

/** ファウルの欄 1 つ */
data class SheetFoul(val text: String, val ink: Ink, val eventIndex: Int)

/** 出場（Player in）の欄。スターターは × を ○ で囲む。途中から出た選手は、出たピリオドの色で × */
data class PlayerIn(val starter: Boolean, val ink: Ink)

data class SheetPlayer(
    val licenseNo: String,
    val name: String,
    val number: String,
    val captain: Boolean,
    val playerIn: PlayerIn?,
    val fouls: List<SheetFoul>,
    /** 前半に使ったファウルの欄の数（前半が終わっていれば、その後ろに太線を引く。終わっていなければ null） */
    val firstHalfFouls: Int?,
    val disqualified: Boolean,
)

data class SheetCoach(val name: String, val licenseNo: String, val fouls: List<SheetFoul>, val disqualified: Boolean)

/** タイムアウトの欄 1 つ */
sealed interface TimeoutBox {
    data object Empty : TimeoutBox
    data class Used(val minute: String, val ink: Ink, val eventIndex: Int) : TimeoutBox
    /** 第4Q の L2M までに後半のタイムアウトを取らなかった（2 本線） */
    data object Lost : TimeoutBox
}

data class SheetTeam(
    val label: String,
    val name: String,
    /** 前半 2・後半 3・OT 3 の枠 */
    val firstHalfTimeouts: List<TimeoutBox>,
    val secondHalfTimeouts: List<TimeoutBox>,
    val overtimeTimeouts: List<TimeoutBox>,
    /** クォーター 1〜4 のチームファウルの数（4 まで。OT は第4Q に足す） */
    val teamFouls: List<Int>,
    val players: List<SheetPlayer>,
    val coach: SheetCoach?,
    val assistantCoach: SheetCoach?,
)

/** ランニングスコアの 1 つの点数の欄 */
data class RunningCell(
    val total: Int,
    val number: String,
    val kind: ShotKind,
    val ink: Ink,
    val eventIndex: Int,
    /** ピリオドの最後の得点（太い○と、下に太い横線） */
    val periodEnd: Boolean,
    /** 試合の最後の得点（太い○と、下に 2 本の太い横線） */
    val gameEnd: Boolean,
)

data class Scoresheet(
    val header: GameHeader,
    val a: SheetTeam,
    val b: SheetTeam,
    val runningA: Map<Int, RunningCell>,
    val runningB: Map<Int, RunningCell>,
    /** 第1〜4Q の得点（A, B）。まだのクォーターは null */
    val quarterScores: List<Pair<Int, Int>?>,
    /** OT の合計（OT が無ければ null） */
    val overtime: Pair<Int, Int>?,
    val finalScore: Pair<Int, Int>?,
    val winner: String?,
    val endedAt: String?,
    val finished: Boolean,
) {
    fun team(side: Side): SheetTeam = if (side == Side.A) a else b

    fun running(side: Side): Map<Int, RunningCell> = if (side == Side.A) runningA else runningB

    /** ランニングスコアに要る枚数（1 枚に 160 点） */
    val pages: Int get() = ((maxOf(runningA.keys.maxOrNull() ?: 0, runningB.keys.maxOrNull() ?: 0) - 1) / POINTS_PER_PAGE + 1).coerceAtLeast(1)

    companion object {
        const val PLAYER_ROWS = 18
        const val POINTS_PER_PAGE = 160

        fun build(setup: GameSetup, state: GameState): Scoresheet {
            val endedPeriods = state.periodEnds.map { it.period }.toSet()
            val halfEnded = 2 in endedPeriods || state.period > 2
            fun team(side: Side): SheetTeam {
                val t = setup.team(side)
                val s = state.team(side)
                val players = t.players.take(PLAYER_ROWS).map { p ->
                    val fouls = s.fouls(p.id).map { SheetFoul(it.text, inkFor(it.period), it.eventIndex) }
                    val entered = s.entered[p.id]
                    SheetPlayer(
                        licenseNo = p.licenseNo,
                        name = p.name,
                        number = p.number,
                        captain = p.captain,
                        playerIn = when {
                            p.starter -> PlayerIn(starter = true, ink = Ink.RED)
                            entered != null -> PlayerIn(starter = false, ink = inkFor(entered))
                            else -> null
                        },
                        fouls = fouls,
                        firstHalfFouls = if (halfEnded) s.fouls(p.id).count { it.period <= 2 } else null,
                        disqualified = p.id in s.disqualified,
                    )
                }
                val used = s.timeouts.map { TimeoutBox.Used(it.sheetMinute?.toString() ?: "", inkFor(it.period), it.eventIndex) }
                val first = used.filterIndexed { i, _ -> s.timeouts[i].period <= 2 }
                val second = used.filterIndexed { i, _ -> s.timeouts[i].period in 3..4 }
                val ot = used.filterIndexed { i, _ -> Period.isOvertime(s.timeouts[i].period) }
                val secondBoxes = buildList {
                    if (state.firstSecondHalfBoxLost(side)) add(TimeoutBox.Lost)
                    addAll(second)
                }
                return SheetTeam(
                    label = side.name,
                    name = t.name,
                    firstHalfTimeouts = boxes(first, 2),
                    secondHalfTimeouts = boxes(secondBoxes, 3),
                    overtimeTimeouts = boxes(ot, 3),
                    teamFouls = (1..4).map { q -> (s.teamFouls[q] ?: 0).coerceAtMost(4) },
                    players = players,
                    coach = t.headCoach?.let { SheetCoach(it.name, it.licenseNo, s.coachFouls.map { f -> SheetFoul(f.text, inkFor(f.period), f.eventIndex) }, s.coachDisqualified) },
                    assistantCoach = t.assistantCoach?.let { SheetCoach(it.name, it.licenseNo, emptyList(), false) },
                )
            }

            fun running(side: Side): Map<Int, RunningCell> {
                val marks = state.runningScore.filter { it.side == side }
                // ピリオドごとの、そのチームの最後の得点
                val lastOfPeriod = marks.groupBy { it.period }.filterKeys { it in endedPeriods }.mapValues { it.value.last().total }
                val lastTotal = marks.lastOrNull()?.total
                return marks.associate { m ->
                    m.total to RunningCell(
                        total = m.total,
                        number = m.playerNumber,
                        kind = m.kind,
                        ink = inkFor(m.period),
                        eventIndex = m.eventIndex,
                        periodEnd = lastOfPeriod[m.period] == m.total,
                        gameEnd = state.finished && m.total == lastTotal,
                    )
                }
            }

            val quarterScores = (1..4).map { q ->
                if (q in endedPeriods || q < state.period || (q == state.period && state.finished)) {
                    (state.a.periodPoints[q] ?: 0) to (state.b.periodPoints[q] ?: 0)
                } else {
                    null
                }
            }
            val overtime = if (state.period > Period.QUARTERS) {
                state.a.periodPoints.filterKeys { it > 4 }.values.sum() to state.b.periodPoints.filterKeys { it > 4 }.values.sum()
            } else {
                null
            }
            return Scoresheet(
                header = setup.header,
                a = team(Side.A),
                b = team(Side.B),
                runningA = running(Side.A),
                runningB = running(Side.B),
                quarterScores = quarterScores,
                overtime = overtime,
                finalScore = if (state.finished) state.a.score to state.b.score else null,
                winner = state.winner()?.let { setup.team(it).name },
                endedAt = state.endedAt,
                finished = state.finished,
            )
        }

        private fun boxes(used: List<TimeoutBox>, count: Int): List<TimeoutBox> =
            (used + List((count - used.size).coerceAtLeast(0)) { TimeoutBox.Empty }).take(maxOf(count, used.size))
    }
}
