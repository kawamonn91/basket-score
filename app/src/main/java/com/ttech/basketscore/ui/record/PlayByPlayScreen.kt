package com.ttech.basketscore.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttech.basketscore.data.StoredEvent
import com.ttech.basketscore.game.EventText
import com.ttech.basketscore.game.Shot
import com.ttech.basketscore.game.Side
import com.ttech.basketscore.graph
import com.ttech.basketscore.rules.Period

/** 記録の一覧（Play by Play。F-614 の一覧と、F-312 の修正・削除）。「？」の記録と、食い違いのある記録は赤字 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayByPlayScreen(gameId: Long, onBack: () -> Unit) {
    val graph = LocalContext.current.graph
    val vm = viewModel(key = "record-$gameId") { RecordViewModel(graph.games, gameId) }
    val game by vm.game.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<StoredEvent?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("記録の一覧（${game?.events?.size ?: 0} 件）") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
            )
        },
    ) { padding ->
        val g = game ?: return@Scaffold
        // 出来事ごとの、その時点の得点
        val scores = remember(g.events) {
            var a = 0
            var b = 0
            g.events.map { stored ->
                val e = stored.event
                if (e is Shot && e.made) if (e.side == Side.A) a += e.kind.points else b += e.kind.points
                a to b
            }
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
                itemsIndexed(g.events, key = { _, it -> it.entity.id }) { index, stored ->
                    val e = stored.event
                    val bad = EventText.hasUnknownPlayer(e) || g.state.warnings.containsKey(index)
                    val side = EventText.sideOf(e)
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = stored }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}", modifier = Modifier.width(48.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(com.ttech.basketscore.game.ClockFormat.at(e.period, e.clockMillis), modifier = Modifier.width(110.dp))
                        Box(Modifier.width(8.dp).padding(end = 4.dp).background(side?.let { teamColor(g.setup, it) } ?: MaterialTheme.colorScheme.surface))
                        Text(
                            EventText.describe(g.setup, e) + if (g.state.warnings.containsKey(index)) "（記録が合っていません）" else "",
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text("${scores[index].first} - ${scores[index].second}", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
        editing?.let { stored ->
            EditEventDialog(
                g.setup, stored,
                onDismiss = { editing = null },
                onSave = {
                    vm.update(stored, it)
                    editing = null
                },
                onDelete = {
                    vm.delete(stored)
                    editing = null
                },
            )
        }
    }
}
