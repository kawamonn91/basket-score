package com.ttech.basketscore.game

/** プレーの種別の選択肢（F-408）。チームごとの編集（F-207）は 1.1 */
object PlayTypes {
    val offense = listOf("セット", "速攻", "アーリー", "セカンドチャンス", "スローイン")
    val defense = listOf("マンツーマン", "ゾーン", "プレス")
    val shot = listOf("レイアップ", "ダンク", "ジャンプシュート", "フローター", "フック", "ティップ")
    val turnover = listOf(
        "パスミス", "キャッチミス", "トラベリング", "ダブルドリブル", "オフェンスファウル",
        "ラインクロス", "3 秒", "5 秒", "8 秒", "24 秒", "その他",
    )

    const val LAYUP = "レイアップ"
    const val DUNK = "ダンク"
}
