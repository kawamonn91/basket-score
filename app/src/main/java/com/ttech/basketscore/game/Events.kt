package com.ttech.basketscore.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 試合中の出来事 1 件（docs/design.md の 2.3）。すべての出来事はピリオドとゲームクロックの残りを持つ。
 * ピリオドは 1〜4 がクォーター、5 以降が OT。ゲームクロックの残り（ミリ秒）は、時計を使わない試合では null
 */
@Serializable
sealed interface GameEvent {
    val period: Int
    val clockMillis: Long?
}

@Serializable
@SerialName("period_start")
data class PeriodStart(override val period: Int, override val clockMillis: Long? = null) : GameEvent

@Serializable
@SerialName("period_end")
data class PeriodEnd(override val period: Int, override val clockMillis: Long? = null) : GameEvent

@Serializable
enum class ShotKind(val points: Int, val label: String) {
    FT(1, "FT"),
    TWO(2, "2P"),
    THREE(3, "3P"),
}

/**
 * シュート。成功したものが得点になる（公式の記録では成功だけを入れる）。
 * [x]・[y] はコートの図の座標（0〜1。スタッツのとき）
 */
@Serializable
@SerialName("shot")
data class Shot(
    override val period: Int,
    override val clockMillis: Long? = null,
    val side: Side,
    val playerId: String,
    val kind: ShotKind,
    val made: Boolean,
    val x: Float? = null,
    val y: Float? = null,
    /** シュートの種別（レイアップ・ジャンプシュートなど。種別を記録するとき） */
    val shotType: String? = null,
    val assistId: String? = null,
    /** ブロックした相手の選手 */
    val blockId: String? = null,
) : GameEvent

/** ファウルの記号（JBA の TO マニュアル） */
@Serializable
enum class FoulCode(val label: String) {
    /** パーソナルファウル */
    P("P"),
    /** テクニカルファウル（プレーヤー） */
    T("T"),
    /** アンスポーツマンライクファウル */
    U("U"),
    /** ディスクォリファイングファウル */
    D("D"),
    /** テクニカルファウル（ヘッドコーチ自身の振る舞い） */
    C("C"),
    /** テクニカルファウル（ベンチメンバーの振る舞い。ヘッドコーチに記録する） */
    B("B"),
    /** マンツーマンペナルティ（U15。ヘッドコーチに記録する） */
    M("M"),
}

/** ファウルを記録する相手 */
@Serializable
enum class Offender { PLAYER, HEAD_COACH }

@Serializable
@SerialName("foul")
data class Foul(
    override val period: Int,
    override val clockMillis: Long? = null,
    val side: Side,
    val offender: Offender = Offender.PLAYER,
    /** [offender] が PLAYER のときの選手 */
    val playerId: String? = null,
    val code: FoulCode,
    /** フリースローの数（0〜3）。スコアシートでは記号の右下に書く */
    val freeThrows: Int = 0,
    /** 罰則が相殺された（記号に c を書き添える） */
    val cancelled: Boolean = false,
    /** ファウルを受けた選手（スタッツ） */
    val drawnById: String? = null,
) : GameEvent

/** タイムアウト。時計を使わないときは、欄に書く分を [minute] に手で入れる */
@Serializable
@SerialName("timeout")
data class Timeout(
    override val period: Int,
    override val clockMillis: Long? = null,
    val side: Side,
    val minute: Int? = null,
) : GameEvent

@Serializable
data class SubChange(val side: Side, val outId: String, val inId: String)

/** 交代。両チームの交代を 1 件にまとめて記録できる */
@Serializable
@SerialName("substitution")
data class Substitution(
    override val period: Int,
    override val clockMillis: Long? = null,
    val changes: List<SubChange>,
) : GameEvent

/** 試合の初めに、ポゼッションアローの向きを決める（[toward] が次のジャンプボールシチュエーションでボールを得るチーム） */
@Serializable
@SerialName("arrow_set")
data class ArrowSet(override val period: Int, override val clockMillis: Long? = null, val toward: Side) : GameEvent

/** ジャンプボールシチュエーション。アローを反転する（各ピリオドの初めは PeriodStart が反転する） */
@Serializable
@SerialName("jump_ball")
data class JumpBallSituation(override val period: Int, override val clockMillis: Long? = null) : GameEvent

@Serializable
@SerialName("rebound")
data class Rebound(
    override val period: Int,
    override val clockMillis: Long? = null,
    val side: Side,
    /** null ならチームリバウンド */
    val playerId: String?,
    val offensive: Boolean,
) : GameEvent

@Serializable
@SerialName("turnover")
data class Turnover(
    override val period: Int,
    override val clockMillis: Long? = null,
    val side: Side,
    /** null ならチームのターンオーバー */
    val playerId: String?,
    val type: String? = null,
    /** スティールした相手の選手 */
    val stealById: String? = null,
) : GameEvent

@Serializable
@SerialName("deflection")
data class Deflection(
    override val period: Int,
    override val clockMillis: Long? = null,
    val side: Side,
    val playerId: String,
) : GameEvent

/** 自動で判断できない攻守の交代を直す（スタッツ） */
@Serializable
@SerialName("possession")
data class PossessionChange(override val period: Int, override val clockMillis: Long? = null, val side: Side) : GameEvent

@Serializable
@SerialName("game_end")
data class GameEnd(
    override val period: Int,
    override val clockMillis: Long? = null,
    /** 試合終了時間（24 時間制。"15:12"） */
    val endedAt: String,
) : GameEvent
