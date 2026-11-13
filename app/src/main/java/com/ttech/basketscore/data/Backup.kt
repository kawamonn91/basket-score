package com.ttech.basketscore.data

import android.content.Context
import androidx.core.content.edit
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * すべてのデータのバックアップ（F-1004）。チーム・選手・大会のフォルダ・試合・出来事・更新履歴を 1 つの JSON ファイルにする。
 * 復元は、今のデータをすべて置き換える。
 * 形式 2 で大会のフォルダ（[folders] と [BackupGame.folderId]）を足した。形式 1 のファイルは、全試合を未分類として読む
 */
@Serializable
data class BackupFile(
    val app: String = APP,
    val format: Int = FORMAT,
    val createdAt: Long,
    val teams: List<BackupTeam>,
    val players: List<BackupPlayer>,
    val games: List<BackupGame>,
    val events: List<BackupEvent>,
    val history: List<BackupHistory> = emptyList(),
    val folders: List<BackupFolder> = emptyList(),
) {
    companion object {
        const val APP = "basket-score"
        const val FORMAT = 2
    }
}

@Serializable
data class BackupFolder(
    val id: Long,
    val name: String,
    val createdAt: Long,
)

@Serializable
data class BackupTeam(
    val id: Long,
    val name: String,
    val shortName: String = "",
    val color: Long? = null,
    val headCoachName: String = "",
    val headCoachLicense: String = "",
    val assistantCoachName: String = "",
    val assistantCoachLicense: String = "",
    val createdAt: Long,
)

@Serializable
data class BackupPlayer(
    val id: Long,
    val teamId: Long,
    val number: String,
    val name: String,
    val practiceNumber: String = "",
    val defaultStarter: Boolean = false,
    val licenseNo: String = "",
    val captain: Boolean = false,
    val position: String = "",
    val sortOrder: Int = 0,
)

@Serializable
data class BackupGame(
    val id: Long,
    val status: String,
    val setupJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val date: String,
    val competition: String,
    val teamAName: String,
    val teamBName: String,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    val folderId: Long? = null,
)

@Serializable
data class BackupEvent(
    val id: Long,
    val gameId: Long,
    val seq: Long,
    val period: Int,
    val clockMillis: Long? = null,
    val json: String,
    val createdAt: Long,
)

@Serializable
data class BackupHistory(
    val id: Long,
    val gameId: Long? = null,
    val at: Long,
    val action: String,
    val target: String,
    val before: String? = null,
    val after: String? = null,
)

/** バックアップのファイルの読み書き（純粋な変換。単体テストで確かめる） */
object Backup {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** 読み込む。このアプリのバックアップでなければ、理由を付けて失敗する */
    fun decode(text: String): BackupFile {
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("Basket Score のバックアップのファイルではありません") }
        require(file.app == BackupFile.APP) { "Basket Score のバックアップのファイルではありません" }
        require(file.format <= BackupFile.FORMAT) { "新しい版のアプリで作ったバックアップです。アプリを更新してください" }
        // 試合の状態の名前が分からないものは読まない（Room に入れると落ちる）
        file.games.forEach { g -> require(GameStatus.entries.any { it.name == g.status }) { "試合の状態が読めません（${g.status}）" } }
        return file
    }

    fun from(
        teams: List<TeamEntity>,
        players: List<PlayerEntity>,
        games: List<GameEntity>,
        events: List<GameEventEntity>,
        history: List<EditHistoryEntity>,
        now: Long,
        folders: List<FolderEntity> = emptyList(),
    ) =
        BackupFile(
            createdAt = now,
            folders = folders.map { BackupFolder(it.id, it.name, it.createdAt) },
            teams = teams.map { BackupTeam(it.id, it.name, it.shortName, it.color, it.headCoachName, it.headCoachLicense, it.assistantCoachName, it.assistantCoachLicense, it.createdAt) },
            players = players.map { BackupPlayer(it.id, it.teamId, it.number, it.name, it.practiceNumber, it.defaultStarter, it.licenseNo, it.captain, it.position, it.sortOrder) },
            games = games.map { BackupGame(it.id, it.status.name, it.setupJson, it.createdAt, it.updatedAt, it.date, it.competition, it.teamAName, it.teamBName, it.scoreA, it.scoreB, it.folderId) },
            events = events.map { BackupEvent(it.id, it.gameId, it.seq, it.period, it.clockMillis, it.json, it.createdAt) },
            history = history.map { BackupHistory(it.id, it.gameId, it.at, it.action, it.target, it.before, it.after) },
        )

    fun teams(file: BackupFile) = file.teams.map {
        TeamEntity(it.id, it.name, it.shortName, it.color, it.headCoachName, it.headCoachLicense, it.assistantCoachName, it.assistantCoachLicense, it.createdAt)
    }

    fun players(file: BackupFile) = file.players.map {
        PlayerEntity(it.id, it.teamId, it.number, it.name, it.practiceNumber, it.defaultStarter, it.licenseNo, it.captain, it.position, it.sortOrder)
    }

    fun folders(file: BackupFile) = file.folders.map { FolderEntity(it.id, it.name, it.createdAt) }

    /** 試合。ファイルの中に無いフォルダを指していたら、未分類にする（どのフォルダからも見えなくなるため） */
    fun games(file: BackupFile): List<GameEntity> {
        val folderIds = file.folders.map { it.id }.toSet()
        return file.games.map {
            GameEntity(
                it.id, GameStatus.valueOf(it.status), it.setupJson, it.createdAt, it.updatedAt, it.date, it.competition, it.teamAName, it.teamBName, it.scoreA, it.scoreB,
                folderId = it.folderId?.takeIf { id -> id in folderIds },
            )
        }
    }

    fun events(file: BackupFile) = file.events.map { GameEventEntity(it.id, it.gameId, it.seq, it.period, it.clockMillis, it.json, it.createdAt) }

    fun history(file: BackupFile) = file.history.map { EditHistoryEntity(it.id, it.gameId, it.at, it.action, it.target, it.before, it.after) }

    /** ファイル名（"basket-score-backup-2026-11-13.json"） */
    fun fileName(date: String) = "basket-score-backup-$date.json"
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM team")
    suspend fun teams(): List<TeamEntity>

    @Query("SELECT * FROM player")
    suspend fun players(): List<PlayerEntity>

    @Query("SELECT * FROM folder")
    suspend fun folders(): List<FolderEntity>

    @Query("SELECT * FROM game")
    suspend fun games(): List<GameEntity>

    @Query("SELECT * FROM game_event")
    suspend fun events(): List<GameEventEntity>

    @Query("SELECT * FROM edit_history")
    suspend fun history(): List<EditHistoryEntity>

    @Query("DELETE FROM edit_history")
    suspend fun deleteHistory()

    @Query("DELETE FROM game")
    suspend fun deleteGames()

    @Query("DELETE FROM team")
    suspend fun deleteTeams()

    @Query("DELETE FROM folder")
    suspend fun deleteFolders()

    @Insert
    suspend fun insertFolders(items: List<FolderEntity>)

    @Insert
    suspend fun insertTeams(items: List<TeamEntity>)

    @Insert
    suspend fun insertPlayers(items: List<PlayerEntity>)

    @Insert
    suspend fun insertGames(items: List<GameEntity>)

    @Insert
    suspend fun insertEvents(items: List<GameEventEntity>)

    @Insert
    suspend fun insertHistory(items: List<EditHistoryEntity>)
}

/** バックアップの書き出しと復元 */
class BackupRepository(private val db: AppDatabase) {
    private val dao = db.backupDao()

    suspend fun export(now: Long = System.currentTimeMillis()): String = db.withTransaction {
        Backup.encode(Backup.from(dao.teams(), dao.players(), dao.games(), dao.events(), dao.history(), now, dao.folders()))
    }

    /** 今のデータをすべて消して、バックアップの中身に置き換える */
    suspend fun restore(text: String): BackupFile {
        val file = Backup.decode(text)
        db.withTransaction {
            dao.deleteHistory()
            dao.deleteGames()
            dao.deleteTeams()
            dao.deleteFolders()
            dao.insertTeams(Backup.teams(file))
            dao.insertPlayers(Backup.players(file))
            dao.insertFolders(Backup.folders(file))
            dao.insertGames(Backup.games(file))
            dao.insertEvents(Backup.events(file))
            dao.insertHistory(Backup.history(file))
        }
        return file
    }
}

/** 前回のバックアップの日時と、促すのを先に延ばした日時（F-1006） */
class BackupPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    var lastBackupAt: Long?
        get() = prefs.getLong(LAST, 0L).takeIf { it > 0 }
        set(value) = prefs.edit { if (value == null) remove(LAST) else putLong(LAST, value) }

    var snoozedUntil: Long
        get() = prefs.getLong(SNOOZE, 0L)
        set(value) = prefs.edit { putLong(SNOOZE, value) }

    companion object {
        private const val LAST = "last_backup_at"
        private const val SNOOZE = "snoozed_until"
        const val DAY = 24 * 60 * 60 * 1000L

        /** 前回から 14 日たったら促す。「あとで」は 3 日延ばす */
        const val INTERVAL_DAYS = 14
        const val SNOOZE_DAYS = 3

        /**
         * 起動したときにバックアップを促すか。試合が無ければ促さない。
         * 一度もバックアップしていなければ、いちばん古い試合を作ってから [INTERVAL_DAYS] 日たったら
         */
        fun shouldPrompt(now: Long, lastBackupAt: Long?, snoozedUntil: Long, oldestGameAt: Long?): Boolean {
            oldestGameAt ?: return false
            if (now < snoozedUntil) return false
            val since = lastBackupAt ?: oldestGameAt
            return now - since >= INTERVAL_DAYS * DAY
        }

        /** 前回のバックアップから何日たったか */
        fun daysSince(now: Long, since: Long): Long = (now - since) / DAY
    }
}
