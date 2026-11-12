package com.ttech.basketscore.game

import com.ttech.basketscore.rules.Period

/** 出来事を、画面の一覧に出す短い文にする（「東西 #4 2P 成功（AST #5）」など） */
object EventText {

    fun teamLabel(setup: GameSetup, side: Side): String = setup.team(side).let { it.shortName.ifBlank { it.name } }

    /** 背番号（「？」は ?、名簿に無い選手は ??） */
    fun number(setup: GameSetup, side: Side, playerId: String?): String = when (playerId) {
        null -> "チーム"
        RosterPlayer.UNKNOWN -> "#?"
        else -> setup.team(side).player(playerId)?.let { "#${it.number}" } ?: "#??"
    }

    fun describe(setup: GameSetup, event: GameEvent): String = when (event) {
        is PeriodStart -> "${Period.label(event.period)} 開始"
        is PeriodEnd -> "${Period.label(event.period)} 終了"
        is Shot -> buildString {
            append("${teamLabel(setup, event.side)} ${number(setup, event.side, event.playerId)} ${event.kind.label} ")
            append(if (event.made) "成功" else "失敗")
            listOfNotNull(event.shotType, event.offenseType, event.defenseType?.let { "相手 $it" })
                .takeIf { it.isNotEmpty() }?.let { append("（${it.joinToString("・")}）") }
            event.assistId?.let { append("（AST ${number(setup, event.side, it)}）") }
            event.blockId?.let { append("（BLK ${number(setup, event.side.other, it)}）") }
        }
        is Foul -> buildString {
            append(teamLabel(setup, event.side) + " ")
            append(if (event.offender == Offender.HEAD_COACH) "コーチ" else number(setup, event.side, event.playerId))
            append(" ファウル ")
            append(FoulMark(event.code, event.freeThrows, event.cancelled, event.period).text)
            if (event.offensive) append("（OF）")
        }
        is Timeout -> "${teamLabel(setup, event.side)} タイムアウト" +
            ((event.minute ?: event.clockMillis?.let { com.ttech.basketscore.rules.Timeouts.sheetMinute(setup.rules.periodMillis(event.period), it) })
                ?.let { "（${it} 分）" } ?: "")
        is Substitution -> "交代 " + event.changes.joinToString("、") {
            "${teamLabel(setup, it.side)} ${number(setup, it.side, it.outId)}→${number(setup, it.side, it.inId)}"
        }
        is ArrowSet -> "アロー → ${teamLabel(setup, event.toward)}"
        is JumpBallSituation -> "ジャンプボールシチュエーション（アローを反転）"
        is Rebound -> "${teamLabel(setup, event.side)} ${number(setup, event.side, event.playerId)} " +
            (if (event.offensive) "オフェンス" else "ディフェンス") + "リバウンド"
        is Turnover -> buildString {
            append("${teamLabel(setup, event.side)} ${number(setup, event.side, event.playerId)} ターンオーバー")
            event.type?.let { append("（$it）") }
            event.stealById?.let { append("（STL ${number(setup, event.side.other, it)}）") }
        }
        is Deflection -> "${teamLabel(setup, event.side)} ${number(setup, event.side, event.playerId)} ディフレクション"
        is PossessionChange -> "攻守交代 → ${teamLabel(setup, event.side)}"
        is GameEnd -> "試合終了（${event.endedAt}）"
    }

    /** その出来事のチーム（どちらのチームでもないものは null） */
    fun sideOf(event: GameEvent): Side? = when (event) {
        is Shot -> event.side
        is Foul -> event.side
        is Timeout -> event.side
        is Rebound -> event.side
        is Turnover -> event.side
        is Deflection -> event.side
        is Substitution -> event.changes.map { it.side }.distinct().singleOrNull()
        else -> null
    }

    /** 「？」で仮に入力した出来事か（一覧で目立たせる） */
    fun hasUnknownPlayer(event: GameEvent): Boolean = when (event) {
        is Shot -> event.playerId == RosterPlayer.UNKNOWN || event.assistId == RosterPlayer.UNKNOWN
        is Foul -> event.playerId == RosterPlayer.UNKNOWN
        is Rebound -> event.playerId == RosterPlayer.UNKNOWN
        is Turnover -> event.playerId == RosterPlayer.UNKNOWN
        is Deflection -> event.playerId == RosterPlayer.UNKNOWN
        else -> false
    }
}
