package com.ttech.basketscore.stats

import com.ttech.basketscore.game.ArrowSet
import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.FoulCode
import com.ttech.basketscore.game.GameEngine
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.PeriodEnd
import com.ttech.basketscore.game.PeriodStart
import com.ttech.basketscore.game.PlayTypes
import com.ttech.basketscore.game.Rebound
import com.ttech.basketscore.game.RosterPlayer
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.SubChange
import com.ttech.basketscore.game.Substitution
import com.ttech.basketscore.game.TeamSetup
import com.ttech.basketscore.game.Timeout
import com.ttech.basketscore.game.Turnover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {

    private fun ms(min: Int, sec: Int) = (min * 60 + sec) * 1000L

    private fun team(prefix: String, benchLeft: Boolean) = TeamSetup(
        name = "Team $prefix",
        benchLeft = benchLeft,
        players = (4..12).map { n -> RosterPlayer(id = "$prefix$n", number = n.toString(), name = "", starter = n <= 8) },
    )

    private val setup = GameSetup(teamA = team("a", benchLeft = true), teamB = team("b", benchLeft = false))

    private fun shot(clock: Long, side: Side, id: String, kind: ShotKind, made: Boolean, x: Double? = null, y: Double? = null) =
        Shot(1, clock, side, id, kind, made, x?.toFloat(), y?.toFloat())

    @Test
    fun boxScoreHasPlusMinusStartersEffAndQuarters() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            Shot(1, ms(9, 0), Side.A, "a4", ShotKind.TWO, made = true, shotType = PlayTypes.DUNK),
            Substitution(1, ms(8, 0), listOf(SubChange(Side.A, "a5", "a9"))),
            Shot(1, ms(7, 0), Side.B, "b4", ShotKind.THREE, made = true),
            Foul(1, ms(6, 0), Side.A, playerId = "a9", code = FoulCode.T, freeThrows = 1),
            Foul(1, ms(5, 0), Side.B, playerId = "b5", code = FoulCode.P, offensive = true),
            PeriodEnd(1, 0),
            PeriodStart(2, ms(10, 0)),
            Shot(2, ms(9, 0), Side.A, "a9", ShotKind.TWO, made = false),
        )
        val state = GameEngine.replay(setup, events)
        val box = BoxScore.compute(setup, events, state, ms(8, 0))
        val a = box.getValue(Side.A)
        // a5 は 2 点のときだけコートにいた
        assertEquals(2, a.player("a5").plusMinus)
        assertEquals(-3, a.player("a9").plusMinus)
        assertEquals(-1, a.player("a4").plusMinus)
        assertEquals(1, a.player("a4").gs)
        assertEquals(0, a.player("a9").gs)
        assertEquals(1, a.player("a4").dk)
        assertEquals(1, a.player("a9").tf)
        assertTrue(a.player("a9").played)
        assertTrue(!a.player("a10").played)
        assertEquals(1, box.getValue(Side.B).player("b5").of)
        assertEquals(1, box.getValue(Side.B).player("b5").tov)
        // EFF: a4 は 2 点 → 2、a9 は 2P 失敗 → -1
        assertEquals(2, a.player("a4").eff)
        assertEquals(-1, a.player("a9").eff)
        // 出場時間: a4 は 1Q 10:00 + 2Q 2:00
        assertEquals(ms(12, 0), a.player("a4").minutes)
        // クォーターごと
        val q2 = BoxScore.compute(setup, events, state, ms(8, 0), setOf(2))
        assertEquals(0, q2.getValue(Side.A).player("a4").pts)
        assertEquals(0 to 1, q2.getValue(Side.A).player("a9").fgm to q2.getValue(Side.A).player("a9").fga)
        assertEquals(ms(2, 0), q2.getValue(Side.A).player("a4").minutes)
    }

    @Test
    fun runsAreConsecutivePointsWithoutAnswer() {
        val events = listOf(
            shot(ms(9, 0), Side.A, "a4", ShotKind.TWO, true),
            shot(ms(8, 0), Side.A, "a5", ShotKind.THREE, true),
            shot(ms(7, 0), Side.A, "a5", ShotKind.THREE, true),
            shot(ms(6, 30), Side.B, "b5", ShotKind.TWO, false),
            shot(ms(6, 0), Side.B, "b5", ShotKind.TWO, true),
            shot(ms(5, 0), Side.A, "a4", ShotKind.TWO, true),
        )
        val scores = Flow.scores(events)
        assertEquals(listOf(2, 5, 8, 8, 10), scores.map { it.scoreA })
        assertEquals(listOf(0, 0, 0, 2, 2), scores.map { it.scoreB })
        val runs = Flow.runs(scores)
        assertEquals(1, runs.size)
        assertEquals(Side.A, runs[0].side)
        assertEquals(8, runs[0].points)
        assertEquals(0 to 2, runs[0].startIndex to runs[0].endIndex)
        assertEquals(ms(5, 0) + ms(10, 0), Flow.elapsed(setup, 2, ms(5, 0)))
    }

    @Test
    fun lineupsCountTimeAndPoints() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            Shot(1, ms(9, 0), Side.A, "a4", ShotKind.TWO, made = true),
            Substitution(1, ms(6, 0), listOf(SubChange(Side.A, "a8", "a9"))),
            Shot(1, ms(5, 0), Side.B, "b4", ShotKind.THREE, made = true),
            PeriodEnd(1, 0),
        )
        val lineups = Lineups.compute(setup, events).filter { it.side == Side.A }
        assertEquals(2, lineups.size)
        val first = lineups.first { "a8" in it.players }
        assertEquals(ms(4, 0), first.millis)
        assertEquals(2, first.plusMinus)
        val second = lineups.first { "a9" in it.players }
        assertEquals(ms(6, 0), second.millis)
        assertEquals(0 to 3, second.pointsFor to second.pointsAgainst)
    }

    @Test
    fun segmentsSplitQuartersByTheClock() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            Shot(1, ms(7, 0), Side.A, "a4", ShotKind.TWO, made = true),
            Shot(1, ms(5, 0), Side.B, "b4", ShotKind.THREE, made = true), // 残り 5:00 ちょうどは後半
            Rebound(1, ms(3, 0), Side.A, "a5", offensive = false),
            PeriodEnd(1, 0),
        )
        val half = Segments.compute(setup, events)
        assertEquals(listOf("1Q 前半", "1Q 後半"), half.map { it.label })
        assertEquals(2 to 0, half[0].a.pts to half[0].b.pts)
        assertEquals(0 to 3, half[1].a.pts to half[1].b.pts)
        assertEquals(1, half[1].a.dr)
        val two = Segments.compute(setup, events, SegmentMode.TWO_MINUTES)
        assertEquals(5, two.size)
        assertEquals("1Q 2〜4 分", two[1].label)
        assertEquals(2, two[1].a.pts)
        assertEquals(3, two[2].b.pts)
    }

    @Test
    fun zonesSplitByDistanceAndAngleFromTheShootersView() {
        assertEquals(22, Zones.all.size)
        // ゴール下
        assertEquals("ゴール下", Zones.of(Court.UNDER_BASKET_X, 0.5, ShotKind.TWO).label)
        // トップの 3P
        assertEquals("3P 正面", Zones.of(1 - (1.575 + 7.0) / 28, 0.5, ShotKind.THREE).label)
        // y が小さい（奥のサイドライン）コーナーはシューターの左
        assertEquals("3P 左コーナー", Zones.of(1 - 1.575 / 28, 0.3 / 15, ShotKind.THREE).label)
        assertEquals("3P 右コーナー", Zones.of(1 - 1.575 / 28, 14.7 / 15, ShotKind.THREE).label)
        // 2P は 3P の帯に入れない
        assertEquals(3, Zones.of(1 - (1.575 + 7.0) / 28, 0.5, ShotKind.TWO).band)
        val lines = Zones.compute(
            listOf(
                Shot(1, null, Side.A, "a4", ShotKind.THREE, true, (1 - 8.575 / 28).toFloat(), 0.5f),
                Shot(1, null, Side.A, "a4", ShotKind.THREE, false, (1 - 8.575 / 28).toFloat(), 0.5f),
            ),
        )
        val top = lines.first { it.zone.label == "3P 正面" }
        assertEquals(0.5, top.percent!!, 1e-9)
        assertEquals(1.5, top.expected!!, 1e-9)
    }

    @Test
    fun expectedValueByCategory() {
        val shots = listOf(
            Shot(1, null, Side.A, "a4", ShotKind.TWO, true, Court.UNDER_BASKET_X.toFloat(), 0.5f),
            Shot(1, null, Side.A, "a4", ShotKind.TWO, false, (1 - 6.0 / 28).toFloat(), 0.1f),
            Shot(1, null, Side.A, "a4", ShotKind.FT, true),
            Shot(1, null, Side.A, "a4", ShotKind.FT, false),
            Shot(1, null, Side.A, "a4", ShotKind.THREE, true, 0.6f, 0.5f),
        )
        val by = Expected.byCategory(shots).associateBy { it.category }
        assertEquals(2.0, by.getValue(ShotCategory.PAINT).expected!!, 1e-9)
        assertEquals(0.0, by.getValue(ShotCategory.MID).expected!!, 1e-9)
        assertEquals(0.5, by.getValue(ShotCategory.FT).expected!!, 1e-9)
        assertEquals(3.0, by.getValue(ShotCategory.THREE).expected!!, 1e-9)
    }

    @Test
    fun playTypesGroupShotsAndTurnovers() {
        val events = listOf(
            Shot(1, null, Side.A, "a4", ShotKind.TWO, true, offenseType = "速攻", defenseType = "ゾーン"),
            Shot(1, null, Side.A, "a4", ShotKind.THREE, false, offenseType = "セット", defenseType = "ゾーン"),
            Turnover(1, null, Side.A, "a4", offenseType = "速攻"),
            Shot(1, null, Side.B, "b4", ShotKind.TWO, true, offenseType = "セット", defenseType = "マンツーマン"),
        )
        val offense = PlayTypeStats.offense(events, Side.A).associateBy { it.type }
        assertEquals(2, offense.getValue("速攻").plays)
        assertEquals(1.0, offense.getValue("速攻").ppp!!, 1e-9)
        assertEquals(0, offense.getValue("セット").points)
        // B のディフェンス（ゾーン）に対する A の攻撃
        val defenseB = PlayTypeStats.defense(events, Side.B).associateBy { it.type }
        assertEquals(2, defenseB.getValue("ゾーン").plays)
        assertEquals(2, defenseB.getValue("ゾーン").points)
    }

    @Test
    fun teamAdvancedUsesPossessionsSecondChanceAndTurnovers() {
        val events = listOf(
            PeriodStart(1, ms(10, 0)),
            ArrowSet(1, ms(10, 0), toward = Side.B), // A
            Shot(1, ms(9, 50), Side.A, "a4", ShotKind.TWO, made = false),
            Rebound(1, ms(9, 48), Side.A, "a5", offensive = true),
            Shot(1, ms(9, 40), Side.A, "a5", ShotKind.TWO, made = true), // セカンドチャンス 2 点、B へ
            Turnover(1, ms(9, 20), Side.B, "b4", stealById = "a6"), // A へ（TO から）
            Shot(1, ms(9, 10), Side.A, "a6", ShotKind.THREE, made = true), // TO からの得点 3、B へ
            Timeout(1, ms(9, 10), Side.B),
            PeriodEnd(1, 0),
        )
        val state = GameEngine.replay(setup, events)
        val box = BoxScore.compute(setup, events, state)
        val possessions = Possessions.compute(events)
        assertEquals(StartReason.TURNOVER, possessions[2].reason)
        val adv = Advanced.team(setup, Side.A, box.getValue(Side.A).total, box.getValue(Side.B).total, possessions, 1)
        assertEquals(2, adv.possessions)
        assertEquals(2, adv.secondChancePoints)
        assertEquals(3, adv.pointsOffTurnovers)
        assertEquals(2.5, adv.ppp!!, 1e-9)
        // eFG% = (2 + 0.5 × 1) ÷ 3
        assertEquals(2.5 / 3, adv.efg!!, 1e-9)
        assertEquals(1.0, adv.orbPercent!!, 1e-9)
        // 攻撃時間: 10:00〜9:40 の 20 秒と、9:20〜9:10 の 10 秒
        assertEquals(ms(0, 15), adv.averagePossessionMillis)
        // PACE: 2 回 ÷ 40 分 × 40
        assertEquals(2.0, adv.pace!!, 1e-9)
        val b = Advanced.team(setup, Side.B, box.getValue(Side.B).total, box.getValue(Side.A).total, possessions, 1)
        assertNull(b.efg)
        assertEquals(1.0, b.toRate!!, 1e-9)
    }

    @Test
    fun playerAdvancedFollowsTheFormulas() {
        val line = StatLine(pts = 20, fgm = 8, fga = 15, tpm = 2, tpa = 5, ftm = 2, fta = 4, or = 2, dr = 5, ast = 4, stl = 1, blk = 1, tov = 2, pf = 3, minutes = ms(20, 0))
        val team = StatLine(fgm = 30, fga = 70, fta = 20, tov = 12)
        val p = Advanced.player(setup, line, team, ms(200, 0))
        assertEquals(9.0 / 15, p.efg!!, 1e-9)
        assertEquals(20 / (2 * (15 + 0.44 * 4)), p.ts!!, 1e-9)
        // USAGE = (15 + 1.76 + 2) × 40 ÷ (20 × (70 + 8.8 + 12))
        assertEquals((18.76 * 40) / (20 * 90.8), p.usage!!, 1e-9)
        assertEquals(2.0, p.astPerTo!!, 1e-9)
        assertEquals(listOf(40.0, 30.0, 14.0, 4.0), p.per40)
        val gs = 20 + 0.4 * 8 - 0.7 * 15 - 0.4 * 2 + 0.7 * 2 + 0.3 * 5 + 1 + 0.7 * 4 + 0.7 * 1 - 0.4 * 3 - 2
        assertEquals(gs, p.gameScore, 1e-9)
    }
}
