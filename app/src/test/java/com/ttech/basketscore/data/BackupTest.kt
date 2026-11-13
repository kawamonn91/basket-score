package com.ttech.basketscore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val day = BackupPrefs.DAY

    @Test
    fun backupRoundTripsAllTables() {
        val teams = listOf(TeamEntity(1, "Stars", "ST", 0xFFD64545, "監督", "123", "", "", createdAt = 10))
        val players = listOf(PlayerEntity(5, 1, "00", "Ichiro", practiceNumber = "7", defaultStarter = true, licenseNo = "045", captain = true, sortOrder = 2))
        val games = listOf(GameEntity(3, GameStatus.LOCKED, "{\"x\":1}", 20, 30, "2026-11-13", "大会", "Stars", "Moons", 70, 65))
        val events = listOf(GameEventEntity(9, 3, 1000, 1, 600_000, "{\"event\":\"period_start\",\"period\":1}", 21), GameEventEntity(10, 3, 2000, 1, null, "{}", 22))
        val history = listOf(EditHistoryEntity(4, 3, 25, "追加", "出来事", null, "{}"), EditHistoryEntity(6, null, 26, "変更", "チーム", "a", "b"))
        val text = Backup.encode(Backup.from(teams, players, games, events, history, now = 99))
        val file = Backup.decode(text)
        assertEquals(99, file.createdAt)
        assertEquals(teams, Backup.teams(file))
        assertEquals(players, Backup.players(file))
        assertEquals(games, Backup.games(file))
        assertEquals(events, Backup.events(file))
        assertEquals(history, Backup.history(file))
    }

    @Test
    fun backupKeepsFoldersAndTheGamesInThem() {
        val folders = listOf(FolderEntity(7, "秋季リーグ", createdAt = 40))
        val games = listOf(
            GameEntity(3, GameStatus.FINISHED, "{}", 20, 30, "2026-11-13", "秋季リーグ", "Stars", "Moons", 70, 65, folderId = 7),
            GameEntity(4, GameStatus.READY, "{}", 21, 31, "2026-11-14", "", "Stars", "Suns", folderId = null),
        )
        val file = Backup.decode(Backup.encode(Backup.from(emptyList(), emptyList(), games, emptyList(), emptyList(), now = 99, folders = folders)))
        assertEquals(BackupFile.FORMAT, file.format)
        assertEquals(folders, Backup.folders(file))
        assertEquals(games, Backup.games(file))
    }

    @Test
    fun formerBackupsAreReadWithAllGamesUnfiled() {
        // 形式 1（大会のフォルダが無かったころ）のファイル
        val old = "{\"app\":\"basket-score\",\"format\":1,\"createdAt\":1,\"teams\":[],\"players\":[],\"events\":[]," +
            "\"games\":[{\"id\":1,\"status\":\"FINISHED\",\"setupJson\":\"{}\",\"createdAt\":1,\"updatedAt\":1,\"date\":\"2026-11-12\",\"competition\":\"\",\"teamAName\":\"A\",\"teamBName\":\"B\"}]}"
        val file = Backup.decode(old)
        assertEquals(emptyList<FolderEntity>(), Backup.folders(file))
        assertNull(Backup.games(file).single().folderId)
    }

    @Test
    fun gamesPointingToMissingFoldersBecomeUnfiled() {
        val games = listOf(GameEntity(3, GameStatus.FINISHED, "{}", 20, 30, "2026-11-13", "", "A", "B", folderId = 99))
        val file = Backup.decode(Backup.encode(Backup.from(emptyList(), emptyList(), games, emptyList(), emptyList(), now = 1)))
        assertNull(Backup.games(file).single().folderId)
    }

    @Test
    fun otherFilesAreRejectedWithAReason() {
        val notJson = runCatching { Backup.decode("hello") }
        assertTrue(notJson.isFailure)
        assertEquals("Basket Score のバックアップのファイルではありません", notJson.exceptionOrNull()?.message)
        val otherApp = runCatching { Backup.decode("{\"app\":\"other\",\"createdAt\":1,\"teams\":[],\"players\":[],\"games\":[],\"events\":[]}") }
        assertTrue(otherApp.isFailure)
        val newer = runCatching { Backup.decode("{\"app\":\"basket-score\",\"format\":99,\"createdAt\":1,\"teams\":[],\"players\":[],\"games\":[],\"events\":[]}") }
        assertTrue(newer.exceptionOrNull()?.message!!.contains("新しい版"))
        val badStatus = runCatching {
            Backup.decode(
                "{\"app\":\"basket-score\",\"createdAt\":1,\"teams\":[],\"players\":[],\"events\":[]," +
                    "\"games\":[{\"id\":1,\"status\":\"WHAT\",\"setupJson\":\"\",\"createdAt\":1,\"updatedAt\":1,\"date\":\"\",\"competition\":\"\",\"teamAName\":\"\",\"teamBName\":\"\"}]}",
            )
        }
        assertTrue(badStatus.isFailure)
    }

    @Test
    fun promptsAfterTwoWeeksAndRespectsSnooze() {
        val now = 100 * day
        // 試合が無ければ促さない
        assertFalse(BackupPrefs.shouldPrompt(now, null, 0, null))
        // 一度もしていない: いちばん古い試合から 14 日
        assertFalse(BackupPrefs.shouldPrompt(now, null, 0, now - 13 * day))
        assertTrue(BackupPrefs.shouldPrompt(now, null, 0, now - 14 * day))
        // 前回から 14 日
        assertFalse(BackupPrefs.shouldPrompt(now, now - 10 * day, 0, now - 60 * day))
        assertTrue(BackupPrefs.shouldPrompt(now, now - 15 * day, 0, now - 60 * day))
        // 「あとで」の間は促さない
        assertFalse(BackupPrefs.shouldPrompt(now, now - 15 * day, now + day, now - 60 * day))
        assertEquals(15, BackupPrefs.daysSince(now, now - 15 * day))
    }
}
