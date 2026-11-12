package com.ttech.basketscore.ui.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ttech.basketscore.data.LoadedGame
import com.ttech.basketscore.data.StoredEvent
import com.ttech.basketscore.graph
import com.ttech.basketscore.sheet.Scoresheet
import com.ttech.basketscore.sheet.ScoresheetPdf
import com.ttech.basketscore.sheet.ScoresheetRenderer
import com.ttech.basketscore.sheet.SheetHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** スコアシートの表示（F-905）。欄を押すと、その記録を直せる（F-316）。PDF の共有と印刷（F-901・F-904） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoresheetScreen(gameId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val vm = viewModel(key = "record-$gameId") { RecordViewModel(graph.games, gameId) }
    val game by vm.game.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<StoredEvent?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun pdf(g: LoadedGame): File = withContext(Dispatchers.IO) {
        val file = ScoresheetPdf.file(context, "scoresheet-${g.entity.id}.pdf")
        ScoresheetPdf.write(Scoresheet.build(g.setup, g.state), file)
        file
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("スコアシート") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") } },
                actions = {
                    val g = game
                    IconButton(enabled = g != null && !busy, onClick = {
                        g ?: return@IconButton
                        busy = true
                        scope.launch {
                            val file = pdf(g)
                            context.startActivity(ScoresheetPdf.shareIntent(context, file, "スコアシート ${g.setup.teamA.name} vs ${g.setup.teamB.name}"))
                            busy = false
                        }
                    }) { Icon(Icons.Filled.Share, contentDescription = "PDF を共有") }
                    IconButton(enabled = g != null && !busy, onClick = {
                        g ?: return@IconButton
                        busy = true
                        scope.launch {
                            ScoresheetPdf.print(context, pdf(g), "scoresheet-${g.entity.id}")
                            busy = false
                        }
                    }) { Icon(Icons.Filled.Print, contentDescription = "印刷") }
                },
            )
        },
    ) { padding ->
        val g = game ?: return@Scaffold
        val sheet = remember(g.setup, g.state) { Scoresheet.build(g.setup, g.state) }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text(
                    "欄を押すと、その記録を直せます（ランニングスコア・ファウル・タイムアウト）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sheet.pages > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        (0 until sheet.pages).forEach { p ->
                            FilterChip(selected = page == p, onClick = { page = p }, label = { Text("${p + 1} 枚目") })
                        }
                    }
                }
                SheetCanvas(sheet, page.coerceAtMost(sheet.pages - 1)) { index -> editing = g.events.getOrNull(index) }
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

/** スコアシートを、画面の幅に合わせて描く（PDF と同じ描き方） */
@Composable
private fun SheetCanvas(sheet: Scoresheet, page: Int, onTapEvent: (Int) -> Unit) {
    val hits = remember { mutableListOf<SheetHit>() }
    var scale by remember { mutableFloatStateOf(1f) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ScoresheetRenderer.WIDTH / ScoresheetRenderer.HEIGHT)
            .pointerInput(sheet, page) {
                detectTapGestures { offset ->
                    val x = offset.x / scale
                    val y = offset.y / scale
                    hits.lastOrNull { it.rect.contains(x, y) && it.eventIndex >= 0 }?.let { onTapEvent(it.eventIndex) }
                }
            },
    ) {
        scale = size.width / ScoresheetRenderer.WIDTH
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(scale, scale)
            val drawn = ScoresheetRenderer.draw(native, sheet, page)
            native.restore()
            hits.clear()
            hits.addAll(drawn)
        }
    }
}
