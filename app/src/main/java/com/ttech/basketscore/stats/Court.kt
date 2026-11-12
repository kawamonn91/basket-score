package com.ttech.basketscore.stats

import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.ShotKind
import com.ttech.basketscore.game.Side
import kotlin.math.hypot

/**
 * コートの図（F-401）。FIBA の寸法（28 m × 15 m）。
 *
 * シュートの位置は「右のゴールを攻める向き」にそろえた座標で持つ（x: 0 が自分のエンドライン、1 が攻めるゴールのエンドライン。
 * y: 0 が記録係から見て奥のサイドライン、1 が手前）。左のゴールを攻めたシュートは 180 度回して持つ（シューターから見た左右を保つ）
 */
object Court {
    const val LENGTH = 28.0
    const val WIDTH = 15.0

    /** ゴールの中心（エンドラインから 1.575 m） */
    const val BASKET_FROM_BASELINE = 1.575

    /** 3P ラインの半径 */
    const val THREE_RADIUS = 6.75

    /** コーナーの 3P ラインとサイドラインの間（0.9 m）。ゴールの中心からの横の距離は 6.6 m */
    const val THREE_CORNER = WIDTH / 2 - 0.9

    /** ゴール下（レイアップ・ダンクのボタン。F-415） */
    const val UNDER_BASKET_X = 1 - (BASKET_FROM_BASELINE + 0.6) / LENGTH
    const val UNDER_BASKET_Y = 0.5

    /** そろえた座標で、3P の位置か */
    fun isThree(x: Double, y: Double): Boolean {
        val dx = (1 - x) * LENGTH - BASKET_FROM_BASELINE
        val dy = (y - 0.5) * WIDTH
        return kotlin.math.abs(dy) > THREE_CORNER || hypot(dx, dy) > THREE_RADIUS
    }

    /** 位置から決まる 2P か 3P か */
    fun kindAt(x: Double, y: Double): ShotKind = if (isThree(x, y)) ShotKind.THREE else ShotKind.TWO

    /** ゴールからの距離（m） */
    fun distance(x: Double, y: Double): Double = hypot((1 - x) * LENGTH - BASKET_FROM_BASELINE, (y - 0.5) * WIDTH)

    /** 画面のコートの座標（0〜1。x は左から）を、[attacksRight] のチームのそろえた座標にする */
    fun normalize(screenX: Double, screenY: Double, attacksRight: Boolean): Pair<Double, Double> =
        if (attacksRight) screenX to screenY else (1 - screenX) to (1 - screenY)

    /** そろえた座標を、画面のコートの座標に戻す */
    fun toScreen(x: Double, y: Double, attacksRight: Boolean): Pair<Double, Double> = normalize(x, y, attacksRight)
}

/** 前半・後半の攻める向きと、画面の左右のチーム（F-209・F-409） */
object Sides {
    /** 後半（第3Q 以降と OT） */
    fun secondHalf(period: Int): Boolean = period >= 3

    /** ベンチが記録係から見て左のチーム */
    fun benchLeft(setup: GameSetup): Side = if (setup.teamA.benchLeft) Side.A else Side.B

    /**
     * そのピリオドで、[side] が右のゴールを攻めるか。
     * 前半は、相手のベンチ側を攻める（[GameSetup.attackOpponentBenchFirstHalf]）か自分のベンチ側を攻めるか。後半は入れ替える
     */
    fun attacksRight(setup: GameSetup, side: Side, period: Int): Boolean {
        val leftBenchAttacksRight = setup.attackOpponentBenchFirstHalf != secondHalf(period)
        return (side == benchLeft(setup)) == leftBenchAttacksRight
    }

    /** 画面の左に出すチーム。ベンチごと入れ替えるときは、後半に左右を入れ替える（F-409） */
    fun leftPanel(setup: GameSetup, period: Int): Side {
        val left = benchLeft(setup)
        return if (setup.swapPanelsSecondHalf && secondHalf(period)) left.other else left
    }

    /** 画面のコートの左右どちらのゴールを、どのチームが攻めるか */
    fun attackerOfRightBasket(setup: GameSetup, period: Int): Side =
        if (attacksRight(setup, Side.A, period)) Side.A else Side.B
}
