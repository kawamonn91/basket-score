package com.ttech.basketscore.game

import com.ttech.basketscore.rules.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private fun ms(min: Int, sec: Int) = (min * 60 + sec) * 1000L

    private fun team(prefix: String, benchLeft: Boolean) = TeamSetup(
        name = "Team $prefix",
        benchLeft = benchLeft,
        headCoach = Staff("コーチ $prefix"),
        players = (4..12).map { n ->
            RosterPlayer(id = "$prefix$n", number = n.toString(), name = "選手 $prefix$n", captain = n == 4, starter = n <= 8)
        },
    )

    private val setup = GameSetup(teamA = team("a", benchLeft = true), teamB = team("b", benchLeft = false))

    private fun state(vararg events: GameEvent, setup: GameSetup = this.setup) = GameEngine.replay(setup, events.toList())

    @Test
    fun startersAreOnCourtAndEnteredInTheFirstQuarter() {
        val s = state()
        assertEquals(0, s.period)
        assertEquals(listOf("a4", "a5", "a6", "a7", "a8"), s.a.onCourt)
        assertEquals(1, s.a.entered["a4"])
        assertNull(s.a.entered["a9"])
    }

    @Test
    fun runningScoreMarksEachTotalWithThePlayerAndKind() {
        val s = state(
            PeriodStart(1, ms(10, 0)),
            Shot(1, ms(9, 40), Side.A, "a4", ShotKind.TWO, made = true),
            Shot(1, ms(9, 20), Side.B, "b5", ShotKind.THREE, made = true),
            Shot(1, ms(9, 0), Side.A, "a5", ShotKind.THREE, made = false),
            Shot(1, ms(8, 50), Side.A, "a6", ShotKind.FT, made = true),
            Shot(1, ms(8, 50), Side.A, "a6", ShotKind.FT, made = true),
            PeriodEnd(1, 0),
            PeriodStart(2, ms(10, 0)),
            Shot(2, ms(9, 0), Side.B, "b6", ShotKind.TWO, made = true),
        )
        assertEquals(4, s.a.score)
        assertEquals(5, s.b.score)
        assertEquals(
            listOf(
                ScoreMark(Side.A, 2, "a4", "4", ShotKind.TWO, 1, eventIndex = 1),
                ScoreMark(Side.B, 3, "b5", "5", ShotKind.THREE, 1, eventIndex = 2),
                ScoreMark(Side.A, 3, "a6", "6", ShotKind.FT, 1, eventIndex = 4),
                ScoreMark(Side.A, 4, "a6", "6", ShotKind.FT, 1, eventIndex = 5),
                ScoreMark(Side.B, 5, "b6", "6", ShotKind.TWO, 2, eventIndex = 8),
            ),
            s.runningScore,
        )
        assertEquals(listOf(PeriodEndMark(1, 4, 3)), s.periodEnds)
        assertEquals(mapOf(1 to 4), s.a.periodPoints)
        assertEquals(listOf(Triple(1, 4, 3), Triple(2, 0, 2), Triple(3, 0, 0), Triple(4, 0, 0)), s.periodScores())
        assertTrue(s.periodRunning)
    }

    @Test
    fun foulMarksAndTeamFoulsPerQuarter() {
        val s = state(
            PeriodStart(1, ms(10, 0)),
            Foul(1, ms(9, 0), Side.A, playerId = "a4", code = FoulCode.P),
            Foul(1, ms(8, 0), Side.A, playerId = "a4", code = FoulCode.P, freeThrows = 2),
            Foul(1, ms(7, 0), Side.A, playerId = "a5", code = FoulCode.T, freeThrows = 1),
            Foul(1, ms(6, 0), Side.A, playerId = "a6", code = FoulCode.P, freeThrows = 2, cancelled = true),
            // コーチのファウルはチームファウルに数えない
            Foul(1, ms(5, 0), Side.A, offender = Offender.HEAD_COACH, code = FoulCode.C, freeThrows = 1),
        )
        assertEquals(listOf("P", "P2"), s.a.fouls("a4").map { it.text })
        assertEquals("T1", s.a.fouls("a5").single().text)
        assertEquals("P2c", s.a.fouls("a6").single().text)
        assertEquals("C1", s.a.coachFouls.single().text)
        assertEquals(4, s.a.teamFoulsIn(1))
        assertTrue(s.inPenalty(Side.A))
        assertFalse(s.inPenalty(Side.B))
        // 4 個目で罰則を知らせる（直前の出来事はコーチのファウルなので、ここでは知らせない）
        assertTrue(s.alerts.isEmpty())
    }

    @Test
    fun teamFoulPenaltyAlertAndOvertimeCountsWithTheFourthQuarter() {
        val fouls = (1..4).map { Foul(4, ms(9 - it, 0), Side.B, playerId = "b${it + 4}", code = FoulCode.P) }
        val s = state(PeriodStart(4, ms(10, 0)), *fouls.toTypedArray())
        assertEquals(listOf(Alert.TeamFoulPenalty(Side.B, 4)), s.alerts)

        val ot = state(PeriodStart(4, ms(10, 0)), *fouls.toTypedArray(), PeriodEnd(4, 0), PeriodStart(5, ms(5, 0)),
            Foul(5, ms(4, 0), Side.B, playerId = "b4", code = FoulCode.P, freeThrows = 2))
        assertEquals(5, ot.b.teamFoulsIn(5))
        assertTrue(ot.inPenalty(Side.B))
    }

    @Test
    fun fifthFoulAndDisqualification() {
        val five = (1..5).map { Foul(1, ms(10 - it, 0), Side.A, playerId = "a4", code = FoulCode.P) }
        val s = state(PeriodStart(1, ms(10, 0)), *five.toTypedArray())
        assertEquals(listOf(Alert.FifthFoul(Side.A, "a4")), s.alerts.filterIsInstance<Alert.FifthFoul>())
        assertTrue(s.a.isOut("a4"))

        // T と U が 1 個ずつで失格・退場（GD）。コートから外れる
        val gd = state(
            PeriodStart(1, ms(10, 0)),
            Foul(1, ms(9, 0), Side.B, playerId = "b5", code = FoulCode.T, freeThrows = 1),
            Foul(1, ms(8, 0), Side.B, playerId = "b5", code = FoulCode.U, freeThrows = 2),
        )
        assertEquals(listOf(Alert.Disqualified(Side.B, "b5")), gd.alerts)
        assertTrue("b5" in gd.b.disqualified)
        assertFalse("b5" in gd.b.onCourt)

        // ヘッドコーチ: B 2 個と C 1 個で失格・退場
        val coach = state(
            PeriodStart(1, ms(10, 0)),
            Foul(1, ms(9, 0), Side.A, offender = Offender.HEAD_COACH, code = FoulCode.B, freeThrows = 1),
            Foul(1, ms(8, 0), Side.A, offender = Offender.HEAD_COACH, code = FoulCode.B, freeThrows = 1),
            Foul(1, ms(7, 0), Side.A, offender = Offender.HEAD_COACH, code = FoulCode.C, freeThrows = 1),
        )
        assertTrue(coach.a.coachDisqualified)
        assertEquals(listOf(Alert.Disqualified(Side.A, null)), coach.alerts)
    }

    @Test
    fun manToManPenaltyDisqualifiesTheCoachOnlyInU15() {
        val u15 = GameSetup(category = Category.U15, teamA = setup.teamA, teamB = setup.teamB)
        val two = arrayOf<GameEvent>(
            PeriodStart(1, ms(8, 0)),
            Foul(1, ms(7, 0), Side.A, offender = Offender.HEAD_COACH, code = FoulCode.M),
            Foul(1, ms(6, 0), Side.A, offender = Offender.HEAD_COACH, code = FoulCode.M),
        )
        assertTrue(state(*two, setup = u15).a.coachDisqualified)
        assertFalse(state(*two).a.coachDisqualified)
    }

    @Test
    fun timeoutsAreWrittenAsElapsedMinutes() {
        val s = state(
            PeriodStart(1, ms(10, 0)),
            Timeout(1, ms(4, 32), Side.A),
            PeriodEnd(1, 0),
            PeriodStart(2, ms(10, 0)),
            // 時計を使わないときは、欄に書く分を手で入れる
            Timeout(2, null, Side.A, minute = 8),
        )
        assertEquals(listOf(6, 8), s.a.timeouts.map { it.sheetMinute })
        assertEquals(0, s.timeoutsAvailable(Side.A, ms(1, 0)))
        assertEquals(2, s.timeoutsAvailable(Side.B, ms(1, 0)))
    }

    @Test
    fun timeoutOverTheLimitIsAlertedButStillRecorded() {
        val s = state(
            PeriodStart(1, ms(10, 0)),
            Timeout(1, ms(8, 0), Side.B),
            Timeout(1, ms(6, 0), Side.B),
            Timeout(1, ms(4, 0), Side.B),
        )
        assertEquals(listOf(Alert.TimeoutOverLimit(Side.B)), s.alerts)
        assertEquals(3, s.b.timeouts.size)
    }

    @Test
    fun lastTwoMinutesOfTheFourthQuarter() {
        val before = state(PeriodStart(4, ms(10, 0)), Shot(4, ms(2, 30), Side.A, "a4", ShotKind.TWO, made = true))
        assertFalse(before.reachedFourthQuarterLastTwoMinutes)
        assertFalse(before.firstSecondHalfBoxLost(Side.A))

        val s = state(PeriodStart(4, ms(10, 0)), Shot(4, ms(1, 58), Side.A, "a4", ShotKind.TWO, made = true))
        assertEquals(listOf(Alert.EnteredLastTwoMinutes), s.alerts)
        // 後半に 1 回もタイムアウトを取らずに L2M に入ったので、後半の最初の枠は使えない。取れるのは 2 回まで
        assertTrue(s.firstSecondHalfBoxLost(Side.A))
        assertEquals(2, s.timeoutsAvailable(Side.A, ms(1, 50)))

        val took = state(
            PeriodStart(3, ms(10, 0)), Timeout(3, ms(5, 0), Side.B), PeriodEnd(3, 0),
            PeriodStart(4, ms(10, 0)), Shot(4, ms(1, 58), Side.A, "a4", ShotKind.TWO, made = true),
        )
        assertFalse(took.firstSecondHalfBoxLost(Side.B))
    }

    @Test
    fun substitutionsTrackCourtAndFirstEntry() {
        val s = state(
            PeriodStart(1, ms(10, 0)),
            PeriodEnd(1, 0),
            PeriodStart(2, ms(10, 0)),
            // 両チームの交代を 1 件で
            Substitution(2, ms(7, 0), listOf(SubChange(Side.A, "a4", "a9"), SubChange(Side.B, "b5", "b10"))),
            Substitution(2, ms(5, 0), listOf(SubChange(Side.A, "a9", "a4"))),
        )
        assertEquals(listOf("a4", "a5", "a6", "a7", "a8"), s.a.onCourt)
        assertEquals(2, s.a.entered["a9"])
        assertEquals(1, s.a.entered["a4"])
        assertTrue("b10" in s.b.onCourt)
        assertTrue(s.warnings.isEmpty())
    }

    @Test
    fun recordsThatDoNotFitAreKeptWithWarnings() {
        val s = state(
            PeriodStart(1, ms(10, 0)),
            // ベンチの選手の得点、コートにいない選手との交代
            Shot(1, ms(9, 0), Side.A, "a10", ShotKind.TWO, made = true),
            Substitution(1, ms(8, 0), listOf(SubChange(Side.A, "a11", "a12"))),
        )
        assertEquals(2, s.a.score)
        assertEquals(listOf(Warning.PlayerNotOnCourt(Side.A, "a10")), s.warnings[1])
        assertEquals(listOf(Warning.BadSubstitution(SubChange(Side.A, "a11", "a12"))), s.warnings[2])
    }

    @Test
    fun possessionArrowFlipsAtJumpBallsAndEachNewPeriod() {
        val s1 = state(PeriodStart(1, ms(10, 0)), ArrowSet(1, ms(9, 59), toward = Side.B))
        assertEquals(Side.B, s1.arrow)
        val s2 = state(PeriodStart(1, ms(10, 0)), ArrowSet(1, ms(9, 59), toward = Side.B), JumpBallSituation(1, ms(5, 0)))
        assertEquals(Side.A, s2.arrow)
        val s3 = state(
            PeriodStart(1, ms(10, 0)), ArrowSet(1, ms(9, 59), toward = Side.B), PeriodEnd(1, 0), PeriodStart(2, ms(10, 0)),
        )
        assertEquals(Side.A, s3.arrow)
    }

    @Test
    fun gameEndDecidesTheWinner() {
        val s = state(
            PeriodStart(4, ms(10, 0)),
            Shot(4, ms(0, 3), Side.B, "b4", ShotKind.THREE, made = true),
            GameEnd(4, 0, endedAt = "15:12"),
        )
        assertTrue(s.finished)
        assertEquals(Side.B, s.winner())
        assertEquals("15:12", s.endedAt)
        assertEquals(listOf(PeriodEndMark(4, 0, 3)), s.periodEnds)
    }
}
