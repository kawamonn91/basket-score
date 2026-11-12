package com.ttech.basketscore.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TeamWithPlayers(
    @Embedded val team: TeamEntity,
    @Relation(parentColumn = "id", entityColumn = "team_id")
    val players: List<PlayerEntity>,
) {
    /** 背番号の順（並び順を決めていればそちら） */
    val sortedPlayers: List<PlayerEntity>
        get() = players.sortedWith(compareBy<PlayerEntity> { it.sortOrder }.thenBy { it.number.length }.thenBy { it.number })
}

@Dao
abstract class TeamDao {
    @Transaction
    @Query("SELECT * FROM team ORDER BY name")
    abstract fun observeTeams(): Flow<List<TeamWithPlayers>>

    @Transaction
    @Query("SELECT * FROM team WHERE id = :id")
    abstract suspend fun team(id: Long): TeamWithPlayers?

    @Insert
    abstract suspend fun insertTeam(team: TeamEntity): Long

    @Update
    abstract suspend fun updateTeam(team: TeamEntity)

    @Delete
    abstract suspend fun deleteTeam(team: TeamEntity)

    @Insert
    abstract suspend fun insertPlayers(players: List<PlayerEntity>)

    @Query("DELETE FROM player WHERE team_id = :teamId")
    abstract suspend fun deletePlayers(teamId: Long)

    @Query("UPDATE player SET default_starter = CASE WHEN id IN (:playerIds) THEN 1 ELSE 0 END WHERE team_id = :teamId")
    abstract suspend fun setDefaultStarters(teamId: Long, playerIds: List<Long>)

    /** チームと選手をまとめて保存する（選手は入れ替える） */
    @Transaction
    open suspend fun saveTeam(team: TeamEntity, players: List<PlayerEntity>): Long {
        val id = if (team.id == 0L) insertTeam(team) else team.id.also { updateTeam(team) }
        deletePlayers(id)
        insertPlayers(players.mapIndexed { index, p -> p.copy(id = 0, teamId = id, sortOrder = index) })
        return id
    }
}

@Dao
interface GameDao {
    @Query("SELECT * FROM game ORDER BY date DESC, created_at DESC")
    fun observeGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM game WHERE id = :id")
    fun observeGame(id: Long): Flow<GameEntity?>

    @Query("SELECT * FROM game WHERE id = :id")
    suspend fun game(id: Long): GameEntity?

    @Insert
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

    @Query("DELETE FROM game WHERE id = :id")
    suspend fun deleteGame(id: Long)

    @Query("SELECT * FROM game_event WHERE game_id = :gameId ORDER BY seq")
    fun observeEvents(gameId: Long): Flow<List<GameEventEntity>>

    @Query("SELECT * FROM game_event WHERE game_id = :gameId ORDER BY seq")
    suspend fun events(gameId: Long): List<GameEventEntity>

    @Query("SELECT MAX(seq) FROM game_event WHERE game_id = :gameId")
    suspend fun maxSeq(gameId: Long): Long?

    @Insert
    suspend fun insertEvent(event: GameEventEntity): Long

    @Update
    suspend fun updateEvent(event: GameEventEntity)

    @Delete
    suspend fun deleteEvent(event: GameEventEntity)
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(history: EditHistoryEntity)

    @Query("SELECT * FROM edit_history WHERE game_id = :gameId ORDER BY at DESC")
    fun observeForGame(gameId: Long): Flow<List<EditHistoryEntity>>
}
