package com.ttech.basketscore.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private val general = Category.GENERAL.defaultRules()
    private val u15 = Category.U15.defaultRules()

    private fun ms(min: Int, sec: Int) = (min * 60 + sec) * 1000L

    @Test
    fun categoryDefaults() {
        assertEquals(600, general.quarterSeconds)
        assertEquals(300, general.overtimeSeconds)
        assertFalse(general.manToManPenalty)
        assertEquals(480, u15.quarterSeconds)
        assertEquals(180, u15.overtimeSeconds)
        assertTrue(u15.manToManPenalty)
        assertEquals(18, general.maxPlayers)
        assertEquals(ms(5, 0), general.periodMillis(5))
    }

    @Test
    fun periodLabelsAndTeamFoulPeriod() {
        assertEquals("2Q", Period.label(2))
        assertEquals("OT1", Period.label(5))
        // OT のチームファウルは第4Q から続けて数える
        assertEquals(4, Period.teamFoulPeriod(6))
        assertEquals(3, Period.teamFoulPeriod(3))
    }

    @Test
    fun sheetMinuteMatchesTheManualExamples() {
        assertEquals(6, Timeouts.sheetMinute(ms(10, 0), ms(4, 32)))
        assertEquals(5, Timeouts.sheetMinute(ms(8, 0), ms(3, 21)))
        assertEquals(5, Timeouts.sheetMinute(ms(5, 0), ms(0, 23)))
        // 始まってすぐは 1、ちょうど 6 分経ったところは 6
        assertEquals(1, Timeouts.sheetMinute(ms(10, 0), ms(10, 0)))
        assertEquals(6, Timeouts.sheetMinute(ms(10, 0), ms(4, 0)))
    }

    @Test
    fun lastTwoMinutesIsOnlyInTheFourthQuarterAndOvertime() {
        assertTrue(LastTwoMinutes.contains(4, ms(2, 0)))
        assertFalse(LastTwoMinutes.contains(4, ms(2, 1)))
        assertFalse(LastTwoMinutes.contains(3, ms(1, 0)))
        assertTrue(LastTwoMinutes.contains(5, ms(0, 30)))
    }

    @Test
    fun timeoutsPerHalfAndOvertime() {
        assertEquals(2, Timeouts.available(general, 1, ms(5, 0), emptyList()))
        assertEquals(0, Timeouts.available(general, 2, ms(5, 0), listOf(TakenTimeout(1, ms(3, 0)), TakenTimeout(2, ms(8, 0)))))
        // 前半の残りは後半に持ち越さない
        assertEquals(3, Timeouts.available(general, 3, ms(9, 0), listOf(TakenTimeout(1, ms(3, 0)))))
        assertEquals(1, Timeouts.available(general, 5, ms(4, 0), emptyList()))
        assertEquals(0, Timeouts.available(general, 5, ms(1, 0), listOf(TakenTimeout(5, ms(3, 0)))))
        assertEquals(1, Timeouts.available(general, 6, ms(4, 0), listOf(TakenTimeout(5, ms(3, 0)))))
    }

    @Test
    fun lastTwoMinutesAllowsAtMostTwo() {
        // 後半に 1 回も取らずに L2M に入ると、取れるのは 2 回まで（後半の最初の枠が使えなくなる）
        assertEquals(2, Timeouts.available(general, 4, ms(1, 50), emptyList()))
        assertTrue(Timeouts.firstSecondHalfBoxLost(emptyList()))
        // L2M の前に 1 回取っていれば、L2M でも 2 回取れる（合計 3）
        val before = listOf(TakenTimeout(4, ms(5, 0)))
        assertEquals(2, Timeouts.available(general, 4, ms(1, 50), before))
        assertFalse(Timeouts.firstSecondHalfBoxLost(before))
        // L2M の前に 2 回取っていれば、L2M は残りの 1 回だけ
        assertEquals(1, Timeouts.available(general, 4, ms(1, 0), listOf(TakenTimeout(3, ms(5, 0)), TakenTimeout(4, ms(6, 0)))))
        // L2M で 2 回取ったら、後半の枠が残っていてももう取れない
        val inL2m = listOf(TakenTimeout(4, ms(1, 50)), TakenTimeout(4, ms(0, 40)))
        assertEquals(0, Timeouts.available(general, 4, ms(0, 20), inL2m))
    }

    @Test
    fun teamFoulPenaltyStartsAtTheLimit() {
        assertFalse(TeamFouls.inPenalty(general, 3))
        assertTrue(TeamFouls.inPenalty(general, 4))
        assertTrue(TeamFouls.inPenalty(general, 6))
    }
}
