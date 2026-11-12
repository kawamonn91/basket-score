package com.ttech.basketscore.rules

/**
 * 大会の区分。規則の初期値は 2026-04-01 の JBA の TO マニュアルによる（docs/requirements.md の 5.2）。
 * U12 は入れない（2026-11-12 の決定）。
 */
enum class Category(val label: String) {
    GENERAL("一般・U18"),
    U15("U15"),
    CUSTOM("自由"),
    ;

    fun defaultRules(): GameRules = when (this) {
        GENERAL, CUSTOM -> GameRules(quarterSeconds = 10 * 60, overtimeSeconds = 5 * 60)
        U15 -> GameRules(quarterSeconds = 8 * 60, overtimeSeconds = 3 * 60, manToManPenalty = true)
    }
}

/** 1 試合の規則。区分の初期値から、項目ごとに変えられる */
data class GameRules(
    val quarterSeconds: Int,
    val overtimeSeconds: Int,
    val timeoutsFirstHalf: Int = 2,
    val timeoutsSecondHalf: Int = 3,
    /** 第4Q の L2M に取れるタイムアウトの上限 */
    val timeoutsInLastTwoMinutes: Int = 2,
    val timeoutsPerOvertime: Int = 1,
    /** この数に達したら、そのクォーターはチームファウルの罰則（次のファウルからフリースロー） */
    val teamFoulLimit: Int = 4,
    /** 個人ファウルの退場の数 */
    val playerFoulLimit: Int = 5,
    /** マンツーマンペナルティ（M）を使うか（U15） */
    val manToManPenalty: Boolean = false,
    /** 出場登録できる選手の数（公式のスコアシートの欄は 18） */
    val maxPlayers: Int = 18,
) {
    /** [period] の長さ（ミリ秒）。1〜4 がクォーター、5 以降が OT */
    fun periodMillis(period: Int): Long = (if (Period.isOvertime(period)) overtimeSeconds else quarterSeconds) * 1000L
}

/** ピリオドの番号: 1〜4 がクォーター、5 以降が OT（5 = OT1） */
object Period {
    const val QUARTERS = 4

    fun isOvertime(period: Int): Boolean = period > QUARTERS

    /** 表示の名前（「2Q」「OT1」） */
    fun label(period: Int): String = if (isOvertime(period)) "OT${period - QUARTERS}" else "${period}Q"

    /** チームファウルを数えるクォーター。OT は第4Q から続けて数える */
    fun teamFoulPeriod(period: Int): Int = minOf(period, QUARTERS)
}
