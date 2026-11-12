package com.ttech.basketscore.game

import com.ttech.basketscore.rules.Category
import com.ttech.basketscore.rules.GameRules
import kotlinx.serialization.Serializable

/** チーム A・B（公式のスコアシートの呼び方） */
@Serializable
enum class Side {
    A, B;

    val other: Side get() = if (this == A) B else A
}

/** 記録の詳しさ（docs/requirements.md の 4 章） */
@Serializable
enum class DetailLevel(val label: String) {
    OFFICIAL("公式"),
    STATS("公式＋スタッツ"),
    STATS_WITH_TYPES("公式＋スタッツ＋種別"),
}

/** 試合に出場登録した選手の写し。[id] は試合の中だけで使う */
@Serializable
data class RosterPlayer(
    val id: String,
    /** 背番号（"0"〜"99" と "00"） */
    val number: String,
    val name: String,
    /** 登録番号の下 3 桁（省略可） */
    val licenseNo: String = "",
    val captain: Boolean = false,
    val starter: Boolean = false,
)

@Serializable
data class Staff(val name: String, val licenseNo: String = "")

@Serializable
data class TeamSetup(
    val name: String,
    val shortName: String = "",
    /** チームの色（ARGB）。画面と PDF の見出しに使う */
    val color: Long? = null,
    /** ベンチが記録係から見て左か */
    val benchLeft: Boolean,
    val headCoach: Staff? = null,
    val assistantCoach: Staff? = null,
    val players: List<RosterPlayer>,
    /** スタッツを記録するか（チームごと） */
    val recordStats: Boolean = true,
) {
    val starters: List<RosterPlayer> get() = players.filter { it.starter }

    fun player(id: String): RosterPlayer? = players.firstOrNull { it.id == id }
}

/** スコアシートのヘッダー */
@Serializable
data class GameHeader(
    val competition: String = "",
    /** "2026-11-12" */
    val date: String = "",
    /** 開始時刻（24 時間制。"13:30"） */
    val startTime: String = "",
    val venue: String = "",
    val gameNo: String = "",
    val crewChief: String = "",
    val umpire1: String = "",
    val umpire2: String = "",
    val scorer: String = "",
    val assistantScorer: String = "",
    val timer: String = "",
    val shotClockOperator: String = "",
)

/** 1 試合の設定（Room の game に JSON で入れる。docs/design.md の 2.2） */
@Serializable
data class GameSetup(
    val header: GameHeader = GameHeader(),
    val category: Category = Category.GENERAL,
    val rules: GameRules = category.defaultRules(),
    val detail: DetailLevel = DetailLevel.OFFICIAL,
    val useClock: Boolean = true,
    val teamA: TeamSetup,
    val teamB: TeamSetup,
) {
    fun team(side: Side): TeamSetup = if (side == Side.A) teamA else teamB
}
