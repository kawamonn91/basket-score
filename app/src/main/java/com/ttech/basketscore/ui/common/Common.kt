package com.ttech.basketscore.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** チームの色の候補（ユニフォームの色に近いものを選ぶ） */
val TeamColors: List<Long> = listOf(
    0xFF1E2A44, 0xFF1F5FD6, 0xFF0F8A8A, 0xFF2E7D32, 0xFF7CB342,
    0xFFF2C12E, 0xFFF28C28, 0xFFD64545, 0xFFB0306A, 0xFF6A3DB8,
    0xFF6D4C41, 0xFF616161, 0xFF000000, 0xFFFFFFFF,
)

/** ARGB の Long を Compose の色にする（下位 32 ビットが ARGB） */
fun Long.toColor(): Color = Color(this.toInt())

/** 見出しのついた枠 */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** チームの色を選ぶ */
@Composable
fun ColorPicker(selected: Long?, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TeamColors.forEach { color ->
            val chosen = color == selected
            Box(
                Modifier
                    .size(36.dp)
                    .background(color.toColor(), CircleShape)
                    .border(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                    .clickable { onSelect(color) },
            )
        }
    }
}

/** 問題の一覧（赤字） */
@Composable
fun Problems(problems: List<String>) {
    problems.forEach { Text("・$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}
