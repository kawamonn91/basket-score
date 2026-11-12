package com.ttech.basketscore.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ttech.basketscore.ui.game.GameScreen
import com.ttech.basketscore.ui.setup.GameSetupScreen
import com.ttech.basketscore.ui.teams.TeamEditScreen
import com.ttech.basketscore.ui.teams.TeamsScreen

/** 画面の移動（docs/design.md の 3.1） */
@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNewGame = { nav.navigate("setup") },
                onTeams = { nav.navigate("teams") },
                onOpenGame = { nav.navigate("game/$it") },
            )
        }
        composable("teams") {
            TeamsScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("team/$it") },
                onNew = { nav.navigate("team/0") },
            )
        }
        composable("team/{teamId}", arguments = listOf(navArgument("teamId") { type = NavType.LongType })) { entry ->
            TeamEditScreen(teamId = entry.arguments?.getLong("teamId") ?: 0L, onDone = { nav.popBackStack() })
        }
        composable("setup") {
            GameSetupScreen(
                onBack = { nav.popBackStack() },
                onEditTeams = { nav.navigate("teams") },
                onCreated = { gameId -> nav.navigate("game/$gameId") { popUpTo("home") } },
            )
        }
        composable("game/{gameId}", arguments = listOf(navArgument("gameId") { type = NavType.LongType })) { entry ->
            GameScreen(gameId = entry.arguments?.getLong("gameId") ?: 0L, onBack = { nav.popBackStack() })
        }
    }
}
