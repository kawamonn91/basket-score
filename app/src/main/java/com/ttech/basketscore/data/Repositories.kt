package com.ttech.basketscore.data

import com.ttech.basketscore.game.GameEngine
import com.ttech.basketscore.game.GameEvent
import com.ttech.basketscore.game.GameJson
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.GameState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TeamRepository(private val db: AppDatabase) {
    private val dao = db.teamDao()

    fun observeTeams(): Flow<List<TeamWithPlayers>> = dao.observeTeams()

    suspend fun team(id: Long): TeamWithPlayers? = dao.team(id)

    suspend fun save(team: TeamEntity, players: List<PlayerEntity>): Long {
        val before = if (team.id != 0L) dao.team(team.id) else null
        val id = dao.saveTeam(team, players)
        db.historyDao().insert(
            EditHistoryEntity(
                gameId = null,
                at = System.currentTimeMillis(),
                action = if (before == null) "追加" else "変更",
                target = "チーム",
                before = before?.let(::describe),
                after = describe(TeamWithPlayers(team.copy(id = id), players)),
            ),
        )
        return id
    }

    suspend fun delete(team: TeamEntity) {
        val before = dao.team(team.id)
        dao.deleteTeam(team)
        db.historyDao().insert(
            EditHistoryEntity(gameId = null, at = System.currentTimeMillis(), action = "削除", target = "チーム", before = before?.let(::describe), after = null),
        )
    }

    /** 既定のスターターを決める（F-109） */
    suspend fun setDefaultStarters(teamId: Long, playerIds: List<Long>) = dao.setDefaultStarters(teamId, playerIds)

    /** チームのコピー（F-105）。名簿ごと複製する */
    suspend fun copy(id: Long): Long? {
        val source = dao.team(id) ?: return null
        return save(source.team.copy(id = 0, name = source.team.name + "（コピー）", createdAt = System.currentTimeMillis()), source.sortedPlayers)
    }

    private fun describe(team: TeamWithPlayers): String =
        team.team.name + "：" + team.sortedPlayers.joinToString("、") { "#${it.number} ${it.name}" }
}

/** 大会のフォルダ */
class FolderRepository(private val db: AppDatabase) {
    private val dao = db.folderDao()

    fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    suspend fun create(name: String): Long = dao.insert(FolderEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun rename(folder: FolderEntity, name: String) = dao.update(folder.copy(name = name.trim()))

    /** フォルダを消す。中の試合は消さずに未分類へ戻す */
    suspend fun delete(id: Long) = dao.delete(id)
}

/** 試合の出来事 1 件（保存した行と、読み込んだ出来事） */
data class StoredEvent(val entity: GameEventEntity, val event: GameEvent)

/** 読み込んだ試合 */
data class LoadedGame(val entity: GameEntity, val setup: GameSetup, val events: List<StoredEvent>, val state: GameState)

class GameRepository(private val db: AppDatabase) {
    private val dao = db.gameDao()

    /** 出来事の書き込みを 1 本ずつ流す（続けて押しても順番の番号がぶつからないように） */
    private val writes = Mutex()

    fun observeGames(): Flow<List<GameEntity>> = dao.observeGames()

    fun observeGame(gameId: Long): Flow<LoadedGame?> =
        combine(dao.observeGame(gameId), dao.observeEvents(gameId)) { game, events ->
            game?.let { load(it, events) }
        }

    /** 試合を作る。[folderId] は入れる大会のフォルダ（null なら未分類） */
    suspend fun create(setup: GameSetup, folderId: Long? = null): Long {
        val now = System.currentTimeMillis()
        return dao.insertGame(
            GameEntity(
                status = GameStatus.READY,
                setupJson = GameJson.encodeSetup(setup),
                createdAt = now,
                updatedAt = now,
                date = setup.header.date,
                competition = setup.header.competition,
                teamAName = setup.teamA.name,
                teamBName = setup.teamB.name,
                folderId = folderId,
            ),
        )
    }

    /**
     * 試合を大会のフォルダに移す（null なら未分類へ）。記録の書き込み（[refresh] が行ごと書き直す）と
     * 重ならないように、同じ鍵の中で行う
     */
    suspend fun moveGames(ids: List<Long>, folderId: Long?) = writes.withLock {
        if (ids.isNotEmpty()) db.folderDao().moveGames(ids, folderId)
    }

    suspend fun delete(gameId: Long) = dao.deleteGame(gameId)

    /** 試合をまとめて消す（F-1006）。出来事と更新履歴も消える */
    suspend fun deleteGames(ids: List<Long>) = writes.withLock {
        if (ids.isEmpty()) return@withLock
        dao.deleteHistoryOf(ids)
        dao.deleteGames(ids)
    }

    suspend fun oldestGameAt(): Long? = dao.oldestGameAt()

    /** 試合を確定する / 解除する（F-1003）。確定した試合は記録を変えられない */
    suspend fun setLocked(gameId: Long, locked: Boolean) = writes.withLock {
        val game = dao.game(gameId) ?: return@withLock
        dao.updateGame(game.copy(status = if (locked) GameStatus.LOCKED else GameStatus.FINISHED, updatedAt = System.currentTimeMillis()))
        refresh(gameId)
    }

    /** 出来事を最後に足す */
    suspend fun addEvent(gameId: Long, event: GameEvent): Long = writes.withLock {
        val seq = (dao.maxSeq(gameId) ?: 0L) + SEQ_STEP
        val entity = GameEventEntity(
            gameId = gameId,
            seq = seq,
            period = event.period,
            clockMillis = event.clockMillis,
            json = GameJson.encodeEvent(event),
            createdAt = System.currentTimeMillis(),
        )
        val id = dao.insertEvent(entity)
        history(gameId, "追加", null, entity.json)
        refresh(gameId)
        id
    }

    /** 出来事を書き換える */
    suspend fun updateEvent(stored: StoredEvent, event: GameEvent): Unit = writes.withLock {
        val json = GameJson.encodeEvent(event)
        dao.updateEvent(stored.entity.copy(period = event.period, clockMillis = event.clockMillis, json = json))
        history(stored.entity.gameId, "変更", stored.entity.json, json)
        refresh(stored.entity.gameId)
    }

    /** 出来事を消す（取り消し・削除） */
    suspend fun deleteEvent(stored: StoredEvent): Unit = writes.withLock {
        dao.deleteEvent(stored.entity)
        history(stored.entity.gameId, "削除", stored.entity.json, null)
        refresh(stored.entity.gameId)
    }

    /** 試合の設定を変える（試合中の選手の追加など）。一覧用の写しも直す */
    suspend fun updateSetup(gameId: Long, setup: GameSetup) {
        val game = dao.game(gameId) ?: return
        val json = GameJson.encodeSetup(setup)
        dao.updateGame(
            game.copy(
                setupJson = json,
                date = setup.header.date,
                competition = setup.header.competition,
                teamAName = setup.teamA.name,
                teamBName = setup.teamB.name,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        db.historyDao().insert(EditHistoryEntity(gameId = gameId, at = System.currentTimeMillis(), action = "変更", target = "試合の情報", before = game.setupJson, after = json))
    }

    suspend fun setStatus(gameId: Long, status: GameStatus) {
        val game = dao.game(gameId) ?: return
        dao.updateGame(game.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    /** 一覧に出す写し（スコア・状態）を、記録から作り直す */
    private suspend fun refresh(gameId: Long) {
        val game = dao.game(gameId) ?: return
        val loaded = load(game, dao.events(gameId))
        val status = when {
            game.status == GameStatus.LOCKED && loaded.state.finished -> GameStatus.LOCKED
            loaded.state.finished -> GameStatus.FINISHED
            loaded.events.isEmpty() -> GameStatus.READY
            else -> GameStatus.LIVE
        }
        dao.updateGame(
            game.copy(status = status, scoreA = loaded.state.a.score, scoreB = loaded.state.b.score, updatedAt = System.currentTimeMillis()),
        )
    }

    private suspend fun history(gameId: Long, action: String, before: String?, after: String?) {
        db.historyDao().insert(EditHistoryEntity(gameId = gameId, at = System.currentTimeMillis(), action = action, target = "出来事", before = before, after = after))
    }

    private fun load(game: GameEntity, events: List<GameEventEntity>): LoadedGame {
        val setup = GameJson.decodeSetup(game.setupJson)
        val stored = events.mapNotNull { e -> runCatching { StoredEvent(e, GameJson.decodeEvent(e.json)) }.getOrNull() }
        return LoadedGame(game, setup, stored, GameEngine.replay(setup, stored.map { it.event }))
    }

    companion object {
        /** 出来事の順番の間隔（あとで間に差し込めるように空ける） */
        const val SEQ_STEP = 1000L
    }
}
