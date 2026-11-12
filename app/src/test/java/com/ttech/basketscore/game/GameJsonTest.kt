package com.ttech.basketscore.game

import com.ttech.basketscore.rules.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameJsonTest {

    @Test
    fun everyEventTypeSurvivesTheRoundTrip() {
        val events = listOf(
            PeriodStart(1, 600_000),
            Shot(1, 590_000, Side.A, "a4", ShotKind.THREE, made = true, x = 0.2f, y = 0.8f, shotType = "ジャンプシュート", assistId = "a5"),
            Shot(1, 580_000, Side.B, "b4", ShotKind.TWO, made = false, blockId = "a6"),
            Foul(1, 570_000, Side.A, playerId = "a4", code = FoulCode.U, freeThrows = 2, drawnById = "b4"),
            Foul(1, 560_000, Side.B, offender = Offender.HEAD_COACH, code = FoulCode.C, freeThrows = 1, cancelled = true),
            Timeout(1, 550_000, Side.A),
            Timeout(2, null, Side.B, minute = 7),
            Substitution(1, 540_000, listOf(SubChange(Side.A, "a4", "a9"), SubChange(Side.B, "b5", "b10"))),
            ArrowSet(1, 599_000, Side.B),
            JumpBallSituation(1, 300_000),
            Rebound(1, 579_000, Side.A, null, offensive = false),
            Turnover(1, 500_000, Side.B, "b6", type = "パスミス", stealById = "a7"),
            Deflection(1, 499_000, Side.A, "a7"),
            PossessionChange(1, 498_000, Side.A),
            PeriodEnd(1, 0),
            GameEnd(4, 0, "15:12"),
        )
        for (event in events) {
            val text = GameJson.encodeEvent(event)
            assertEquals(text, event, GameJson.decodeEvent(text))
        }
        // 型の名前は JSON の "event" に入る（保存した記録を読めるように、名前は変えない）
        assertTrue(GameJson.encodeEvent(events[1]).contains("\"event\":\"shot\""))
    }

    @Test
    fun setupSurvivesTheRoundTrip() {
        val setup = GameSetup(
            header = GameHeader(competition = "新人戦", date = "2026-11-12", startTime = "13:30", gameNo = "3"),
            category = Category.U15,
            detail = DetailLevel.STATS,
            teamA = TeamSetup(name = "東西", benchLeft = true, players = listOf(RosterPlayer("a4", "4", "北海 一郎", captain = true, starter = true))),
            teamB = TeamSetup(name = "南北", benchLeft = false, players = listOf(RosterPlayer("b00", "00", "青森 二郎", licenseNo = "123"))),
        )
        val decoded = GameJson.decodeSetup(GameJson.encodeSetup(setup))
        assertEquals(setup, decoded)
        assertEquals(480, decoded.rules.quarterSeconds)
    }
}
