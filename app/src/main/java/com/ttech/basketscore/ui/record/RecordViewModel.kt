package com.ttech.basketscore.ui.record

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ttech.basketscore.data.GameRepository
import com.ttech.basketscore.data.LoadedGame
import com.ttech.basketscore.data.StoredEvent
import com.ttech.basketscore.game.Alert
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
import com.ttech.basketscore.game.Recording
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.SubChange
import com.ttech.basketscore.game.Substitution
import com.ttech.basketscore.game.Timeout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 選んでいる選手（「？」も）またはコーチ */
data class Selection(val side: Side, val playerId: String)

/** 記録の画面（docs/design.md の 3.3）。いまは公式の記録だけ。時計は 5 段目、スタッツは 6 段目で足す */
class RecordViewModel(private val repo: GameRepository, val gameId: Long) : ViewModel() {

    val game: StateFlow<LoadedGame?> = repo.observeGame(gameId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var selected by mutableStateOf<Selection?>(null)

    /** 画面の上に出す知らせ（直前の操作で起きたこと） */
    var banner by mutableStateOf<List<String>>(emptyList())
        private set

    /** 前半（第2Q）の終わりに出す照合（F-311） */
    var halfTimeCheck by mutableStateOf(false)

    private val current: LoadedGame? get() = game.value

    private val period: Int get() = current?.state?.period?.coerceAtLeast(1) ?: 1

    /** 出来事を足す。足したあとの状態から、知らせることを出す */
    fun add(vararg events: GameEvent) {
        val g = current ?: return
        val after = GameEngine.replay(g.setup, g.events.map { it.event } + events)
        banner = after.alerts.map { alertText(g.setup, it) }
        if (events.any { it is PeriodEnd && it.period == 2 }) halfTimeCheck = true
        selected = null
        viewModelScope.launch { events.forEach { repo.addEvent(gameId, it) } }
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
