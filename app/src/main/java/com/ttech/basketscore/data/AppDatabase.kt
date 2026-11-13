package com.ttech.basketscore.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun statusToText(status: GameStatus): String = status.name

    @TypeConverter
    fun textToStatus(text: String): GameStatus = GameStatus.valueOf(text)
}

@Database(
    entities = [TeamEntity::class, PlayerEntity::class, GameEntity::class, GameEventEntity::class, EditHistoryEntity::class],
    version = 2,
    exportSchema = true,
    // 2: 選手の練習用の背番号と既定のスターター（2026-11-12）
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teamDao(): TeamDao
    abstract fun gameDao(): GameDao
    abstract fun historyDao(): HistoryDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "basket-score.db").build()
    }
}
