package com.ttech.basketscore.ui.record

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ttech.basketscore.data.ClockStore
import com.ttech.basketscore.data.GameRepository
import com.ttech.basketscore.data.LoadedGame
import com.ttech.basketscore.data.StoredEvent
import com.ttech.basketscore.game.Alert
import com.ttech.basketscore.game.ClockRules
import com.ttech.basketscore.game.GameClock
import com.ttech.basketscore.game.EventText
import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.FoulCode
import com.ttech.basketscore.game.GameEnd
import com.ttech.basketscore.game.GameEngine
import com.ttech.basketscore.game.GameEvent
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.JumpBallSituation
import com.ttech.basketscore.game.Offender
import com.ttech.basketscore.game.PeriodEnd
import com.ttech.basketscore.game.PeriodStart
import com.ttech.basketscore.game.Recording
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.SubChange
import com.ttech.basketscore.game.Substitution
import com.ttech.basketscore.game.Timeout
import com.ttech.basketscore.rules.LastTwoMinutes
import com.ttech.basketscore.rules.Period
import com.ttech.basketscore.rules.TakenTimeout
import com.ttech.basketscore.rules.Timeouts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 選んでいる選手（「？」も）またはコーチ */
data class Selection(val side: Side, val playerId: String)

/** 0:00 で「次へ」を押したときに聞くこと（F-508） */
sealed interface Buzzer {
    /** 次のピリオド（クォーター・OT）へ進む */
    data class NextPeriod(val ended: Int, val next: Int) : Buzzer

    /** 第4Q 以降で同点でない: 試合を終える */
    data class EndGame(val ended: Int) : Buzzer
}

/**
 * 記録の画面（docs/design.md の 3.3）。いまは公式の記録と時計。スタッツは 6 段目で足す。
 * [clocks] を渡したときだけ時計を動かす（スコアシートの画面などでは渡さない）
 */
class RecordViewModel(
    private val repo: GameRepository,
    val gameId: Long,
    private val clocks: ClockStore? = null,
) : ViewModel() {

    val game: StateFlow<LoadedGame?> = repo.observeGame(gameId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var selected by mutableStateOf<Selection?>(null)

    /** 画面の上に出す知らせ（直前の操作で起きたこと） */
    var banner by mutableStateOf<List<String>>(emptyList())
        private set

    /** 前半（第2Q）の終わりに出す照合（F-311） */
    var halfTimeCheck by mutableStateOf(false)

    private val current: LoadedGame? get() = game.value

    private val period: Int get() = current?.state?.period?.coerceAtLeast(1) ?: 1

    // --- ゲームクロック（F-501〜F-503・F-508） ---

    private val clock = GameClock { SystemClock.elapsedRealtime() }

    /** 時計を使うか（試合の設定で使うと決め、この画面が時計を持つとき） */
    val usesClock: Boolean get() = clocks != null && current?.setup?.useClock == true

    /** 画面に出す時計の残り（動いている間は 50 ミリ秒ごとに更新） */
    var clockMillis by mutableLongStateOf(0L)
        private set

    var clockRunning by mutableStateOf(false)
        private set

    /** 0:00 で「次へ」を押した（次のピリオドへ進むか、試合を終えるかを聞く） */
    var buzzer by mutableStateOf<Buzzer?>(null)

    private var ticker: Job? = null
    private var synced = false
    private var lastTwoMinutesAnnounced = false

    /** ピリオドを戻したあと（やり直し）に合わせる時計 */
    private var pendingClock: ClockStore.Saved? = null

    init {
        viewModelScope.launch { game.filterNotNull().collect { syncClock(it) } }
    }

    /** ピリオドが変わったら（開始・取り消し）、時計をそのピリオドに合わせる */
    private fun syncClock(g: LoadedGame) {
        val store = clocks ?: return
        if (!g.setup.useClock) return
        val period = g.state.period
        if (synced && clock.period == period) return
        val full = g.setup.rules.periodMillis(period.coerceAtLeast(1))
        val pending = pendingClock?.takeIf { it.period == period }
        val saved = if (!synced) store.load(gameId)?.takeIf { it.period == period } else null
        val remaining = when {
            period == 0 -> full
            pending != null -> pending.remainingMillis
            saved != null -> saved.remainingMillis
            else -> ClockRules.restore(g.setup.rules, g.state, g.events.map { it.event })
        }
        pendingClock = null
        clock.reset(period, full, remaining)
        synced = true
        lastTwoMinutesAnnounced = period == 4 && remaining <= LastTwoMinutes.MILLIS
        publish()
        saveClock()
    }

    private fun publish() {
        clockMillis = clock.remaining()
        clockRunning = clock.running
    }

    private fun saveClock() {
        if (clock.period >= 1) clocks?.save(gameId, clock.period, clock.remaining())
    }

    /** スタート / ストップ（キーボードのスペースでも） */
    fun toggleClock() {
        if (clock.running) stopClock() else startClock()
    }

    fun startClock() {
        val state = current?.state ?: return
        if (!usesClock || !state.periodRunning) return
        if (clock.remaining() <= 0) {
            buzzer = buzzerFor(state.period, state.a.score == state.b.score)
            return
        }
        if (clock.start()) runTicker()
        publish()
    }

    fun stopClock() {
        if (!clock.running) return
        clock.stop()
        publish()
        saveClock()
    }

    private fun buzzerFor(period: Int, tied: Boolean): Buzzer =
        if (period < 4 || tied) Buzzer.NextPeriod(period, period + 1) else Buzzer.EndGame(period)

    private fun runTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            var lastSave = SystemClock.elapsedRealtime()
            while (clock.running) {
                val before = clock.remaining()
                delay(50)
                val now = clock.remaining()
                if (ClockRules.crossedLastTwoMinutes(clock.period, before, now)) announceLastTwoMinutes()
                if (clock.stopIfExpired()) {
                    publish()
                    saveClock()
                    banner = listOf("${Period.label(clock.period)} の時間が終わりました（「次へ」で次に進みます）")
                    break
                }
                publish()
                if (SystemClock.elapsedRealtime() - lastSave >= 5_000) {
                    saveClock()
                    lastSave = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /** 第4Q の残り 2 分に入った（F-503）。後半のタイムアウトを取っていないチームは、最初の枠が使えなくなる */
    private fun announceLastTwoMinutes() {
        val g = current ?: return
        if (clock.period != 4 || lastTwoMinutesAnnounced) return
        lastTwoMinutesAnnounced = true
        banner = buildList {
            add(alertText(g.setup, Alert.EnteredLastTwoMinutes))
            Side.entries.forEach { side ->
                val taken = g.state.team(side).timeouts.map { TakenTimeout(it.period, it.remainingMillis ?: Long.MAX_VALUE) }
                if (Timeouts.firstSecondHalfBoxLost(taken)) {
                    add("${EventText.teamLabel(g.setup, side)} は後半のタイムアウトを取っていないため、最初の枠が使えなくなりました")
                }
            }
        }
    }

    /** 時計を直す */
    fun setClock(millis: Long) {
        clock.set(millis)
        // 残り 2 分より前に戻したら、もう一度 L2M を知らせる
        if (clock.remaining() > LastTwoMinutes.MILLIS) lastTwoMinutesAnnounced = false
        publish()
        saveClock()
    }

    /** 終わったピリオドに時間を足して、やり直す（ピリオドの終わりと次のピリオドの初めの記録を消す。F-508） */
    fun resumePeriod(millis: Long) {
        val g = current ?: return
        val events = g.events.map { it.event }
        val tail = ClockRules.resumableTail(events) ?: return
        val ended = events[events.size - tail] as? PeriodEnd ?: return
        if (tail == 1) {
            clock.set(millis)
        } else {
            pendingClock = ClockStore.Saved(ended.period, millis)
        }
        banner = emptyList()
        publish()
        saveClock()
        viewModelScope.launch { g.events.takeLast(tail).asReversed().forEach { repo.deleteEvent(it) } }
    }

    /** 0:00 の「次へ」で、次のピリオドへ進む（F-508）。試合を終えるときは [endGame] */
    fun advanceAfterBuzzer() {
        val b = buzzer as? Buzzer.NextPeriod ?: return
        buzzer = null
        add(PeriodEnd(b.ended), PeriodStart(b.next))
    }

    /** 時計を使うときの、出来事に付ける時刻（F-502）。ピリオドの初めはその長さ、ピリオドの外は付けない */
    private fun stamp(g: LoadedGame, event: GameEvent): GameEvent {
        if (!usesClock || event.clockMillis != null) return event
        val clockMillis = when (event) {
            is PeriodStart -> g.setup.rules.periodMillis(event.period)
            is PeriodEnd, is GameEnd -> clock.remaining()
            else -> if (g.state.periodRunning) clock.remaining() else null
        }
        return ClockRules.stamp(event, clockMillis)
    }

    /** タイムアウトの残りなどを数えるときの時計の残り（時計を使わなければ L2M を考えない） */
    val clockForRules: Long get() = if (usesClock) clockMillis else Long.MAX_VALUE

    override fun onCleared() {
        if (clock.running) {
            clock.stop()
            saveClock()
        }
    }

    // --- 記録 ---

    /** 出来事を足す。時刻を付け、時計を自動で止める / 動かし、足したあとの状態から知らせることを出す */
    fun add(vararg events: GameEvent) {
        val g = current ?: return
        val stamped = events.map { stamp(g, it) }
        val before = g.events.map { it.event }
        val after = GameEngine.replay(g.setup, before + stamped)
        banner = after.alerts
            .filterNot { it == Alert.EnteredLastTwoMinutes && lastTwoMinutesAnnounced }
            .map { alertText(g.setup, it) }
        if (stamped.any { it is PeriodEnd && it.period == 2 }) halfTimeCheck = true
        selected = null
        if (usesClock) {
            var acc = before
            stamped.forEach { e ->
                when (ClockRules.autoAction(acc, e)) {
                    ClockRules.Action.STOP -> stopClock()
                    ClockRules.Action.START -> startClock()
                    null -> Unit
                }
                acc = acc + e
            }
            if (stamped.any { it is PeriodEnd || it is GameEnd }) stopClock()
        }
        viewModelScope.launch { stamped.forEach { repo.addEvent(gameId, it) } }
    }

    fun dismissBanner() {
        banner = emptyList()
    }

    fun score(side: Side, playerId: String, kind: ShotKind) = add(Shot(period, null, side, playerId, kind, made = true))

    fun foul(side: Side, playerId: String?, code: FoulCode, freeThrows: Int, cancelled: Boolean, offensive: Boolean) = add(
        Foul(
            period = period,
            side = side,
            offender = if (playerId == null) Offender.HEAD_COACH else Offender.PLAYER,
            playerId = playerId,
            code = code,
            freeThrows = freeThrows,
            cancelled = cancelled,
            offensive = offensive,
        ),
    )

    fun timeout(side: Side, minute: Int?) = add(Timeout(period, null, side, minute))

    fun substitute(changes: List<SubChange>) {
        if (changes.isNotEmpty()) add(Substitution(period, null, changes))
    }

    fun flipArrow() = add(JumpBallSituation(period, null))

    fun startGame(firstPossession: Side) = add(*Recording.startGame(firstPossession).toTypedArray())

    fun nextPeriod() {
        val state = current?.state ?: return
        if (Recording.canStartNextPeriod(state)) add(Recording.nextPeriod(state))
    }

    fun endPeriod() {
        val state = current?.state ?: return
        if (state.periodRunning) add(PeriodEnd(state.period))
    }

    fun endGame() {
        val state = current?.state ?: return
        val events = buildList {
            if (state.periodRunning) add(PeriodEnd(state.period))
            add(GameEnd(state.period.coerceAtLeast(1), null, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))))
        }
        add(*events.toTypedArray())
    }

    /** 直前の出来事を消す */
    fun undo() {
        val last = current?.events?.lastOrNull() ?: return
        banner = emptyList()
        viewModelScope.launch { repo.deleteEvent(last) }
    }

    fun delete(stored: StoredEvent) {
        viewModelScope.launch { repo.deleteEvent(stored) }
    }

    fun update(stored: StoredEvent, event: GameEvent) {
        viewModelScope.launch { repo.updateEvent(stored, event) }
    }

    /** 試合中に背番号だけで選手を足す（F-317）。足せなければ理由を返す */
    fun addPlayer(side: Side, number: String): String? {
        val g = current ?: return null
        return when (val result = Recording.addPlayer(g.setup, side, number)) {
            is Recording.AddPlayerResult.Added -> {
                viewModelScope.launch { repo.updateSetup(gameId, result.setup) }
                null
            }
            is Recording.AddPlayerResult.Rejected -> result.reason
        }
    }

    companion object {
        fun alertText(setup: GameSetup, alert: Alert): String = when (alert) {
            is Alert.FifthFoul -> "${EventText.teamLabel(setup, alert.side)} ${EventText.number(setup, alert.side, alert.playerId)} が 5 個目のファウル（審判に知らせる）"
            is Alert.Disqualified -> "${EventText.teamLabel(setup, alert.side)} " +
                (alert.playerId?.let { EventText.number(setup, alert.side, it) } ?: "コーチ") + " 失格・退場（GD）"
            is Alert.TeamFoulPenalty -> "${EventText.teamLabel(setup, alert.side)} チームファウルの罰則（次のファウルからフリースロー）"
            is Alert.TimeoutOverLimit -> "${EventText.teamLabel(setup, alert.side)} はタイムアウトの上限を超えています"
            Alert.EnteredLastTwoMinutes -> "第4Q の残り 2 分（L2M）に入りました"
        }
    }
}
