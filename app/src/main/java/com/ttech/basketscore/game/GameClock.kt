package com.ttech.basketscore.game

import com.ttech.basketscore.rules.GameRules
import com.ttech.basketscore.rules.LastTwoMinutes

/**
 * ゲームクロック（F-501）。[now] は単調に増えるミリ秒（アプリの中では SystemClock.elapsedRealtime）。
 * アプリが動いている間だけ進む。止めたときの残りを保存して、アプリが落ちても止まった時刻から再開する（docs/design.md の 5 章）
 */
class GameClock(private val now: () -> Long) {
    /** 計っているピリオド（0 は試合前） */
    var period: Int = 0
        private set

    /** そのピリオドの長さ（ミリ秒） */
    var periodMillis: Long = 0
        private set

    /** 止まっているときの残り。動いているときは [anchor] の時点の残り */
    private var base: Long = 0

    /** 動かした時刻。止まっていれば null */
    private var anchor: Long? = null

    val running: Boolean get() = anchor != null

    fun remaining(): Long = anchor?.let { (base - (now() - it)).coerceAtLeast(0) } ?: base

    /** 動かす。残りが 0 なら動かさない（ピリオドの終わり。次へ進むかを聞く） */
    fun start(): Boolean {
        if (running || base <= 0) return false
        anchor = now()
        return true
    }

    fun stop() {
        base = remaining()
        anchor = null
    }

    /** 時刻を直す（0〜ピリオドの長さ）。動いていれば、その時刻から続けて計る */
    fun set(remaining: Long) {
        base = remaining.coerceIn(0, periodMillis)
        if (running) anchor = now()
    }

    /** ピリオドを変える。止まった状態で [remaining]（既定はピリオドの長さ）にする */
    fun reset(period: Int, periodMillis: Long, remaining: Long = periodMillis) {
        this.period = period
        this.periodMillis = periodMillis
        base = remaining.coerceIn(0, periodMillis)
        anchor = null
    }

    /** 0:00 になっていたら止める。止めたら true */
    fun stopIfExpired(): Boolean {
        if (running && remaining() <= 0) {
            base = 0
            anchor = null
            return true
        }
        return false
    }
}

/** 時計にかかわる決まり（純粋な計算。画面と切り離してテストする） */
object ClockRules {

    /** 時計が知らせる操作 */
    enum class Action { START, STOP }

    /** 出来事にゲームクロックの時刻を付ける（F-502） */
    fun stamp(event: GameEvent, clock: Long?): GameEvent = when (event) {
        is PeriodStart -> event.copy(clockMillis = clock)
        is PeriodEnd -> event.copy(clockMillis = clock)
        is Shot -> event.copy(clockMillis = clock)
        is Foul -> event.copy(clockMillis = clock)
        is Timeout -> event.copy(clockMillis = clock)
        is Substitution -> event.copy(clockMillis = clock)
        is ArrowSet -> event.copy(clockMillis = clock)
        is JumpBallSituation -> event.copy(clockMillis = clock)
        is Rebound -> event.copy(clockMillis = clock)
        is Turnover -> event.copy(clockMillis = clock)
        is Deflection -> event.copy(clockMillis = clock)
        is PossessionChange -> event.copy(clockMillis = clock)
        is GameEnd -> event.copy(clockMillis = clock)
    }

    /**
     * 記録を足したときの時計の自動の操作（F-508）: タイムアウトで止める、最後のフリースローが外れたら動かす。
     * [before] は足す前の出来事の並び
     */
    fun autoAction(before: List<GameEvent>, added: GameEvent): Action? = when {
        added is Timeout -> Action.STOP
        added is Shot && added.kind == ShotKind.FT && !added.made && isLastFreeThrow(before, added) -> Action.START
        else -> null
    }

    /**
     * そのフリースローが、直前のファウルで与えられたフリースローの最後の 1 本か。
     * 同じピリオドで、相手チームのファウル（フリースローの数が 1 以上）のあとに入れたフリースローの本数で数える
     */
    fun isLastFreeThrow(before: List<GameEvent>, shot: Shot): Boolean {
        val foulIndex = before.indexOfLast { it is Foul && it.side == shot.side.other && it.freeThrows > 0 && !it.cancelled }
        if (foulIndex < 0) return false
        val foul = before[foulIndex] as Foul
        if (foul.period != shot.period) return false
        val taken = before.drop(foulIndex + 1).count { it is Shot && it.kind == ShotKind.FT && it.side == shot.side }
        return taken + 1 >= foul.freeThrows
    }

    /** 終わったピリオドを再開するとき、後ろから消す出来事の数（ピリオドの終わりと、次のピリオドの初めだけなら）。再開できなければ null */
    fun resumableTail(events: List<GameEvent>): Int? {
        val last = events.lastOrNull() ?: return null
        if (last is PeriodEnd) return 1
        val prev = events.getOrNull(events.size - 2)
        if (last is PeriodStart && prev is PeriodEnd && last.period == prev.period + 1) return 2
        return null
    }

    /**
     * 記録から、今のピリオドの時計の残りを戻す（保存した値が無いとき）。
     * ピリオドが終わっていれば、その終わりの時刻（無ければ 0）。途中なら、そのピリオドの最後の時刻（無ければピリオドの長さ）
     */
    fun restore(rules: GameRules, state: GameState, events: List<GameEvent>): Long {
        val period = state.period.coerceAtLeast(1)
        val full = rules.periodMillis(period)
        if (state.period >= 1 && !state.periodRunning) {
            return events.lastOrNull { it is PeriodEnd && it.period == state.period }?.clockMillis ?: 0
        }
        return events.lastOrNull { it.period == period && it.clockMillis != null }?.clockMillis ?: full
    }

    /** 第4Q・OT の残り 2:00 をまたいだか（L2M に入った。F-503） */
    fun crossedLastTwoMinutes(period: Int, before: Long, after: Long): Boolean =
        !LastTwoMinutes.contains(period, before) && LastTwoMinutes.contains(period, after)
}

/** 時刻の表し方 */
object ClockFormat {
    /** 得点板: 1 分以上は "7:43"、1 分未満は 10 分の 1 秒まで "59.9" */
    fun board(millis: Long): String {
        val ms = millis.coerceAtLeast(0)
        return if (ms >= 60_000) {
            val s = ms / 1000
            "%d:%02d".format(s / 60, s % 60)
        } else {
            "%d.%d".format(ms / 1000, ms % 1000 / 100)
        }
    }

    /** 記録の一覧: 1 分以上は "7:43"、1 分未満は "0:59.9" */
    fun stamp(millis: Long): String {
        val ms = millis.coerceAtLeast(0)
        val s = ms / 1000
        return if (ms >= 60_000) "%d:%02d".format(s / 60, s % 60) else "0:%02d.%d".format(s, ms % 1000 / 100)
    }

    /** ピリオドと時刻（"2Q 7:43"。時刻が無ければ "2Q"） */
    fun at(period: Int, clockMillis: Long?): String =
        com.ttech.basketscore.rules.Period.label(period) + (clockMillis?.let { " " + stamp(it) } ?: "")
}
