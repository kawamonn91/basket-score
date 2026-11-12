package com.ttech.basketscore.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.graph
import com.ttech.basketscore.ui.common.Section

/**
 * 試合の画面。記録の画面（docs/design.md の 3.3）は 3 段目で作る。いまは試合の設定を確かめる表示だけ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(gameId: Long, onBack: () -> Unit) {
    val repo = LocalContext.current.graph.games
    val gameFlow = remember(gameId) { repo.observeGame(gameId) }
    val game by gameFlow.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val g = game
                    Text(if (g == null) "試合" else "${g.setup.teamA.name}  ${g.state.a.score} - ${g.state.b.score}  ${g.setup.teamB.name}")
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
            )
        },
    ) { padding ->
        val g = game ?: return@Scaffold
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 1200.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("記録の画面は準備中です（次の版で使えるようになります）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val h = g.setup.header
                Section("試合") {
                    Text(listOf(h.competition, h.date, h.startTime, h.venue, h.gameNo.takeIf { it.isNotBlank() }?.let { "No.$it" }.orEmpty())
                        .filter { it.isNotBlank() }.joinToString("　"))
                    Text("${g.setup.category.label}・${g.setup.detail.label}・" + if (g.setup.useClock) "ゲームクロックあり" else "ゲームクロックなし")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Side.entries.forEach { side ->
                        val team = g.setup.team(side)
                        Section("チーム $side　${team.name}（ベンチ${if (team.benchLeft) "左" else "右"}）", Modifier.weight(1f)) {
                            team.players.forEach { p ->
                                Text("#${p.number}　${p.name}" + (if (p.captain) "（CAP）" else "") + (if (p.starter) "　スターター" else ""))
                            }
                            team.headCoach?.let { Text("コーチ　${it.name}") }
                            team.assistantCoach?.let { Text("A.コーチ　${it.name}") }
                        }
                    }
                }
            }
        }
    }
}
