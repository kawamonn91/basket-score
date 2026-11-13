package com.ttech.basketscore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class FoldersTest {

    private val utc = ZoneOffset.UTC
    private val day = 24 * 60 * 60 * 1000L

    /** 1970-01-01 から [days] 日目の 0 時（UTC） */
    private fun at(days: Long) = days * day

    private fun game(id: Long, date: String, folderId: Long?) =
        GameEntity(id, GameStatus.FINISHED, "{}", 0, 0, date, "", "A", "B", folderId = folderId)

    @Test
    fun gamesAreFilteredByView() {
        val games = listOf(game(1, "2026-11-01", 10), game(2, "2026-11-02", null), game(3, "2026-11-03", 20))
        assertEquals(listOf(1L, 2L, 3L), Folders.gamesIn(games, Folders.ALL).map { it.id })
        assertEquals(listOf(2L), Folders.gamesIn(games, Folders.UNFILED).map { it.id })
        assertEquals(listOf(3L), Folders.gamesIn(games, 20).map { it.id })
        assertEquals(emptyList<Long>(), Folders.gamesIn(games, 99).map { it.id })
    }

    @Test
    fun summariesCountGamesAndSortByLatestDate() {
        // 2026-11-13 は 1970-01-01 から 20770 日目
        val folders = listOf(
            FolderEntity(1, "夏の大会", createdAt = at(20000)),
            FolderEntity(2, "秋季リーグ", createdAt = at(20100)),
            // 試合がまだ無い、今日作った大会
            FolderEntity(3, "新人戦", createdAt = at(20770)),
        )
        val games = listOf(
            game(1, "2026-07-21", 1),
            game(2, "2026-07-20", 1),
            game(3, "2026-10-04", 2),
            game(4, "", 2),
            game(5, "2026-11-01", null),
        )
        val list = Folders.summaries(folders, games, utc)
        assertEquals(listOf(3L, 2L, 1L), list.map { it.folder.id })
        val summer = list.first { it.folder.id == 1L }
        assertEquals(2, summer.games)
        assertEquals("2026-07-20", summer.firstDate)
        assertEquals("2026-07-21", summer.lastDate)
        assertEquals("2026-07-20〜07-21", summer.period)
        // 日付の空の試合も数に入れるが、期間には使わない
        val autumn = list.first { it.folder.id == 2L }
        assertEquals(2, autumn.games)
        assertEquals("2026-10-04", autumn.period)
        val rookie = list.first { it.folder.id == 3L }
        assertEquals(0, rookie.games)
        assertNull(rookie.period)
    }

    @Test
    fun foldersOfTheSameDateAreNewestFirst() {
        val folders = listOf(FolderEntity(1, "A", createdAt = at(20770)), FolderEntity(2, "B", createdAt = at(20770) + 1000))
        assertEquals(listOf(2L, 1L), Folders.summaries(folders, emptyList(), utc).map { it.folder.id })
    }

    @Test
    fun periodIsShortWithinAYear() {
        assertEquals("2026-11-01", Folders.period("2026-11-01", "2026-11-01"))
        assertEquals("2026-11-01〜11-03", Folders.period("2026-11-01", "2026-11-03"))
        assertEquals("2026-12-27〜2027-01-05", Folders.period("2026-12-27", "2027-01-05"))
        // 決まった書き方でない日付は、そのまま並べる
        assertEquals("11/1〜11/3", Folders.period("11/1", "11/3"))
        assertNull(Folders.period(null, null))
    }

    @Test
    fun namesMustBeFilledAndUnique() {
        val folders = listOf(FolderEntity(1, "秋季リーグ", createdAt = 0))
        assertEquals("大会の名前を入れてください", Folders.nameProblem("  ", folders))
        assertEquals("同じ名前の大会があります", Folders.nameProblem(" 秋季リーグ ", folders))
        assertNull(Folders.nameProblem("新人戦", folders))
        // 名前を変えるときは、自分自身とは比べない
        assertNull(Folders.nameProblem("秋季リーグ", folders, exceptId = 1))
    }

    @Test
    fun titlesOfFixedViews() {
        val folders = listOf(FolderEntity(5, "新人戦", createdAt = 0))
        assertEquals("すべての試合", Folders.title(Folders.ALL, folders))
        assertEquals("未分類", Folders.title(Folders.UNFILED, folders))
        assertEquals("新人戦", Folders.title(5, folders))
    }
}
