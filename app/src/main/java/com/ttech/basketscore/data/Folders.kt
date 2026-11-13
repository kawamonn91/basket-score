package com.ttech.basketscore.data

import java.time.Instant
import java.time.ZoneId

/** 大会のフォルダ 1 つと、中の試合の数・期間 */
data class FolderSummary(
    val folder: FolderEntity,
    val games: Int,
    /** 中の試合のいちばん早い日付・遅い日付（"2026-11-12"。試合が無ければ null） */
    val firstDate: String?,
    val lastDate: String?,
) {
    /** 一覧に出す期間（"2026-11-01〜11-03"） */
    val period: String? get() = Folders.period(firstDate, lastDate)
}

/** 大会ごとのフォルダで試合をまとめる（純粋な計算。単体テストで確かめる） */
object Folders {
    /** ホームで「すべての試合」を見ているとき */
    const val ALL = -1L

    /** ホームで「未分類」（どのフォルダにも入っていない試合）を見ているとき */
    const val UNFILED = 0L

    /** 見ているまとまり（[ALL]・[UNFILED]・フォルダの ID）の試合 */
    fun gamesIn(games: List<GameEntity>, view: Long): List<GameEntity> = when (view) {
        ALL -> games
        UNFILED -> games.filter { it.folderId == null }
        else -> games.filter { it.folderId == view }
    }

    /**
     * フォルダごとの試合の数と期間。新しい大会が上に来るように、中の試合の最後の日付（試合が無ければ作った日）の
     * 新しい順に並べる
     */
    fun summaries(folders: List<FolderEntity>, games: List<GameEntity>, zone: ZoneId = ZoneId.systemDefault()): List<FolderSummary> {
        val byFolder = games.filter { it.folderId != null }.groupBy { it.folderId }
        return folders.map { folder ->
            val inside = byFolder[folder.id].orEmpty()
            val dates = inside.map { it.date.trim() }.filter { it.isNotEmpty() }.sorted()
            FolderSummary(folder, inside.size, dates.firstOrNull(), dates.lastOrNull())
        }.sortedWith(
            compareByDescending<FolderSummary> { it.lastDate ?: dateOf(it.folder.createdAt, zone) }
                .thenByDescending { it.folder.createdAt },
        )
    }

    /** 期間の書き方: 1 日なら "2026-11-01"、同じ年なら "2026-11-01〜11-03"、年をまたげば両方とも年から */
    fun period(first: String?, last: String?): String? {
        if (first == null || last == null) return first ?: last
        if (first == last) return first
        val sameYear = first.length == 10 && last.length == 10 && first.take(5) == last.take(5)
        return if (sameYear) "$first〜${last.drop(5)}" else "$first〜$last"
    }

    /** フォルダの名前の問題（無ければ null）。空と、ほかのフォルダと同じ名前は受け付けない */
    fun nameProblem(name: String, folders: List<FolderEntity>, exceptId: Long? = null): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "大会の名前を入れてください"
            folders.any { it.id != exceptId && it.name.trim() == trimmed } -> "同じ名前の大会があります"
            else -> null
        }
    }

    /** フォルダの名前（[ALL]・[UNFILED] は決まった名前） */
    fun title(view: Long, folders: List<FolderEntity>): String = when (view) {
        ALL -> "すべての試合"
        UNFILED -> "未分類"
        else -> folders.firstOrNull { it.id == view }?.name ?: "すべての試合"
    }

    private fun dateOf(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()
}
