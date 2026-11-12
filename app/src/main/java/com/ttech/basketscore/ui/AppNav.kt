package com.ttech.basketscore.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ttech.basketscore.ui.record.PlayByPlayScreen
import com.ttech.basketscore.ui.analysis.AnalysisScreen
import com.ttech.basketscore.ui.record.RecordScreen
import com.ttech.basketscore.ui.record.ScoresheetScreen
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
                onOpenAnalysis = { id, tab -> nav.navigate("game/$id/analysis?tab=${tab.ordinal}") },
                onOpenScoresheet = { nav.navigate("game/$it/sheet") },
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
            val gameId = entry.arguments?.getLong("gameId") ?: 0L
            RecordScreen(
                gameId = gameId,
                onBack = { nav.popBackStack("home", inclusive = false) },
                onOpenPlayByPlay = { nav.navigate("game/$gameId/pbp") },
                onOpenScoresheet = { nav.navigate("game/$gameId/sheet") },
                onOpenAnalysis = { nav.navigate("game/$gameId/analysis?tab=0") },
            )
        }
        composable(
            "game/{gameId}/analysis?tab={tab}",
            arguments = listOf(navArgument("gameId") { type = NavType.LongType }, navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
        ) { entry ->
            val gameId = entry.arguments?.getLong("gameId") ?: 0L
            AnalysisScreen(
                gameId = gameId,
                initialTab = entry.arguments?.getInt("tab") ?: 0,
                onBack = { nav.popBackStack() },
                onOpenScoresheet = { nav.navigate("game/$gameId/sheet") },
                onOpenPlayByPlay = { nav.navigate("game/$gameId/pbp") },
            )
        }
        composable("game/{gameId}/sheet", arguments = listOf(navArgument("gameId") { type = NavType.LongType })) { entry ->
            ScoresheetScreen(gameId = entry.arguments?.getLong("gameId") ?: 0L, onBack = { nav.popBackStack() })
        }
        composable("game/{gameId}/pbp", arguments = listOf(navArgument("gameId") { type = NavType.LongType })) { entry ->
            PlayByPlayScreen(gameId = entry.arguments?.getLong("gameId") ?: 0L, onBack = { nav.popBackStack() })
        }
    }
}
