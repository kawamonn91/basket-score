package com.ttech.basketscore.game

import com.ttech.basketscore.rules.GameRules

/** 背番号の決まり（"0"〜"99" と "00"） */
object Jersey {
    private val pattern = Regex("00|0|[1-9][0-9]?")

    fun isValid(number: String): Boolean = pattern.matches(number)

    /** 並べ替えの鍵（"0"・"00"・"1"・…・"99" の順） */
    fun sortKey(number: String): Int = when (number) {
        "0" -> -2
        "00" -> -1
        else -> number.toIntOrNull() ?: Int.MAX_VALUE
    }
}

/** 名簿と出場登録の確かめ。問題があれば、画面に出す文を返す */
object RosterRules {

    const val STARTERS = 5

    /** チームの名簿（試合の外） */
    fun teamProblems(numbers: List<String>, captains: Int): List<String> = buildList {
        val invalid = numbers.filter { !Jersey.isValid(it) }
        if (invalid.isNotEmpty()) add("背番号は 0〜99 か 00 にしてください（${invalid.joinToString("・").ifBlank { "空欄" }}）")
        duplicates(numbers).takeIf { it.isNotEmpty() }?.let { add("背番号が重なっています（${it.joinToString("・")}）") }
        if (captains > 1) add("キャプテンは 1 人にしてください")
    }

    /** 試合の出場登録 */
    fun entryProblems(players: List<RosterPlayer>, rules: GameRules): List<String> = buildList {
        addAll(teamProblems(players.map { it.number }, players.count { it.captain }))
        if (players.size > rules.maxPlayers) add("出場登録は ${rules.maxPlayers} 人までです（今は ${players.size} 人）")
        val starters = players.count { it.starter }
        if (starters != STARTERS) add("スターターを $STARTERS 人選んでください（今は $starters 人）")
    }

    private fun duplicates(numbers: List<String>): List<String> =
        numbers.filter { it.isNotBlank() }.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.sortedBy(Jersey::sortKey)
}
