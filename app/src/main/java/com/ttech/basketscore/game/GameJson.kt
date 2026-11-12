package com.ttech.basketscore.game

import kotlinx.serialization.json.Json

/** 試合の設定と出来事を JSON にする（Room に入れる・バックアップに書き出す） */
object GameJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "event"
    }

    fun encodeEvent(event: GameEvent): String = json.encodeToString(GameEvent.serializer(), event)

    fun decodeEvent(text: String): GameEvent = json.decodeFromString(GameEvent.serializer(), text)

    fun encodeSetup(setup: GameSetup): String = json.encodeToString(GameSetup.serializer(), setup)

    fun decodeSetup(text: String): GameSetup = json.decodeFromString(GameSetup.serializer(), text)
}
