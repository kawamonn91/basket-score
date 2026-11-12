package com.ttech.basketscore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** ボールの色 */
val BallOrange = Color(0xFFF28C28)

/** 体育館の明るさでも読みやすい濃い紺 */
val Navy = Color(0xFF1E2A44)

private val Light = lightColorScheme(
    primary = Color(0xFFB85A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Navy,
    onSecondary = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB77C),
    onPrimary = Color(0xFF4D2600),
    primaryContainer = Color(0xFF6E3900),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFBCC7E6),
    onSecondary = Color(0xFF26304A),
)

@Composable
fun BasketScoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
