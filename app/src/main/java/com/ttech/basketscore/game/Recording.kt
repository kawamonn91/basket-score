package com.ttech.basketscore.game

/** 記録の画面の操作を、出来事や試合の設定の変更にする（画面から切り離してテストする） */
object Recording {

    /** 試合を始める: 第1Q の開始と、ジャンプボールでボールを取らなかったチームへのアロー */
    fun startGame(firstPossession: Side): List<GameEvent> =
        listOf(PeriodStart(1), ArrowSet(1, toward = firstPossession.other))

    /** 次のピリオドを始められるか（前のピリオドが終わっていて、試合が終わっていない） */
    fun canStartNextPeriod(state: GameState): Boolean = state.period >= 1 && !state.periodRunning && !state.finished

    fun nextPeriod(state: GameState): PeriodStart = PeriodStart(state.period + 1)

    /** 第4Q 以降が終わって同点なら OT、そうでなければ試合を終えられる */
    fun needsOvertime(state: GameState): Boolean =
        state.period >= 4 && !state.periodRunning && state.a.score == state.b.score

    sealed interface AddPlayerResult {
        data class Added(val setup: GameSetup, val player: RosterPlayer) : AddPlayerResult
        data class Rejected(val reason: String) : AddPlayerResult
    }

    /** 試合中に背番号だけで選手を足す（F-317） */
    fun addPlayer(setup: GameSetup, side: Side, number: String, name: String = ""): AddPlayerResult {
        val team = setup.team(side)
        if (!Jersey.isValid(number)) return AddPlayerResult.Rejected("背番号は 0〜99 か 00 にしてください")
        if (team.players.any { it.number == number }) return AddPlayerResult.Rejected("#$number はもういます")
        if (team.players.size >= setup.rules.maxPlayers) {
            return AddPlayerResult.Rejected("出場登録は ${setup.rules.maxPlayers} 人までです")
        }
        val nextIndex = team.players.mapNotNull { it.id.removePrefix(side.name).toIntOrNull() }.maxOrNull()?.plus(1) ?: 1
        val player = RosterPlayer(id = "${side.name}$nextIndex", number = number, name = name)
        val players = (team.players + player).sortedBy { Jersey.sortKey(it.number) }
        val newTeam = team.copy(players = players)
        val newSetup = if (side == Side.A) setup.copy(teamA = newTeam) else setup.copy(teamB = newTeam)
        return AddPlayerResult.Added(newSetup, player)
    }

    /** そのチームの直近の出来事（新しい順に [count] 件。[events] は並びの位置つき） */
    fun recent(events: List<IndexedValue<GameEvent>>, side: Side, count: Int = 3): List<IndexedValue<GameEvent>> =
        events.asReversed().filter { (_, e) ->
            EventText.sideOf(e) == side || (e is Substitution && e.changes.any { it.side == side })
        }.take(count)
}
