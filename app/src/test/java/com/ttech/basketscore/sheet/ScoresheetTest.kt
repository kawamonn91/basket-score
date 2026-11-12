package com.ttech.basketscore.sheet

import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.FoulCode
import com.ttech.basketscore.game.GameEnd
import com.ttech.basketscore.game.GameEngine
import com.ttech.basketscore.game.GameEvent
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.PeriodEnd
import com.ttech.basketscore.game.PeriodStart
import com.ttech.basketscore.game.RosterPlayer
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.Staff
import com.ttech.basketscore.game.SubChange
import com.ttech.basketscore.game.Substitution
import com.ttech.basketscore.game.TeamSetup
import com.ttech.basketscore.game.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoresheetTest {

    private fun team(p: String) = TeamSetup(
        name = "Team $p",
        benchLeft = p == "A",
        headCoach = Staff("コーチ $p", "123"),
        players = (4..10).map { RosterPlayer("$p$it", it.toString(), "選手$it", captain = it == 4, starter = it <= 8) },
    )

    private val setup = GameSetup(teamA = team("A"), teamB = team("B"))

    private fun sheet(vararg events: GameEvent) = Scoresheet.build(setup, GameEngine.replay(setup, events.toList()))

    @Test
    fun runningScoreCellsKeepColorsAndPeriodEnds() {
        val s = sheet(
            PeriodStart(1),
            Shot(1, null, Side.A, "A4", ShotKind.TWO, made = true),
            Shot(1, null, Side.A, "A5", ShotKind.THREE, made = true),
            Shot(1, null, Side.B, "B6", ShotKind.FT, made = true),
            PeriodEnd(1),
            PeriodStart(2),
            Shot(2, null, Side.A, "A4", ShotKind.FT, made = true),
        )
        assertEquals(setOf(2, 5, 6), s.runningA.keys)
        val two = s.runningA.getValue(2)
        assertEquals("4", two.number)
        assertEquals(Ink.RED, two.ink)
        assertFalse(two.periodEnd)
        assertEquals(1, two.eventIndex)
        // 第1Q の最後の得点に、太い○と線
        assertTrue(s.runningA.getValue(5).periodEnd)
        assertTrue(s.runningB.getValue(1).periodEnd)
        // 第2Q は濃色、まだ終わっていない
        assertEquals(Ink.DARK, s.runningA.getValue(6).ink)
        assertFalse(s.runningA.getValue(6).periodEnd)
        assertEquals(listOf(5 to 1, null, null, null), s.quarterScores)
        assertNull(s.finalScore)
        assertEquals(1, s.pages)
    }

    @Test
    fun playerRowsShowStartersEntryFoulsAndHalfLine() {
        val s = sheet(
            PeriodStart(1),
            Foul(1, null, Side.A, playerId = "A4", code = FoulCode.P),
            PeriodEnd(1),
            PeriodStart(2),
            Substitution(2, null, listOf(SubChange(Side.A, "A5", "A9"))),
            Foul(2, null, Side.A, playerId = "A4", code = FoulCode.P, freeThrows = 2),
            PeriodEnd(2),
            PeriodStart(3),
            Foul(3, null, Side.A, playerId = "A4", code = FoulCode.U, freeThrows = 2),
        )
        val p4 = s.a.players.first { it.number == "4" }
        assertEquals(PlayerIn(starter = true, ink = Ink.RED), p4.playerIn)
        assertTrue(p4.captain)
        assertEquals(listOf("P", "P2", "U2"), p4.fouls.map { it.text })
        assertEquals(listOf(Ink.RED, Ink.DARK, Ink.RED), p4.fouls.map { it.ink })
        // 前半に 2 つ使ったので、その後ろに太線
        assertEquals(2, p4.firstHalfFouls)
        val p9 = s.a.players.first { it.number == "9" }
        assertEquals(PlayerIn(starter = false, ink = Ink.DARK), p9.playerIn)
        assertNull(s.a.players.first { it.number == "10" }.playerIn)
        // チームファウル: 1Q 1、2Q 1、3Q 1
        assertEquals(listOf(1, 1, 1, 0), s.a.teamFouls)
        assertEquals("コーチ A", s.a.coach?.name)
    }

    @Test
    fun timeoutBoxesAndTheLostBox() {
        val s = sheet(
            PeriodStart(1), Timeout(1, null, Side.A, minute = 6), PeriodEnd(1),
            PeriodStart(4, 600_000), Shot(4, 100_000, Side.A, "A4", ShotKind.TWO, made = true),
        )
        assertEquals(listOf(TimeoutBox.Used("6", Ink.RED, 1), TimeoutBox.Empty), s.a.firstHalfTimeouts)
        // 後半に取らずに L2M に入ったので、後半の最初の枠は 2 本線
        assertEquals(listOf(TimeoutBox.Lost, TimeoutBox.Empty, TimeoutBox.Empty), s.a.secondHalfTimeouts)
        assertEquals(3, s.a.overtimeTimeouts.size)
    }

    @Test
    fun finalResult() {
        val s = sheet(
            PeriodStart(4),
            Shot(4, null, Side.B, "B4", ShotKind.THREE, made = true),
            PeriodEnd(4),
            GameEnd(4, null, "15:12"),
        )
        assertTrue(s.finished)
        assertEquals(0 to 3, s.finalScore)
        assertEquals("Team B", s.winner)
        assertEquals("15:12", s.endedAt)
        val last = s.runningB.getValue(3)
        assertTrue(last.gameEnd)
        assertTrue(last.periodEnd)
        // 第4Q から始めた試合なので、第1〜3Q は 0 点で終わった扱い
        assertEquals(listOf(0 to 0, 0 to 0, 0 to 0, 0 to 3), s.quarterScores)
    }

    @Test
    fun moreThan160PointsNeedsASecondPage() {
        val events = mutableListOf<GameEvent>(PeriodStart(1))
        repeat(81) { events += Shot(1, null, Side.A, "A4", ShotKind.TWO, made = true) }
        assertEquals(2, sheet(*events.toTypedArray()).pages)
    }
}
