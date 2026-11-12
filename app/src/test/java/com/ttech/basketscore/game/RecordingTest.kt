package com.ttech.basketscore.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingTest {

    private fun team(prefix: String) = TeamSetup(
        name = "Team ${prefix.uppercase()}",
        shortName = prefix.uppercase(),
        benchLeft = prefix == "a",
        players = (4..8).map { RosterPlayer(id = "${prefix.uppercase()}${it - 3}", number = it.toString(), name = "", starter = true) },
    )

    private val setup = GameSetup(teamA = team("a"), teamB = team("b"))

    @Test
    fun startGameSetsTheArrowTowardTheOtherTeam() {
        val events = Recording.startGame(firstPossession = Side.A)
        val state = GameEngine.replay(setup, events)
        assertEquals(1, state.period)
        assertTrue(state.periodRunning)
        assertEquals(Side.B, state.arrow)
    }

    @Test
    fun nextPeriodAndOvertime() {
        val running = GameEngine.replay(setup, listOf(PeriodStart(1)))
        assertFalse(Recording.canStartNextPeriod(running))
        val ended = GameEngine.replay(setup, listOf(PeriodStart(1), PeriodEnd(1)))
        assertTrue(Recording.canStartNextPeriod(ended))
        assertEquals(PeriodStart(2), Recording.nextPeriod(ended))

        val tied = GameEngine.replay(setup, listOf(PeriodStart(4), PeriodEnd(4)))
        assertTrue(Recording.needsOvertime(tied))
        val notTied = GameEngine.replay(setup, listOf(PeriodStart(4), Shot(4, null, Side.A, "A1", ShotKind.TWO, made = true), PeriodEnd(4)))
        assertFalse(Recording.needsOvertime(notTied))
    }

    @Test
    fun addPlayerDuringTheGame() {
        val added = Recording.addPlayer(setup, Side.A, "55") as Recording.AddPlayerResult.Added
        assertEquals("A6", added.player.id)
        assertEquals(listOf("4", "5", "6", "7", "8", "55"), added.setup.teamA.players.map { it.number })
        assertEquals(setup.teamB, added.setup.teamB)

        assertEquals(Recording.AddPlayerResult.Rejected("#4 はもういます"), Recording.addPlayer(setup, Side.A, "4"))
        assertEquals(Recording.AddPlayerResult.Rejected("背番号は 0〜99 か 00 にしてください"), Recording.addPlayer(setup, Side.A, "100"))
        // 0 と 00 は別の番号
        val zero = Recording.addPlayer(setup, Side.B, "0") as Recording.AddPlayerResult.Added
        assertTrue(Recording.addPlayer(zero.setup, Side.B, "00") is Recording.AddPlayerResult.Added)
    }

    @Test
    fun recentEventsOfOneTeam() {
        val events = listOf(
            PeriodStart(1),
            Shot(1, null, Side.A, "A1", ShotKind.TWO, made = true),
            Shot(1, null, Side.B, "B1", ShotKind.TWO, made = true),
            Foul(1, null, Side.A, playerId = "A2", code = FoulCode.P),
            Substitution(1, null, listOf(SubChange(Side.A, "A1", "A9"), SubChange(Side.B, "B2", "B9"))),
            Timeout(1, null, Side.A, minute = 5),
        ).withIndex().toList()
        assertEquals(listOf(5, 4, 3), Recording.recent(events, Side.A).map { it.index })
        assertEquals(listOf(4, 2), Recording.recent(events, Side.B).map { it.index })
    }

    @Test
    fun unknownPlayerIsNotAWarningAndShowsAsQuestionMark() {
        val state = GameEngine.replay(
            setup,
            listOf(PeriodStart(1), Shot(1, null, Side.A, RosterPlayer.UNKNOWN, ShotKind.THREE, made = true), Foul(1, null, Side.B, playerId = RosterPlayer.UNKNOWN, code = FoulCode.P)),
        )
        assertTrue(state.warnings.isEmpty())
        assertEquals("?", state.runningScore.single().playerNumber)
        assertEquals(1, state.b.teamFoulsIn(1))
        assertEquals("A #? 3P 成功", EventText.describe(setup, Shot(1, null, Side.A, RosterPlayer.UNKNOWN, ShotKind.THREE, made = true)))
        assertTrue(EventText.hasUnknownPlayer(Foul(1, null, Side.B, playerId = RosterPlayer.UNKNOWN, code = FoulCode.P)))
    }

    @Test
    fun describeEvents() {
        assertEquals("A #4 2P 成功（AST #5）", EventText.describe(setup, Shot(1, null, Side.A, "A1", ShotKind.TWO, made = true, assistId = "A2")))
        assertEquals("B #6 ファウル P2", EventText.describe(setup, Foul(1, null, Side.B, playerId = "B3", code = FoulCode.P, freeThrows = 2)))
        assertEquals("A コーチ ファウル C1", EventText.describe(setup, Foul(1, null, Side.A, offender = Offender.HEAD_COACH, code = FoulCode.C, freeThrows = 1)))
        assertEquals("B #4 ファウル P（OF）", EventText.describe(setup, Foul(1, null, Side.B, playerId = "B1", code = FoulCode.P, offensive = true)))
        assertEquals("A タイムアウト（6 分）", EventText.describe(setup, Timeout(1, null, Side.A, minute = 6)))
        assertEquals("交代 A #4→#8", EventText.describe(setup, Substitution(1, null, listOf(SubChange(Side.A, "A1", "A5")))))
        assertEquals("2Q 開始", EventText.describe(setup, PeriodStart(2)))
        assertEquals("OT1 終了", EventText.describe(setup, PeriodEnd(5)))
    }

    @Test
    fun fightingFoulDisqualifies() {
        val state = GameEngine.replay(setup, listOf(PeriodStart(1), Foul(1, null, Side.A, playerId = "A1", code = FoulCode.F)))
        assertTrue("A1" in state.a.disqualified)
    }
}
