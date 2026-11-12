package com.ttech.basketscore.data

import android.content.Context
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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teamDao(): TeamDao
    abstract fun gameDao(): GameDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "basket-score.db").build()
    }
}
