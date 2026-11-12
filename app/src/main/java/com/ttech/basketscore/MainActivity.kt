package com.ttech.basketscore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ttech.basketscore.ui.HomeScreen
import com.ttech.basketscore.ui.theme.BasketScoreTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BasketScoreTheme {
                HomeScreen()
            }
        }
    }
}
