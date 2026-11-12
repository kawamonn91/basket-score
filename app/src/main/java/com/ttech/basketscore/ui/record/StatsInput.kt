package com.ttech.basketscore.ui.record

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ttech.basketscore.data.LoadedGame
import com.ttech.basketscore.data.StoredEvent
import com.ttech.basketscore.game.ClockRules
import com.ttech.basketscore.game.Deflection
import com.ttech.basketscore.game.Foul
import com.ttech.basketscore.game.GameEvent
import com.ttech.basketscore.game.PossessionChange
import com.ttech.basketscore.game.Rebound
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.Turnover
import com.ttech.basketscore.stats.Court
import com.ttech.basketscore.stats.Sides
import com.ttech.basketscore.stats.StatsRules

/**
 * 作りかけのシュート（F-401）。位置・成否・選手の 3 つを、どの順番でも入れられる。そろったら記録する
 *
 * @param screenX 画面のコートの位置（0〜1、左から）。[underBasket] ならゴール下（F-415）
 * @param kind 2P か 3P（null なら位置から決める）
 */
data class PendingShot(
    val screenX: Double? = null,
    val screenY: Double? = null,
    val underBasket: Boolean = false,
    val made: Boolean? = null,
    val side: Side? = null,
    val playerId: String? = null,
    val kind: ShotKind? = null,
    val shotType: String? = null,
    /** オフェンスリバウンドからのシュート（F-414） */
    val offensiveRebound: Boolean = false,
    val offenseType: String? = null,
    val defenseType: String? = null,
) {
    val hasLocation: Boolean get() = underBasket || screenX != null
    val ready: Boolean get() = hasLocation && made != null && playerId != null && side != null
}

/** 記録のあとに続けて聞くこと */
sealed interface Prompt {
    /** 成功したシュートのアシスト（F-404） */
    data class Assist(val shot: Shot) : Prompt

    /** 外れたシュートのリバウンド（F-403）とブロック（F-405） */
    data class Rebound(val shot: Shot, val blockId: String? = null) : Prompt

    /** ファウルのフリースロー（F-402）。[results] はここまでの成否 */
    data class FreeThrows(val side: Side, val total: Int, val shooterId: String? = null, val results: List<Boolean> = emptyList()) : Prompt

    /** ターンオーバーの種類（F-406・F-408） */
    data class TurnoverType(val side: Side, val playerId: String?) : Prompt

    /** ターンオーバーをスティールした相手 */
    data class Steal(val side: Side, val playerId: String?, val type: String?) : Prompt

    /** スティールから入れたとき、ボールを失った相手 */
    data class LostBy(val stealSide: Side, val stealerId: String) : Prompt
}

/** スタッツの入力（docs/design.md の 3.3）。記録の画面の ViewModel から使う */
class StatsInput(private val vm: RecordViewModel) {

    var pending by mutableStateOf<PendingShot?>(null)
        private set

    var prompt by mutableStateOf<Prompt?>(null)
        private set

    private val game: LoadedGame? get() = vm.game.value
    private val period: Int get() = game?.state?.period?.coerceAtLeast(1) ?: 1

    // --- シュート（F-401・F-414・F-415） ---

    /** コートを押した（画面の位置。0〜1） */
    fun tapCourt(screenX: Double, screenY: Double) {
        update { it.copy(screenX = screenX, screenY = screenY, underBasket = false, kind = null) }
    }

    fun setMade(made: Boolean) = update { it.copy(made = made) }

    /** レイアップ・ダンクのボタン: ゴール下の 2P として入れる */
    fun quick(shotType: String) = update { it.copy(underBasket = true, screenX = null, screenY = null, kind = ShotKind.TWO, shotType = shotType) }

    fun setKind(kind: ShotKind) = update { it.copy(kind = kind) }

    fun setShotType(type: String?) = update { it.copy(shotType = type) }

    fun setOffenseType(type: String?) = update { it.copy(offenseType = type) }

    fun setDefenseType(type: String?) = update { it.copy(defenseType = type) }

    fun toggleOffensiveRebound() = update { it.copy(offensiveRebound = !it.offensiveRebound) }

    fun cancel() {
        pending = null
    }

    /**
     * 選手のボタンを押した。作りかけのシュートや聞いていることがあれば、その答えにして true を返す。
     * 何も無ければ false（ふつうに選手を選ぶ）
     */
    fun tapPlayer(side: Side, playerId: String): Boolean {
        if (pending != null) {
            update { it.copy(side = side, playerId = playerId) }
            return true
        }
        when (val p = prompt) {
            is Prompt.Assist -> if (side == p.shot.side && playerId != p.shot.playerId) {
                answerAssist(playerId)
                return true
            }
            is Prompt.Rebound -> {
                answerRebound(side, playerId)
                return true
            }
            is Prompt.FreeThrows -> if (side == p.side) {
                prompt = p.copy(shooterId = playerId)
                return true
            }
            is Prompt.Steal -> if (side == p.side.other) {
                answerSteal(playerId)
                return true
            }
            is Prompt.LostBy -> if (side == p.stealSide.other) {
                answerLostBy(playerId)
                return true
            }
            else -> Unit
        }
        // 聞いていることに当てはまらない選手を押したら、問いかけを閉じて、その選手を選ぶ
        prompt = null
        return false
    }

    /** 今の作りかけのシュートの、そろえた座標と 2P / 3P（選手のチームが決まっていれば） */
    fun resolved(p: PendingShot): Triple<Double, Double, ShotKind>? {
        val g = game ?: return null
        val side = p.side ?: locationSide(g, p) ?: return null
        val (x, y) = when {
            p.underBasket -> Court.UNDER_BASKET_X to Court.UNDER_BASKET_Y
            p.screenX != null && p.screenY != null -> Court.normalize(p.screenX, p.screenY, Sides.attacksRight(g.setup, side, period))
            else -> return null
        }
        return Triple(x, y, p.kind ?: Court.kindAt(x, y))
    }

    /** 位置だけで決まるチーム（押したゴールを攻めるチーム） */
    private fun locationSide(g: LoadedGame, p: PendingShot): Side? {
        val x = p.screenX ?: return null
        val right = Sides.attackerOfRightBasket(g.setup, period)
        return if (x >= 0.5) right else right.other
    }

    private fun update(change: (PendingShot) -> PendingShot) {
        val start = pending ?: run {
            // 先に選手を選んでいれば、その選手のシュートにする
            prompt = null
            val sel = vm.selected
            vm.selected = null
            PendingShot(side = sel?.side, playerId = sel?.playerId)
        }
        val next = change(start)
        pending = next
        if (next.ready) commit(next)
    }

    private fun commit(p: PendingShot) {
        val side = p.side ?: return
        val playerId = p.playerId ?: return
        val made = p.made ?: return
        val (x, y, kind) = resolved(p) ?: return
        val shot = Shot(
            period = period,
            side = side,
            playerId = playerId,
            kind = kind,
            made = made,
            x = x.toFloat(),
            y = y.toFloat(),
            shotType = p.shotType,
            offenseType = p.offenseType,
            defenseType = p.defenseType,
        )
        pending = null
        val events = buildList<GameEvent> {
            if (p.offensiveRebound) add(Rebound(period, null, side, playerId, offensive = true))
            add(shot)
        }
        vm.add(*events.toTypedArray())
        prompt = if (made) Prompt.Assist(shot) else Prompt.Rebound(shot)
    }

    // --- 続けて聞くこと ---

    fun answerAssist(playerId: String?) {
        val p = prompt as? Prompt.Assist ?: return
        prompt = null
        if (playerId != null) amend(p.shot) { (it as Shot).copy(assistId = playerId) }
    }

    /** リバウンド（[playerId] が null ならチームリバウンド）。OR か DR かは、外れたシュートのチームで決める */
    fun answerRebound(side: Side, playerId: String?) {
        val p = prompt as? Prompt.Rebound ?: return
        prompt = null
        vm.add(Rebound(period, null, side, playerId, offensive = side == p.shot.side))
    }

    /** ブロック（外れたシュートに付ける。リバウンドは続けて聞く） */
    fun answerBlock(playerId: String?) {
        val p = prompt as? Prompt.Rebound ?: return
        prompt = p.copy(blockId = playerId)
        amend(p.shot) { (it as Shot).copy(blockId = playerId) }
    }

    fun skipPrompt() {
        prompt = null
    }

    /** ファウルを記録したあと: フリースローがあれば続けて聞く（スタッツのとき） */
    fun afterFoul(foul: Foul) {
        if (foul.freeThrows > 0 && !foul.cancelled) prompt = Prompt.FreeThrows(foul.side.other, foul.freeThrows)
    }

    fun freeThrow(made: Boolean) {
        val p = prompt as? Prompt.FreeThrows ?: return
        val shooter = p.shooterId ?: return
        val shot = Shot(period, null, p.side, shooter, ShotKind.FT, made)
        vm.add(shot)
        val results = p.results + made
        prompt = when {
            results.size < p.total -> p.copy(results = results)
            made -> null
            else -> Prompt.Rebound(shot)
        }
    }

    // --- ターンオーバーとスティール（F-406） ---

    fun startTurnover(side: Side, playerId: String?) {
        pending = null
        prompt = Prompt.TurnoverType(side, playerId)
    }

    fun answerTurnoverType(type: String?) {
        val p = prompt as? Prompt.TurnoverType ?: return
        prompt = Prompt.Steal(p.side, p.playerId, type)
    }

    fun answerSteal(stealerId: String?) {
        val p = prompt as? Prompt.Steal ?: return
        prompt = null
        vm.add(Turnover(period, null, p.side, p.playerId, p.type, stealById = stealerId))
    }

    fun startSteal(side: Side, stealerId: String) {
        pending = null
        prompt = Prompt.LostBy(side, stealerId)
    }

    fun answerLostBy(playerId: String?) {
        val p = prompt as? Prompt.LostBy ?: return
        prompt = null
        vm.add(Turnover(period, null, p.stealSide.other, playerId, null, stealById = p.stealerId))
    }

    // --- そのほか ---

    /** リバウンド（選手を選んでから）。OR か DR かは自動で決める（F-403） */
    fun rebound(side: Side, playerId: String?) {
        val events = game?.events?.map { it.event } ?: return
        prompt = null
        vm.add(Rebound(period, null, side, playerId, offensive = StatsRules.reboundIsOffensive(events, side)))
    }

    fun deflection(side: Side, playerId: String) = vm.add(Deflection(period, null, side, playerId))

    /** 攻守を手で直す（F-411） */
    fun possession(side: Side) = vm.add(PossessionChange(period, null, side))

    /** 記録したシュートに、あとからアシストやブロックを付ける */
    private fun amend(original: GameEvent, change: (GameEvent) -> GameEvent) {
        val stored = findStored(original) ?: return
        vm.update(stored, change(stored.event))
    }

    private fun findStored(event: GameEvent): StoredEvent? {
        val target = ClockRules.stamp(event, null)
        return game?.events?.lastOrNull { ClockRules.stamp(it.event, null) == target }
    }
}
