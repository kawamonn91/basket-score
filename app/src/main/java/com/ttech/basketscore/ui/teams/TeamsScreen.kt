package com.ttech.basketscore.ui.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttech.basketscore.data.TeamWithPlayers
import com.ttech.basketscore.graph
import com.ttech.basketscore.ui.common.toColor
import kotlinx.coroutines.launch

/** チームの一覧（F-101・F-105） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, onNew: () -> Unit) {
    val repo = LocalContext.current.graph.teams
    val teamsFlow = remember { repo.observeTeams() }
    val teams by teamsFlow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<TeamWithPlayers?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("チーム") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("チームを作る") })
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 960.dp).fillMaxWidth()) {
                val list = teams
                if (list != null && list.isEmpty()) {
                    Text(
                        "まだチームがありません。「チームを作る」から、自分のチームと相手のチームの名簿を作ります",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list.orEmpty(), key = { it.team.id }) { team ->
                        TeamRow(
                            team,
                            onClick = { onEdit(team.team.id) },
                            onCopy = { scope.launch { repo.copy(team.team.id) } },
                            onDelete = { deleting = team },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    deleting?.let { team ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("「${team.team.name}」を消しますか") },
            text = { Text("名簿も消えます。これまでの試合の記録は消えません（試合ごとに名簿の写しを持っています）") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.delete(team.team) }
                    deleting = null
                }) { Text("消す") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("やめる") } },
        )
    }
}

@Composable
private fun TeamRow(team: TeamWithPlayers, onClick: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = {
            Box(Modifier.size(28.dp).background((team.team.color ?: 0xFF9E9E9E).toColor(), CircleShape))
        },
        headlineContent = { Text(team.team.name) },
        supportingContent = { Text("選手 ${team.players.size} 人" + team.team.headCoachName.takeIf { it.isNotBlank() }?.let { "・コーチ $it" }.orEmpty()) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "メニュー") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("コピーする") }, onClick = { menu = false; onCopy() })
                    DropdownMenuItem(text = { Text("消す") }, onClick = { menu = false; onDelete() })
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
