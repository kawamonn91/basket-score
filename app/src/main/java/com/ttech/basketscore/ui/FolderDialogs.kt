package com.ttech.basketscore.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ttech.basketscore.data.FolderEntity
import com.ttech.basketscore.data.Folders
import com.ttech.basketscore.data.GameEntity

/** 大会のフォルダを作る・名前を変える（F-1007） */
@Composable
fun FolderNameDialog(
    title: String,
    initial: String,
    folders: List<FolderEntity>,
    exceptId: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var tried by remember { mutableStateOf(false) }
    val problem = Folders.nameProblem(name, folders, exceptId)
    val showProblem = problem != null && (tried || name.isNotBlank())
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("大会の名前") },
                singleLine = true,
                isError = showProblem,
                supportingText = { Text(if (showProblem) problem.orEmpty() else "例: 2026 秋季リーグ戦、新人戦 地区予選") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                tried = true
                if (problem == null) onConfirm(name.trim())
            }) { Text("決める") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

/** 試合の 1 行の書き方（日付とスコア） */
fun gameLine(g: GameEntity): String = "${g.date}　${g.teamAName} ${g.scoreA} - ${g.scoreB} ${g.teamBName}"

/** 試合を選ぶチェックの一覧（まとめて移す・まとめて消す） */
@Composable
fun GameChecklist(
    games: List<GameEntity>,
    selected: Set<Long>,
    onChange: (Set<Long>) -> Unit,
    allLabel: String = "すべて選ぶ",
    enabled: (GameEntity) -> Boolean = { true },
    note: (GameEntity) -> String = { "" },
) {
    val selectable = games.filter(enabled)
    Column {
        val all = selectable.isNotEmpty() && selected.size == selectable.size
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().toggleable(value = all, role = Role.Checkbox) { on ->
                onChange(if (on) selectable.map { it.id }.toSet() else emptySet())
            },
        ) {
            Checkbox(checked = all, onCheckedChange = null)
            Text(allLabel)
        }
        HorizontalDivider()
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(games, key = { it.id }) { g ->
                val on = enabled(g)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().toggleable(value = g.id in selected, enabled = on, role = Role.Checkbox) { checked ->
                        onChange(if (checked) selected + g.id else selected - g.id)
                    },
                ) {
                    Checkbox(checked = g.id in selected, enabled = on, onCheckedChange = null)
                    Text(
                        gameLine(g) + note(g),
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 移す先の「新しい大会を作る」 */
private const val NEW_FOLDER = -2L

/**
 * 試合を大会のフォルダに移す（F-1007）。[preselected] があれば、試合を選ぶ段を飛ばして移す先から聞く。
 * [onMove] の folderId は null なら未分類へ戻す
 */
@Composable
fun MoveGamesDialog(
    games: List<GameEntity>,
    folders: List<FolderEntity>,
    preselected: Set<Long>?,
    onDismiss: () -> Unit,
    onMove: (ids: List<Long>, folderId: Long?) -> Unit,
    onCreateAndMove: (ids: List<Long>, name: String) -> Unit,
) {
    var selected by remember { mutableStateOf(preselected ?: emptySet()) }
    var choosing by remember { mutableStateOf(preselected != null) }
    var target by remember { mutableStateOf<Long?>(null) }
    var newName by remember { mutableStateOf("") }
    val chosen = games.filter { it.id in selected }
    // 選んだ試合がみな同じ所にあれば、そこを「今の場所」として示す
    val here = chosen.map { it.folderId ?: Folders.UNFILED }.toSet().singleOrNull()
    val nameProblem = Folders.nameProblem(newName, folders)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!choosing) "移す試合を選ぶ" else "大会に移す") },
        text = {
            if (!choosing) {
                GameChecklist(games, selected, { selected = it })
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    item {
                        Text(
                            if (chosen.size == 1) gameLine(chosen[0]) else "${chosen.size} 試合",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    item { Destination(Icons.Outlined.Inbox, "未分類（フォルダから出す）", here == Folders.UNFILED, target == Folders.UNFILED) { target = Folders.UNFILED } }
                    items(folders, key = { it.id }) { f ->
                        Destination(Icons.Outlined.Folder, f.name, here == f.id, target == f.id) { target = f.id }
                    }
                    item {
                        Destination(Icons.Outlined.CreateNewFolder, "新しい大会を作って移す", false, target == NEW_FOLDER) { target = NEW_FOLDER }
                        if (target == NEW_FOLDER) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("大会の名前") },
                                singleLine = true,
                                isError = newName.isNotBlank() && nameProblem != null,
                                supportingText = { if (newName.isNotBlank() && nameProblem != null) Text(nameProblem) },
                                modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!choosing) {
                TextButton(onClick = { choosing = true }, enabled = selected.isNotEmpty()) { Text("次へ") }
            } else {
                val ready = when (target) {
                    null -> false
                    NEW_FOLDER -> nameProblem == null
                    else -> target != here
                }
                TextButton(enabled = ready, onClick = {
                    val ids = chosen.map { it.id }
                    when (val t = target) {
                        NEW_FOLDER -> onCreateAndMove(ids, newName.trim())
                        Folders.UNFILED -> onMove(ids, null)
                        else -> onMove(ids, t)
                    }
                }) { Text("移す") }
            }
        },
        dismissButton = {
            if (choosing && preselected == null) {
                TextButton(onClick = { choosing = false }) { Text("戻る") }
            } else {
                TextButton(onClick = onDismiss) { Text("やめる") }
            }
        },
    )
}

@Composable
private fun Destination(icon: ImageVector, name: String, here: Boolean, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = !here, role = Role.RadioButton, onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = !here)
        Icon(icon, contentDescription = null, modifier = Modifier.padding(horizontal = 8.dp).size(20.dp))
        Text(
            name + if (here) "（今の場所）" else "",
            color = if (here) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
