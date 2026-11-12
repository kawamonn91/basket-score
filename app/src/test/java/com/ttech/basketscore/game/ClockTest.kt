package com.ttech.basketscore.game

import com.ttech.basketscore.rules.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockTest {

    private fun ms(min: Int, sec: Int) = (min * 60 + sec) * 1000L

    private fun team(prefix: String) = TeamSetup(
        name = "Team $prefix",
        benchLeft = prefix == "a",
        players = (4..12).map { n -> RosterPlayer(id = "$prefix$n", number = n.toString(), name = "", starter = n <= 8) },
    )

    private val setup = GameSetup(teamA = team("a"), teamB = team("b"))
    private val rules = Category.GENERAL.defaultRules()

    // --- GameClock ---

    @Test
    fun clockCountsDownOnlyWhileRunning() {
        var now = 1_000L
        val clock = GameClock { now }
        clock.reset(1, ms(10, 0))
        assertEquals(ms(10, 0), clock.remaining())
        assertTrue(clock.start())
        now += 15_300
        assertEquals(ms(9, 44) + 700, clock.remaining())
        clock.stop()
        now += 60_000
        assertEquals(ms(9, 44) + 700, clock.remaining())
        assertFalse(clock.running)
    }

    @Test
    fun clockStopsAtZeroAndDoesNotStartFromZero() {
        var now = 0L
        val clock = GameClock { now }
        clock.reset(4, ms(10, 0), remaining = 1_500)
        clock.start()
        now += 2_000
        assertEquals(0, clock.remaining())
        assertTrue(clock.stopIfExpired())
        assertFalse(clock.running)
        assertFalse(clock.start())
    }

    @Test
    fun settingTheClockKeepsItWithinThePeriodAndKeepsRunning() {
        var now = 0L
        val clock = GameClock { now }
        clock.reset(1, ms(10, 0))
        clock.start()
        now += 5_000
        clock.set(ms(9, 58))
        now += 1_000
        assertEquals(ms(9, 57), clock.remaining())
        clock.set(ms(12, 0))
        assertEquals(ms(10, 0), clock.remaining())
        clock.set(-5)
        assertEquals(0, clock.remaining())
    }

    // --- ClockRules ---

    @Test
    fun stampSetsTheClockOnEveryKindOfEvent() {
        val events = listOf(
            PeriodStart(1), PeriodEnd(1), Shot(1, null, Side.A, "a4", ShotKind.TWO, true),
            Foul(1, side = Side.A, playerId = "a4", code = FoulCode.P), Timeout(1, null, Side.A),
            Substitution(1, null, emptyList()), ArrowSet(1, toward = Side.A), JumpBallSituation(1),
            Rebound(1, null, Side.A, null, false), Turnover(1, null, Side.A, null), Deflection(1, null, Side.A, "a4"),
            PossessionChange(1, null, Side.A), GameEnd(1, null, "15:00"),
        )
        events.forEach { assertEquals(it.toString(), 12_345L, ClockRules.stamp(it, 12_345).clockMillis) }
    }

    @Test
    fun timeoutStopsTheClock() {
        assertEquals(ClockRules.Action.STOP, ClockRules.autoAction(emptyList(), Timeout(1, ms(5, 0), Side.A)))
        assertNull(ClockRules.autoAction(emptyList(), Shot(1, ms(5, 0), Side.A, "a4", ShotKind.TWO, made = false)))
    }

    @Test
    fun missingTheLastFreeThrowStartsTheClock() {
        val foul = Foul(2, ms(3, 0), Side.B, playerId = "b5", code = FoulCode.P, freeThrows = 2)
        val first = Shot(2, ms(3, 0), Side.A, "a4", ShotKind.FT, made = false)
        // 1 本目が外れても動かさない
        assertNull(ClockRules.autoAction(listOf(foul), first))
        // 2 本目（最後）が外れたら動かす
        val second = first.copy()
        assertEquals(ClockRules.Action.START, ClockRules.autoAction(listOf(foul, first), second))
        // 最後が入ったときは動かさない（スローインで動かす）
        assertNull(ClockRules.autoAction(listOf(foul, first), second.copy(made = true)))
    }

    @Test
    fun freeThrowWithoutAFoulOrAfterACancelledFoulIsNotTheLast() {
        val shot = Shot(1, ms(3, 0), Side.A, "a4", ShotKind.FT, made = false)
        assertFalse(ClockRules.isLastFreeThrow(emptyList(), shot))
        val cancelled = Foul(1, ms(3, 0), Side.B, playerId = "b5", code = FoulCode.T, freeThrows = 1, cancelled = true)
        assertFalse(ClockRules.isLastFreeThrow(listOf(cancelled), shot))
        val otherPeriod = Foul(1, ms(0, 1), Side.B, playerId = "b5", code = FoulCode.P, freeThrows = 1)
        assertFalse(ClockRules.isLastFreeThrow(listOf(otherPeriod), shot.copy(period = 2)))
    }

    @Test
    fun anEndedPeriodCanBeResumedOnlyFromTheTail() {
        assertEquals(1, ClockRules.resumableTail(listOf(PeriodStart(1), PeriodEnd(1, 0))))
        assertEquals(2, ClockRules.resumableTail(listOf(PeriodStart(1), PeriodEnd(1, 0), PeriodStart(2))))
        assertNull(ClockRules.resumableTail(listOf(PeriodEnd(1, 0), PeriodStart(2), Timeout(2, null, Side.A))))
        assertNull(ClockRules.resumableTail(emptyList()))
    }

    @Test
    fun restoreUsesTheLastClockOfThePeriod() {
        val running = listOf(PeriodStart(1, ms(10, 0)), Shot(1, ms(7, 30), Side.A, "a4", ShotKind.TWO, true), ArrowSet(1, null, Side.A))
        assertEquals(ms(7, 30), ClockRules.restore(rules, GameEngine.replay(setup, running), running))
        val fresh = listOf(PeriodStart(1, ms(10, 0)), PeriodEnd(1, 0), PeriodStart(2))
        assertEquals(ms(10, 0), ClockRules.restore(rules, GameEngine.replay(setup, fresh), fresh))
        val ended = listOf(PeriodStart(1, ms(10, 0)), PeriodEnd(1, 1_200))
        assertEquals(1_200, ClockRules.restore(rules, GameEngine.replay(setup, ended), ended))
    }

    @Test
    fun lastTwoMinutesIsCrossedOnlyInTheFourthQuarterAndOvertime() {
        assertTrue(ClockRules.crossedLastTwoMinutes(4, ms(2, 0) + 50, ms(1, 59)))
        assertTrue(ClockRules.crossedLastTwoMinutes(5, ms(2, 1), ms(2, 0)))
        assertFalse(ClockRules.crossedLastTwoMinutes(3, ms(2, 1), ms(1, 59)))
        assertFalse(ClockRules.crossedLastTwoMinutes(4, ms(1, 59), ms(1, 58)))
    }

    // --- ClockFormat ---

    @Test
    fun formatsShowTenthsInTheLastMinute() {
        assertEquals("10:00", ClockFormat.board(ms(10, 0)))
        assertEquals("7:43", ClockFormat.board(ms(7, 43) + 900))
        assertEquals("1:00", ClockFormat.board(ms(1, 0)))
        assertEquals("59.9", ClockFormat.board(59_950))
        assertEquals("0.0", ClockFormat.board(0))
        assertEquals("0:05.3", ClockFormat.stamp(5_350))
        assertEquals("2Q 7:43", ClockFormat.at(2, ms(7, 43)))
        assertEquals("OT1", ClockFormat.at(5, null))
    }

    // --- 出場時間（F-504） ---

    @Test
    fun playingTimeFollowsSubstitutionsAndPeriods() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            Substitution(1, ms(6, 30), listOf(SubChange(Side.A, "a4", "a9"))),
            PeriodEnd(1, 0),
            PeriodStart(2, ms(10, 0)),
            Substitution(2, ms(8, 0), listOf(SubChange(Side.A, "a9", "a4"))),
        )
        val s = GameEngine.replay(setup, events)
        // a4: 1Q 3:30、2Q は 8:00 から（今 5:00 なら 3:00）
        assertEquals(ms(3, 30), s.a.playingTime("a4", null))
        assertEquals(ms(6, 30), s.a.playingTime("a4", ms(5, 0)))
        // a9: 1Q 6:30 + 2Q 2:00
        assertEquals(ms(8, 30), s.a.playingTime("a9", ms(5, 0)))
        // a5: 1Q 10:00 + 2Q の今まで 5:00
        assertEquals(ms(15, 0), s.a.playingTime("a5", ms(5, 0)))
        assertEquals(0, s.a.playingTime("a10", ms(5, 0)))
    }

    @Test
    fun disqualificationEndsPlayingTime() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            Foul(1, ms(4, 0), Side.B, playerId = "b5", code = FoulCode.D),
        )
        val s = GameEngine.replay(setup, events)
        assertEquals(ms(6, 0), s.b.playingTime("b5", ms(1, 0)))
    }

    @Test
    fun noPlayingTimeWithoutTheClock() {
        val events = listOf(PeriodStart(1), PeriodEnd(1))
        val s = GameEngine.replay(setup.copy(useClock = false), events)
        assertEquals(0, s.a.playingTime("a4", null))
    }
}
