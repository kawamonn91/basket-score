package com.ttech.basketscore.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** チーム（docs/design.md の 2.1） */
@Entity(tableName = "team")
data class TeamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "short_name") val shortName: String = "",
    /** チームの色（ARGB） */
    val color: Long? = null,
    @ColumnInfo(name = "head_coach_name") val headCoachName: String = "",
    @ColumnInfo(name = "head_coach_license") val headCoachLicense: String = "",
    @ColumnInfo(name = "assistant_coach_name") val assistantCoachName: String = "",
    @ColumnInfo(name = "assistant_coach_license") val assistantCoachLicense: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "player",
    foreignKeys = [ForeignKey(entity = TeamEntity::class, parentColumns = ["id"], childColumns = ["team_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("team_id")],
)
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "team_id") val teamId: Long,
    /** 背番号（"0"〜"99" と "00"） */
    val number: String,
    val name: String,
    /** 登録番号の下 3 桁 */
    @ColumnInfo(name = "license_no") val licenseNo: String = "",
    val captain: Boolean = false,
    val position: String = "",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

enum class GameStatus(val label: String) {
    /** 準備が終わり、まだ記録していない */
    READY("開始前"),
    LIVE("試合中"),
    FINISHED("終了"),
    /** 確定（誤って変えないように鍵をかけた） */
    LOCKED("確定"),
}

/** 試合。設定は JSON（GameSetup）で持ち、一覧に出す項目は写しを持つ */
@Entity(tableName = "game")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val status: GameStatus,
    @ColumnInfo(name = "setup_json") val setupJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** 一覧用の写し */
    val date: String,
    val competition: String,
    @ColumnInfo(name = "team_a_name") val teamAName: String,
    @ColumnInfo(name = "team_b_name") val teamBName: String,
    @ColumnInfo(name = "score_a") val scoreA: Int = 0,
    @ColumnInfo(name = "score_b") val scoreB: Int = 0,
)

/** 試合の出来事 1 件。[seq] は並びの順番（差し込みのために間を空けて振る） */
@Entity(
    tableName = "game_event",
    foreignKeys = [ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["game_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["game_id", "seq"], unique = true)],
)
data class GameEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    val seq: Long,
    val period: Int,
    @ColumnInfo(name = "clock_millis") val clockMillis: Long?,
    /** 出来事（GameJson） */
    val json: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** 更新履歴（F-313）。チームや選手の変更は [gameId] が null */
@Entity(tableName = "edit_history", indices = [Index("game_id")])
data class EditHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long?,
    val at: Long,
    /** 追加・変更・削除 */
    val action: String,
    /** 出来事・選手・チーム・試合の情報 */
    val target: String,
    val before: String?,
    val after: String?,
)
