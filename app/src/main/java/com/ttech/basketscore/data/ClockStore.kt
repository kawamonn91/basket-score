package com.ttech.basketscore.data

import android.content.Context
import androidx.core.content.edit

/** 試合ごとの時計の残り（止めたとき・動いている間に数秒ごとに保存する）。アプリが落ちても、止まった時刻から再開する */
class ClockStore(context: Context) {
    private val prefs = context.getSharedPreferences("game_clock", Context.MODE_PRIVATE)

    data class Saved(val period: Int, val remainingMillis: Long)

    fun load(gameId: Long): Saved? {
        val value = prefs.getString(key(gameId), null) ?: return null
        val (period, remaining) = value.split(";").takeIf { it.size == 2 } ?: return null
        return Saved(period.toIntOrNull() ?: return null, remaining.toLongOrNull() ?: return null)
    }

    fun save(gameId: Long, period: Int, remainingMillis: Long) {
        prefs.edit { putString(key(gameId), "$period;$remainingMillis") }
    }

    fun delete(gameId: Long) {
        prefs.edit { remove(key(gameId)) }
    }

    private fun key(gameId: Long) = "game-$gameId"
}
