package com.ttech.basketscore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ttech.basketscore.rules.Category
import com.ttech.basketscore.ui.theme.BasketScoreTheme

/**
 * ホーム（ひな形）。試合の一覧・新しい試合・チームの管理は、画面設計のあとに作る（docs/requirements.md の 8 章）。
 */
@Composable
fun HomeScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("Basket Score", style = MaterialTheme.typography.displaySmall)
            Text(
                "バスケットボールのスコアシートと試合の分析（準備中）",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                "対応する大会の区分: " + Category.entries.joinToString("・") { it.label },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun HomeScreenPreview() {
    BasketScoreTheme { HomeScreen() }
}
