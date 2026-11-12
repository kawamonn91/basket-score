package com.ttech.basketscore.stats

import com.ttech.basketscore.game.ArrowSet
import com.ttech.basketscore.game.Deflection
import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.FoulCode
import com.ttech.basketscore.game.GameEngine
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.JumpBallSituation
import com.ttech.basketscore.game.PeriodEnd
import com.ttech.basketscore.game.PeriodStart
import com.ttech.basketscore.game.PossessionChange
import com.ttech.basketscore.game.Rebound
import com.ttech.basketscore.game.RosterPlayer
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.TeamSetup
import com.ttech.basketscore.game.Turnover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {

    private fun ms(min: Int, sec: Int) = (min * 60 + sec) * 1000L

    private fun team(prefix: String, benchLeft: Boolean) = TeamSetup(
        name = "Team $prefix",
        benchLeft = benchLeft,
        players = (4..12).map { n -> RosterPlayer(id = "$prefix$n", number = n.toString(), name = "", starter = n <= 8) },
    )

    private val setup = GameSetup(teamA = team("a", benchLeft = true), teamB = team("b", benchLeft = false))

    // --- コート ---

    @Test
    fun threePointLineFollowsTheArcAndTheCorners() {
        // ゴール下・フリースローライン付近は 2P
        assertFalse(Court.isThree(Court.UNDER_BASKET_X, 0.5))
        assertFalse(Court.isThree(1 - (1.575 + 4.6) / 28, 0.5))
        // トップの 7 m は 3P、6.5 m は 2P
        assertTrue(Court.isThree(1 - (1.575 + 7.0) / 28, 0.5))
        assertFalse(Court.isThree(1 - (1.575 + 6.5) / 28, 0.5))
        // コーナー: サイドラインから 0.5 m は 3P、1.2 m は 2P（ゴールと同じ深さ）
        assertTrue(Court.isThree(1 - 1.575 / 28, 0.5 / 15))
        assertFalse(Court.isThree(1 - 1.575 / 28, 1.2 / 15))
        // バックコートは 3P
        assertEquals(ShotKind.THREE, Court.kindAt(0.3, 0.5))
        assertEquals(4.6, Court.distance(1 - (1.575 + 4.6) / 28, 0.5), 1e-9)
    }

    @Test
    fun leftAttackingShotsAreRotatedToKeepTheShootersSide() {
        assertEquals(0.9 to 0.2, Court.normalize(0.9, 0.2, attacksRight = true))
        val (x, y) = Court.normalize(0.1, 0.2, attacksRight = false)
        assertEquals(0.9, x, 1e-9)
        assertEquals(0.8, y, 1e-9)
    }

    @Test
    fun attackDirectionSwitchesAtHalfTime() {
        // A のベンチが左。前半は相手（右）のベンチ側を攻める
        assertTrue(Sides.attacksRight(setup, Side.A, 1))
        assertFalse(Sides.attacksRight(setup, Side.B, 2))
        assertFalse(Sides.attacksRight(setup, Side.A, 3))
        assertFalse(Sides.attacksRight(setup, Side.A, 5))
        // 自分のベンチ側を攻める設定
        val own = setup.copy(attackOpponentBenchFirstHalf = false)
        assertFalse(Sides.attacksRight(own, Side.A, 1))
        assertEquals(Side.B, Sides.attackerOfRightBasket(own, 1))
    }

    @Test
    fun panelsSwapOnlyWhenChosen() {
        assertEquals(Side.A, Sides.leftPanel(setup, 3))
        val swap = setup.copy(swapPanelsSecondHalf = true)
        assertEquals(Side.A, Sides.leftPanel(swap, 2))
        assertEquals(Side.B, Sides.leftPanel(swap, 3))
        // 入れ替えても、左のチームは右を攻める
        assertTrue(Sides.attacksRight(swap, Sides.leftPanel(swap, 1), 1))
        assertTrue(Sides.attacksRight(swap, Sides.leftPanel(swap, 3), 3))
    }

    // --- ポゼッション ---

    @Test
    fun possessionsChangeOnMadeShotsDefensiveReboundsAndTurnovers() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            ArrowSet(1, ms(10, 0), toward = Side.B), // A がジャンプボールを取った
            Shot(1, ms(9, 40), Side.A, "a4", ShotKind.TWO, made = false),
            Rebound(1, ms(9, 38), Side.A, "a5", offensive = true),
            Shot(1, ms(9, 30), Side.A, "a5", ShotKind.TWO, made = true), // A 0:30 で 2 点
            Shot(1, ms(9, 10), Side.B, "b4", ShotKind.THREE, made = false),
            Rebound(1, ms(9, 8), Side.A, "a6", offensive = false), // B 0:22
            Turnover(1, ms(8, 50), Side.A, "a6", stealById = "b5"), // A 0:18
            PeriodEnd(1, 0), // B 8:50
        )
        val p = Possessions.compute(events)
        assertEquals(listOf(Side.A, Side.B, Side.A, Side.B), p.map { it.side })
        assertEquals(listOf(ms(0, 30), ms(0, 22), ms(0, 18), ms(8, 50)), p.map { it.duration() })
        assertEquals(listOf(2, 0, 0, 0), p.map { it.points })
        assertEquals(2, Possessions.count(p, Side.A))
        assertNull(Possessions.current(events))
    }

    @Test
    fun freeThrowsEndThePossessionOnlyAfterTheLastMade() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            ArrowSet(1, ms(10, 0), toward = Side.A), // B が攻める
            Foul(1, ms(9, 0), Side.A, playerId = "a4", code = FoulCode.P, freeThrows = 2),
            Shot(1, ms(9, 0), Side.B, "b4", ShotKind.FT, made = true),
            Shot(1, ms(9, 0), Side.B, "b4", ShotKind.FT, made = true),
        )
        val p = Possessions.compute(events)
        assertEquals(listOf(Side.B, Side.A), p.map { it.side })
        assertEquals(2, p[0].points)
        assertEquals(Side.A, Possessions.current(events)?.side)
        assertEquals(ms(0, 30), Possessions.current(events)?.duration(ms(8, 30)))
    }

    @Test
    fun arrowDecidesQuarterStartsAndJumpBalls() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            ArrowSet(1, ms(10, 0), toward = Side.B), // A が攻める、アローは B
            JumpBallSituation(1, ms(5, 0)), // B へ、アローは A
            PeriodEnd(1, 0),
            PeriodStart(2, ms(10, 0)), // A へ
        )
        val p = Possessions.compute(events)
        assertEquals(listOf(Side.A, Side.B, Side.A), p.map { it.side })
    }

    @Test
    fun recordsOfTheOtherTeamCorrectThePossessionAndManualChangesWork() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            ArrowSet(1, ms(10, 0), toward = Side.B), // A
            Shot(1, ms(9, 0), Side.B, "b4", ShotKind.TWO, made = true), // B が攻めていたとみなす → A
            PossessionChange(1, ms(8, 0), Side.B),
        )
        val p = Possessions.compute(events)
        assertEquals(listOf(Side.A, Side.B, Side.A, Side.B), p.map { it.side })
    }

    // --- リバウンド ---

    @Test
    fun reboundIsOffensiveWhenTheSameTeamMissed() {
        val miss = Shot(1, ms(9, 0), Side.A, "a4", ShotKind.TWO, made = false)
        assertTrue(StatsRules.reboundIsOffensive(listOf(miss), Side.A))
        assertFalse(StatsRules.reboundIsOffensive(listOf(miss), Side.B))
        assertFalse(StatsRules.reboundIsOffensive(emptyList(), Side.A))
        assertEquals(miss, StatsRules.awaitingRebound(listOf(PeriodStart(1), miss)))
        assertNull(StatsRules.awaitingRebound(listOf(miss, Rebound(1, null, Side.B, "b4", false))))
        // 2 本のうち 1 本目のフリースローが外れても、リバウンドは聞かない
        val foul = Foul(1, ms(8, 0), Side.B, playerId = "b4", code = FoulCode.P, freeThrows = 2)
        val ft = Shot(1, ms(8, 0), Side.A, "a4", ShotKind.FT, made = false)
        assertNull(StatsRules.awaitingRebound(listOf(foul, ft)))
        assertEquals(ft, StatsRules.awaitingRebound(listOf(foul, ft, ft)))
    }

    // --- ボックススコア ---

    @Test
    fun boxScoreCountsEachStat() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            Shot(1, ms(9, 40), Side.A, "a4", ShotKind.THREE, made = true, assistId = "a5"),
            Shot(1, ms(9, 20), Side.B, "b4", ShotKind.TWO, made = false, blockId = "a6"),
            Rebound(1, ms(9, 18), Side.A, null, offensive = false),
            Shot(1, ms(9, 0), Side.A, "a4", ShotKind.TWO, made = false),
            Rebound(1, ms(8, 58), Side.A, "a4", offensive = true),
            Turnover(1, ms(8, 50), Side.A, "a4", type = "パスミス", stealById = "b5"),
            Foul(1, ms(8, 40), Side.B, playerId = "b4", code = FoulCode.P, freeThrows = 2),
            Shot(1, ms(8, 40), Side.A, "a5", ShotKind.FT, made = true),
            Shot(1, ms(8, 40), Side.A, "a5", ShotKind.FT, made = false),
            Foul(1, ms(8, 0), Side.A, playerId = "a6", code = FoulCode.P, offensive = true),
            Deflection(1, ms(7, 50), Side.B, "b6"),
        )
        val state = GameEngine.replay(setup, events)
        val box = BoxScore.compute(setup, events, state, ms(7, 0))
        val a4 = box.getValue(Side.A).player("a4")
        assertEquals(StatLine(pts = 3, fgm = 1, fga = 2, tpm = 1, tpa = 1, or = 1, tov = 1, minutes = ms(3, 0), gs = 1, plusMinus = 4, played = true), a4)
        val a5 = box.getValue(Side.A).player("a5")
        assertEquals(1, a5.pts)
        assertEquals(1 to 2, a5.ftm to a5.fta)
        assertEquals(1, a5.ast)
        val a6 = box.getValue(Side.A).player("a6")
        assertEquals(1, a6.blk)
        assertEquals(1, a6.pf)
        assertEquals(1, a6.tov)
        assertEquals(1, box.getValue(Side.A).team.dr)
        val totalA = box.getValue(Side.A).total
        assertEquals(4, totalA.pts)
        assertEquals(2, totalA.reb)
        assertEquals(2, totalA.tov)
        assertNull(totalA.minutes)
        val b = box.getValue(Side.B)
        assertEquals(1, b.player("b5").stl)
        assertEquals(1, b.player("b6").defl)
        assertEquals(1, b.player("b4").pf)
        assertEquals(0 to 1, b.player("b4").fgm to b.player("b4").fga)
        // 出場登録の全員が並ぶ
        assertEquals(9, b.players.size)
    }
}
