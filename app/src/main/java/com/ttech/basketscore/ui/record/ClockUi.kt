package com.ttech.basketscore.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttech.basketscore.game.ClockFormat
import com.ttech.basketscore.rules.LastTwoMinutes
import com.ttech.basketscore.rules.Period

/** 得点板の時計（押すと時刻を直す）と、スタート / ストップの大きなボタン（F-501） */
@Composable
fun ClockPanel(
    period: Int,
    clockMillis: () -> Long,
    running: Boolean,
    enabled: Boolean,
    toggleEnabled: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val ms = clockMillis()
    val l2m = period >= 1 && LastTwoMinutes.contains(period, ms)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.width(136.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onEdit)
                .semantics { contentDescription = "時計 ${ClockFormat.board(ms)}（押すと直す）" }.padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                ClockFormat.board(ms),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (ms < 60_000 && period >= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (l2m) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                    Text("L2M", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
        }
        val atZero = ms <= 0
        Button(
            onClick = onToggle,
            enabled = toggleEnabled,
            colors = if (running) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            else ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.width(132.dp).height(56.dp),
        ) {
            Icon(
                when {
                    running -> Icons.Filled.Pause
                    atZero -> Icons.Filled.SkipNext
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = null,
            )
            Text(
                when {
                    running -> " ストップ"
                    atZero -> " 次へ"
                    else -> " スタート"
                },
                fontSize = 17.sp,
                maxLines = 1,
            )
        }
    }
}

/** 時刻を ± で直す（0〜[max]） */
@Composable
fun ClockAdjuster(value: Long, max: Long, onChange: (Long) -> Unit) {
    val steps = listOf(60_000L to "1分", 10_000L to "10秒", 1_000L to "1秒", 100L to "0.1秒")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(ClockFormat.board(value), fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        listOf(1L, -1L).forEach { sign ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.forEach { (step, label) ->
                    FilledTonalButton(
                        onClick = { onChange((value + sign * step).coerceIn(0, max)) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text((if (sign > 0) "+" else "−") + label, maxLines = 1) }
                }
            }
        }
    }
}

/**
 * 時計を直す（F-501）。終わったピリオドに時間を足すときは、そのピリオドの再開になることを知らせる（F-508）
 *
 * @param ended ピリオドが終わっている
 * @param resumable 終わったピリオドを再開できる（終わりのあとに、次のピリオドの初めしか記録が無い）
 */
@Composable
fun ClockEditDialog(
    period: Int,
    current: Long,
    max: Long,
    ended: Boolean,
    resumable: Boolean,
    onDismiss: () -> Unit,
    onSet: (Long) -> Unit,
    onResume: (Long) -> Unit,
) {
    var value by remember { mutableLongStateOf(current) }
    val label = Period.label(period)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("時計を直す（$label）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    ended && resumable -> Text(
                        "$label は終わっています。時間を足すと、$label の終わりの記録を消して $label をやり直します（続きから記録します）",
                        color = MaterialTheme.colorScheme.error,
                    )
                    ended -> Text(
                        "$label のあとに記録があるため、ここでは直せません。記録の一覧から直してください",
                        color = MaterialTheme.colorScheme.error,
                    )
                    current <= 0 -> Text("時間を足すと、$label の続きを計ります", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!ended || resumable) {
                    ClockAdjuster(value, max) { value = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { value = max }) { Text("${ClockFormat.board(max)} に戻す") }
                        OutlinedButton(onClick = { value = 0 }) { Text("0.0 にする") }
                    }
                }
            }
        },
        confirmButton = {
            when {
                ended && resumable -> TextButton(onClick = { onResume(value) }, enabled = value > 0) { Text("$label をやり直す") }
                ended -> Unit
                else -> TextButton(onClick = { onSet(value) }, enabled = value != current) { Text("直す") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}
