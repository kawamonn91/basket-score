package com.ttech.basketscore.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import com.ttech.basketscore.data.FolderEntity
import com.ttech.basketscore.data.GameRepository
import com.ttech.basketscore.data.TeamRepository
import com.ttech.basketscore.data.TeamWithPlayers
import com.ttech.basketscore.game.DetailLevel
import com.ttech.basketscore.game.GameHeader
import com.ttech.basketscore.game.GameSetup
import com.ttech.basketscore.game.GameType
import com.ttech.basketscore.game.Jersey
import com.ttech.basketscore.game.RosterPlayer
import com.ttech.basketscore.game.RosterRules
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.game.Staff
import com.ttech.basketscore.game.TeamSetup
import com.ttech.basketscore.rules.Category
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 出場登録の 1 行（名簿の選手か、その試合だけの選手） */
data class EntryDraft(
    val key: String,
    val number: String,
    val name: String,
    val licenseNo: String = "",
    val captain: Boolean = false,
    val selected: Boolean = true,
    val starter: Boolean = false,
    val temporary: Boolean = false,
    /** 練習用の背番号（空なら公式用と同じ） */
    val practiceNumber: String = "",
) {
    /** 名簿の選手の ID（その試合だけの選手は null） */
    val playerId: Long? get() = key.removePrefix("p").toLongOrNull()?.takeIf { key.startsWith("p") }
}

/** 試合の準備（docs/design.md の 3.2） */
class GameSetupViewModel(
    private val games: GameRepository,
    private val teams: TeamRepository,
    /** ホームで開いていた大会のフォルダ（はじめから選んでおく） */
    private val initialFolderId: Long?,
) : ViewModel() {

    var step by mutableStateOf(0)

    // 1. 試合の情報
    /** 試合を入れる大会のフォルダ（null なら未分類） */
    var folderId by mutableStateOf<Long?>(null)
        private set
    private var initialFolderApplied = false
    var competition by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now().toString())
    var startTime by mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
    var venue by mutableStateOf("")
    var gameNo by mutableStateOf("")
    var crewChief by mutableStateOf("")
    var umpire1 by mutableStateOf("")
    var umpire2 by mutableStateOf("")
    var umpire3 by mutableStateOf("")
    var gameType by mutableStateOf(GameType.PRACTICE)
    var memo by mutableStateOf("")
    var scorer by mutableStateOf("")
    var assistantScorer by mutableStateOf("")
    var timer by mutableStateOf("")
    var shotClockOperator by mutableStateOf("")

    // 2. チーム
    var teamAId by mutableStateOf<Long?>(null)
        private set
    var teamBId by mutableStateOf<Long?>(null)
        private set
    /** チーム A のベンチが記録係から見て左か */
    var benchALeft by mutableStateOf(true)
    /** 前半に、相手のベンチ側のゴールを攻めるか */
    var attackOpponentBench by mutableStateOf(true)
    /** 練習用の背番号を使うか */
    var usePracticeNumbers by mutableStateOf(false)

    // 3. 出場登録とスターター
    val entriesA = mutableStateListOf<EntryDraft>()
    val entriesB = mutableStateListOf<EntryDraft>()
    private var temporaryCount = 0

    // 4. 規則と記録の詳しさ
    var category by mutableStateOf(Category.GENERAL)
    var useClock by mutableStateOf(true)
    var detail by mutableStateOf(DetailLevel.OFFICIAL)
    var statsA by mutableStateOf(true)
    var statsB by mutableStateOf(true)

    /** ホームで開いていた大会を選んでおく（フォルダの一覧を読んだあとに 1 度だけ） */
    fun applyInitialFolder(folders: List<FolderEntity>) {
        if (initialFolderApplied) return
        initialFolderApplied = true
        if (initialFolderId != null && folders.any { it.id == initialFolderId }) chooseFolder(initialFolderId, folders)
    }

    /**
     * 大会のフォルダを選ぶ。大会名が空か、前に選んでいたフォルダの名前のままなら、新しいフォルダの名前にする
     * （自分で書き換えた大会名は変えない）
     */
    fun chooseFolder(id: Long?, folders: List<FolderEntity>) {
        val before = folders.firstOrNull { it.id == folderId }?.name
        folderId = id
        val after = folders.firstOrNull { it.id == id }?.name
        if (competition.isBlank() || competition == before) competition = after ?: ""
    }

    fun entries(side: Side): SnapshotStateList<EntryDraft> = if (side == Side.A) entriesA else entriesB

    fun teamId(side: Side): Long? = if (side == Side.A) teamAId else teamBId

    /** チームを選ぶと、名簿の選手を出場登録の候補に並べる（18 人を超える分は外しておく） */
    fun chooseTeam(side: Side, team: TeamWithPlayers) {
        if (teamId(side) == team.team.id) return
        if (side == Side.A) teamAId = team.team.id else teamBId = team.team.id
        val list = entries(side)
        list.clear()
        val max = category.defaultRules().maxPlayers
        list.addAll(
            team.sortedPlayers.mapIndexed { index, p ->
                EntryDraft(
                    key = "p${p.id}",
                    number = p.number,
                    name = p.name,
                    licenseNo = p.licenseNo,
                    captain = p.captain,
                    selected = index < max,
                    starter = p.defaultStarter,
                    practiceNumber = p.practiceNumber,
                )
            },
        )
    }

    fun toggleSelected(side: Side, index: Int) {
        val list = entries(side)
        val e = list[index]
        list[index] = e.copy(selected = !e.selected, starter = if (e.selected) false else e.starter)
    }

    fun toggleStarter(side: Side, index: Int) {
        val list = entries(side)
        val e = list[index]
        list[index] = e.copy(starter = !e.starter, selected = true)
    }

    /** 表に出す背番号（練習用を使う試合なら練習用） */
    fun shownNumber(e: EntryDraft): String = RosterRules.numberFor(e.number, e.practiceNumber, usePracticeNumbers)

    /** 今のスターターを、そのチームの既定のスターターにする（F-109） */
    suspend fun saveDefaultStarters(side: Side) {
        val teamId = teamId(side) ?: return
        teams.setDefaultStarters(teamId, entries(side).filter { it.starter }.mapNotNull { it.playerId })
    }

    fun addTemporary(side: Side, number: String, name: String) {
        temporaryCount++
        entries(side) += EntryDraft(key = "t$temporaryCount", number = number.trim(), name = name.trim(), temporary = true)
    }

    fun problems(step: Int, teams: Map<Long, TeamWithPlayers>): List<String> = when (step) {
        1 -> buildList {
            if (teamAId == null || teamBId == null) add("チーム A と B を選んでください")
            else if (teamAId == teamBId) add("チーム A と B に、違うチームを選んでください")
            listOfNotNull(teamAId, teamBId).forEach { id -> if (teams[id] == null) add("選んだチームが見つかりません") }
        }
        2 -> Side.entries.flatMap { side ->
            val label = "チーム $side"
            RosterRules.entryProblems(rosterPlayers(side), category.defaultRules()).map { "$label：$it" }
        }
        else -> emptyList()
    }

    private fun rosterPlayers(side: Side): List<RosterPlayer> =
        entries(side).filter { it.selected }
            .sortedBy { Jersey.sortKey(shownNumber(it)) }
            .mapIndexed { index, e ->
                RosterPlayer(
                    id = "$side${index + 1}",
                    number = shownNumber(e),
                    name = e.name,
                    licenseNo = e.licenseNo,
                    captain = e.captain,
                    starter = e.starter,
                )
            }

    private fun teamSetup(side: Side, team: TeamWithPlayers): TeamSetup = TeamSetup(
        name = team.team.name,
        shortName = team.team.shortName,
        color = team.team.color,
        benchLeft = if (side == Side.A) benchALeft else !benchALeft,
        headCoach = team.team.headCoachName.takeIf { it.isNotBlank() }?.let { Staff(it, team.team.headCoachLicense) },
        assistantCoach = team.team.assistantCoachName.takeIf { it.isNotBlank() }?.let { Staff(it, team.team.assistantCoachLicense) },
        players = rosterPlayers(side),
        recordStats = detail != DetailLevel.OFFICIAL && (if (side == Side.A) statsA else statsB),
    )

    fun buildSetup(teams: Map<Long, TeamWithPlayers>): GameSetup? {
        val a = teams[teamAId ?: return null] ?: return null
        val b = teams[teamBId ?: return null] ?: return null
        return GameSetup(
            header = GameHeader(
                competition = competition.trim(),
                date = date.trim(),
                startTime = startTime.trim(),
                venue = venue.trim(),
                gameNo = gameNo.trim(),
                crewChief = crewChief.trim(),
                umpire1 = umpire1.trim(),
                umpire2 = umpire2.trim(),
                umpire3 = umpire3.trim(),
                scorer = scorer.trim(),
                assistantScorer = assistantScorer.trim(),
                timer = timer.trim(),
                shotClockOperator = shotClockOperator.trim(),
                gameType = gameType,
                memo = memo.trim(),
            ),
            category = category,
            rules = category.defaultRules(),
            detail = detail,
            useClock = useClock,
            usePracticeNumbers = usePracticeNumbers,
            attackOpponentBenchFirstHalf = attackOpponentBench,
            teamA = teamSetup(Side.A, a),
            teamB = teamSetup(Side.B, b),
        )
    }

    /** 試合を作る。選んだフォルダが消えていたら未分類にする */
    suspend fun create(teams: Map<Long, TeamWithPlayers>, folders: List<FolderEntity>): Long? =
        buildSetup(teams)?.let { games.create(it, folderId?.takeIf { id -> folders.any { f -> f.id == id } }) }
}
