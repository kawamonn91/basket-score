package com.ttech.basketscore

import android.app.Application
import android.content.Context
import com.ttech.basketscore.data.AppDatabase
import com.ttech.basketscore.data.BackupPrefs
import com.ttech.basketscore.data.BackupRepository
import com.ttech.basketscore.data.ClockStore
import com.ttech.basketscore.data.GameRepository
import com.ttech.basketscore.data.TeamRepository

class App : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

/** アプリ全体で 1 つずつ持つもの */
class AppGraph(context: Context) {
    val db: AppDatabase = AppDatabase.create(context)
    val teams = TeamRepository(db)
    val games = GameRepository(db)
    val clocks = ClockStore(context)
    val backup = BackupRepository(db)
    val backupPrefs = BackupPrefs(context)
}

val Context.graph: AppGraph get() = (applicationContext as App).graph
