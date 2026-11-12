package com.ttech.basketscore.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ttech.basketscore.game.Jersey

/** 背番号の表の並び（0・00・1〜99） */
val ALL_JERSEY_NUMBERS: List<String> = listOf("0", "00") + (1..99).map { it.toString() }

/**
 * 0〜99（と 00）の表から背番号を押して選ぶ（F-108・F-317）。[used] の番号は選べない。
 * [single] なら 1 つ選んだところで決まる
 */
@Composable
fun NumberGridDialog(
    title: String,
    used: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    single: Boolean = false,
) {
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!single) {
                    Text(
                        "押した番号の選手を作ります（名前はあとから入れられます）。選んだ数: ${selected.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ALL_JERSEY_NUMBERS.forEach { n ->
                        val taken = n in used
                        val chosen = n in selected
                        val onClick: () -> Unit = {
                            when {
                                single -> onConfirm(listOf(n))
                                chosen -> selected.remove(n)
                                else -> selected.add(n)
                            }
                        }
                        if (chosen) {
                            FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(width = 64.dp, height = 48.dp)) { Text(n, maxLines = 1) }
                        } else {
                            OutlinedButton(onClick = onClick, enabled = !taken, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(width = 64.dp, height = 48.dp)) { Text(n, maxLines = 1) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!single) {
                TextButton(onClick = { onConfirm(selected.sortedBy(Jersey::sortKey)) }, enabled = selected.isNotEmpty()) { Text("足す") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}
