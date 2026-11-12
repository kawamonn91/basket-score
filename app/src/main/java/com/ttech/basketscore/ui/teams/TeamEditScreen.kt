package com.ttech.basketscore.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttech.basketscore.data.PlayerEntity
import com.ttech.basketscore.data.TeamEntity
import com.ttech.basketscore.data.TeamRepository
import com.ttech.basketscore.game.RosterRules
import com.ttech.basketscore.graph
import com.ttech.basketscore.ui.common.ColorPicker
import com.ttech.basketscore.ui.common.Problems
import com.ttech.basketscore.ui.common.Section
import kotlinx.coroutines.launch

/** 名簿の 1 行（編集中） */
data class PlayerDraft(val number: String = "", val name: String = "", val licenseNo: String = "", val captain: Boolean = false)

class TeamEditViewModel(private val repo: TeamRepository, private val teamId: Long) : ViewModel() {
    var loaded by mutableStateOf(teamId == 0L)
        private set
    var name by mutableStateOf("")
    var shortName by mutableStateOf("")
    var color by mutableStateOf<Long?>(null)
    var headCoach by mutableStateOf("")
    var headCoachLicense by mutableStateOf("")
    var assistantCoach by mutableStateOf("")
    var assistantCoachLicense by mutableStateOf("")
    val players = mutableStateListOf<PlayerDraft>()
    private var createdAt = System.currentTimeMillis()

    suspend fun load() {
        if (teamId == 0L || loaded) return
        val team = repo.team(teamId) ?: return
        name = team.team.name
        shortName = team.team.shortName
        color = team.team.color
        headCoach = team.team.headCoachName
        headCoachLicense = team.team.headCoachLicense
        assistantCoach = team.team.assistantCoachName
        assistantCoachLicense = team.team.assistantCoachLicense
        createdAt = team.team.createdAt
        players.clear()
        players.addAll(team.sortedPlayers.map { PlayerDraft(it.number, it.name, it.licenseNo, it.captain) })
        loaded = true
    }

    /** 空の行（背番号も名前も無い）は保存しない */
    private val filledPlayers get() = players.filter { it.number.isNotBlank() || it.name.isNotBlank() }

    fun problems(): List<String> = buildList {
        if (name.isBlank()) add("チーム名を入れてください")
        addAll(RosterRules.teamProblems(filledPlayers.map { it.number.trim() }, filledPlayers.count { it.captain }))
    }

    suspend fun save(): Long = repo.save(
        TeamEntity(
            id = teamId,
            name = name.trim(),
            shortName = shortName.trim(),
            color = color,
            headCoachName = headCoach.trim(),
            headCoachLicense = headCoachLicense.trim(),
            assistantCoachName = assistantCoach.trim(),
            assistantCoachLicense = assistantCoachLicense.trim(),
            createdAt = createdAt,
        ),
        filledPlayers.map { PlayerEntity(teamId = teamId, number = it.number.trim(), name = it.name.trim(), licenseNo = it.licenseNo.trim(), captain = it.captain) },
    )
}

/** チームの編集（F-101〜F-103） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamEditScreen(teamId: Long, onDone: () -> Unit) {
    val repo = LocalContext.current.graph.teams
    val vm = viewModel(key = "team-$teamId") { TeamEditViewModel(repo, teamId) }
    val scope = rememberCoroutineScope()
    var problems by androidx.compose.runtime.remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(teamId) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (teamId == 0L) "チームを作る" else "チームを直す") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
                actions = {
                    Button(
                        onClick = {
                            problems = vm.problems()
                            if (problems.isEmpty()) scope.launch { vm.save(); onDone() }
                        },
                        modifier = Modifier.padding(end = 12.dp),
                    ) { Text("保存") }
                },
            )
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Problems(problems) }
                item {
                    Section("チーム") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("チーム名") }, singleLine = true, modifier = Modifier.weight(2f))
                            OutlinedTextField(vm.shortName, { vm.shortName = it }, label = { Text("略称（任意）") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Text("チームの色（ユニフォームの色）")
                        ColorPicker(vm.color) { vm.color = it }
                    }
                }
                item {
                    Section("コーチ") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(vm.headCoach, { vm.headCoach = it }, label = { Text("ヘッドコーチ") }, singleLine = true, modifier = Modifier.weight(2f))
                            OutlinedTextField(vm.headCoachLicense, { vm.headCoachLicense = it }, label = { Text("登録番号（下3桁）") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(vm.assistantCoach, { vm.assistantCoach = it }, label = { Text("アシスタントコーチ") }, singleLine = true, modifier = Modifier.weight(2f))
                            OutlinedTextField(vm.assistantCoachLicense, { vm.assistantCoachLicense = it }, label = { Text("登録番号（下3桁）") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
                item { Text("選手（背番号・氏名・登録番号の下3桁・キャプテン）") }
                itemsIndexed(vm.players) { index, p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            p.number, { vm.players[index] = p.copy(number = it.filter(Char::isDigit).take(2)) },
                            label = { Text("背番号") }, singleLine = true, modifier = Modifier.width(110.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(p.name, { vm.players[index] = p.copy(name = it) }, label = { Text("氏名") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            p.licenseNo, { vm.players[index] = p.copy(licenseNo = it.filter(Char::isDigit).take(3)) },
                            label = { Text("登録番号") }, singleLine = true, modifier = Modifier.width(120.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Checkbox(checked = p.captain, onCheckedChange = { vm.players[index] = p.copy(captain = it) })
                        Text("CAP")
                        IconButton(onClick = { vm.players.removeAt(index) }) { Icon(Icons.Filled.Delete, contentDescription = "この行を消す") }
                    }
                }
                item {
                    OutlinedButton(onClick = { vm.players.add(PlayerDraft()) }, modifier = Modifier.padding(bottom = 48.dp)) {
                        Icon(Icons.Filled.Add, null)
                        Text("選手を足す", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
