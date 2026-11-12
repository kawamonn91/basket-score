package com.ttech.basketscore.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import com.ttech.basketscore.data.GameRepository
import com.ttech.basketscore.data.TeamWithPlayers
import com.ttech.basketscore.game.DetailLevel
import com.ttech.basketscore.game.GameHeader
import com.ttech.basketscore.game.GameSetup
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
)

/** 試合の準備（docs/design.md の 3.2） */
class GameSetupViewModel(private val games: GameRepository) : ViewModel() {

    var step by mutableStateOf(0)

    // 1. 試合の情報
    var competition by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now().toString())
    var startTime by mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
    var venue by mutableStateOf("")
    var gameNo by mutableStateOf("")
    var crewChief by mutableStateOf("")
    var umpire1 by mutableStateOf("")
    var umpire2 by mutableStateOf("")
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
                EntryDraft(key = "p${p.id}", number = p.number, name = p.name, licenseNo = p.licenseNo, captain = p.captain, selected = index < max)
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
            .sortedBy { Jersey.sortKey(it.number) }
            .mapIndexed { index, e ->
                RosterPlayer(
                    id = "$side${index + 1}",
                    number = e.number,
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
                scorer = scorer.trim(),
                assistantScorer = assistantScorer.trim(),
                timer = timer.trim(),
                shotClockOperator = shotClockOperator.trim(),
            ),
            category = category,
            rules = category.defaultRules(),
            detail = detail,
            useClock = useClock,
            teamA = teamSetup(Side.A, a),
            teamB = teamSetup(Side.B, b),
        )
    }

    suspend fun create(teams: Map<Long, TeamWithPlayers>): Long? = buildSetup(teams)?.let { games.create(it) }
}
