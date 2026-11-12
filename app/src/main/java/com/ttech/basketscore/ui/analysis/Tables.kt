package com.ttech.basketscore.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 表の 1 行 */
data class TableRow(val cells: List<String>, val bold: Boolean = false, val color: Color? = null)

/**
 * 横にスクロールできる表。[widths] は列ごとの幅（足りなければ最後の幅をくり返す）。
 * 最初の列は左寄せ、それ以外は中央
 */
@Composable
fun DataTable(headers: List<String>, rows: List<TableRow>, widths: List<Dp>, modifier: Modifier = Modifier) {
    fun width(i: Int) = widths.getOrElse(i) { widths.last() }
    Column(modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 6.dp)) {
            headers.forEachIndexed { i, h ->
                Text(
                    h,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.width(width(i)).padding(horizontal = 4.dp),
                )
            }
        }
        rows.forEach { row ->
            Row(Modifier.padding(vertical = 5.dp)) {
                row.cells.forEachIndexed { i, c ->
                    Text(
                        c,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (row.bold) FontWeight.Bold else FontWeight.Normal,
                        color = row.color ?: MaterialTheme.colorScheme.onSurface,
                        textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(width(i)).padding(horizontal = 4.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** 割合（0.523 → "52.3%"。無ければ "—"） */
fun pct(v: Double?): String = v?.let { "%.1f%%".format(it * 100) } ?: "—"

/** 小数 2 けた */
fun dec(v: Double?): String = v?.let { "%.2f".format(it) } ?: "—"

/** 成功 / 試投（"3/7"） */
fun ma(made: Int, attempts: Int): String = "$made/$attempts"

/** 成功率 */
fun rate(made: Int, attempts: Int): Double? = if (attempts > 0) made.toDouble() / attempts else null

/** 出場時間（"12:34"） */
fun minutes(ms: Long?): String = ms?.let { "%d:%02d".format(it / 60_000, it / 1000 % 60) } ?: "—"
