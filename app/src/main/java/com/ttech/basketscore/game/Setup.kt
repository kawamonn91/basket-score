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
) {
    companion object {
        /** 誰のプレーか分からないとき（「？」で仮に入力し、あとで直す。F-318） */
        const val UNKNOWN = "?"
    }
}

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

/** 試合の種類（集計で絞り込む） */
@Serializable
enum class GameType(val label: String) {
    PRACTICE("練習試合"),
    OFFICIAL("公式試合"),
    OTHER("その他"),
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
    /** 副審 3 人目（公式の様式には欄が無い。画面と分析の PDF に出す） */
    val umpire3: String = "",
    val scorer: String = "",
    val assistantScorer: String = "",
    val timer: String = "",
    val shotClockOperator: String = "",
    val gameType: GameType = GameType.PRACTICE,
    val memo: String = "",
)

/** 1 試合の設定（Room の game に JSON で入れる。docs/design.md の 2.2） */
@Serializable
data class GameSetup(
    val header: GameHeader = GameHeader(),
    val category: Category = Category.GENERAL,
    val rules: GameRules = category.defaultRules(),
    val detail: DetailLevel = DetailLevel.OFFICIAL,
    val useClock: Boolean = true,
    /** 練習用の背番号を使うか（出場登録の写しに入る背番号を決める） */
    val usePracticeNumbers: Boolean = false,
    /** 前半に、相手のベンチ側のゴールを攻めるか（false なら自分のベンチ側を攻める） */
    val attackOpponentBenchFirstHalf: Boolean = true,
    /** 後半に、画面の左右のチームも入れ替えるか（false ならベンチ固定で、コートの攻める向きだけを入れ替える。F-409） */
    val swapPanelsSecondHalf: Boolean = false,
    val teamA: TeamSetup,
    val teamB: TeamSetup,
) {
    fun team(side: Side): TeamSetup = if (side == Side.A) teamA else teamB

    /** スタッツを記録するか（詳しさが「公式」でなければ） */
    val recordsStats: Boolean get() = detail != DetailLevel.OFFICIAL

    /** プレーの種別を記録するか */
    val recordsTypes: Boolean get() = detail == DetailLevel.STATS_WITH_TYPES
}
