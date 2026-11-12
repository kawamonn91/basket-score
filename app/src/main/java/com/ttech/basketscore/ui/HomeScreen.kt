package com.ttech.basketscore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttech.basketscore.data.GameEntity
import com.ttech.basketscore.data.GameStatus
import com.ttech.basketscore.graph

/** ホーム: 新しい試合・チーム・試合の一覧（docs/design.md の 3.1） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNewGame: () -> Unit, onTeams: () -> Unit, onOpenGame: (Long) -> Unit) {
    val context = LocalContext.current
    val gamesFlow = remember { context.graph.games.observeGames() }
    val games by gamesFlow.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(topBar = { TopAppBar(title = { Text("Basket Score") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                    Button(onClick = onNewGame) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("新しい試合", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onTeams) {
                        Icon(Icons.Outlined.Groups, contentDescription = null)
                        Text("チーム", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text("試合", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                val list = games
                when {
                    list == null -> Unit
                    list.isEmpty() -> Text(
                        "まだ試合がありません。「チーム」で名簿を作り、「新しい試合」から記録を始めます",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { game ->
                            GameRow(game, onClick = { onOpenGame(game.id) })
                            HorizontalDivider()
                        }
                        item { Spacer(Modifier.padding(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameRow(game: GameEntity, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(Icons.Outlined.SportsBasketball, contentDescription = null) },
        headlineContent = { Text("${game.teamAName}  ${game.scoreA} - ${game.scoreB}  ${game.teamBName}") },
        supportingContent = {
            Text(listOf(game.date, game.competition).filter { it.isNotBlank() }.joinToString("  "))
        },
        trailingContent = {
            Text(
                game.status.label,
                color = if (game.status == GameStatus.LIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
