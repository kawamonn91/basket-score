package com.ttech.basketscore.game

import com.ttech.basketscore.rules.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterTest {

    private val rules = Category.GENERAL.defaultRules()

    @Test
    fun jerseyNumbers() {
        listOf("0", "00", "1", "9", "10", "99").forEach { assertTrue(it, Jersey.isValid(it)) }
        listOf("", "000", "01", "100", "-1", "a", " 4").forEach { assertFalse(it, Jersey.isValid(it)) }
        assertEquals(listOf("0", "00", "4", "12", "99"), listOf("12", "99", "00", "4", "0").sortedBy(Jersey::sortKey))
    }

    @Test
    fun teamProblems() {
        assertTrue(RosterRules.teamProblems(listOf("4", "5", "00"), captains = 1).isEmpty())
        assertEquals(
            listOf("背番号は 0〜99 か 00 にしてください（100）", "背番号が重なっています（4）", "キャプテンは 1 人にしてください"),
            RosterRules.teamProblems(listOf("4", "4", "100"), captains = 2),
        )
    }

    private fun player(n: Int, starter: Boolean = false) = RosterPlayer("p$n", n.toString(), "選手$n", starter = starter)

    @Test
    fun entryNeedsFiveStartersAndAtMostEighteenPlayers() {
        val ok = (4..15).map { player(it, starter = it <= 8) }
        assertTrue(RosterRules.entryProblems(ok, rules).isEmpty())

        val fourStarters = (4..15).map { player(it, starter = it <= 7) }
        assertEquals(listOf("スターターを 5 人選んでください（今は 4 人）"), RosterRules.entryProblems(fourStarters, rules))

        val tooMany = (0..18).map { player(it, starter = it in 1..5) }
        assertEquals(listOf("出場登録は 18 人までです（今は 19 人）"), RosterRules.entryProblems(tooMany, rules))
    }
}
