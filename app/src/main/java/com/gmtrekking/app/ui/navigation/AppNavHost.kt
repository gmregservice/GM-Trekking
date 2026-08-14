package com.gmtrekking.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gmtrekking.app.ui.screens.history.ActivityDetailScreen
import com.gmtrekking.app.ui.screens.history.ActivityHistoryScreen
import com.gmtrekking.app.ui.screens.places.PlacesScreen
import com.gmtrekking.app.ui.screens.trailnavigation.MainMapScreen

private object Routes {
    const val MAIN_MAP = "main_map"
    const val PLACES = "places"
    const val HISTORY = "history"
    const val HISTORY_DETAIL_ARG = "activityId"
    const val HISTORY_DETAIL = "history_detail/{activityId}"
    fun historyDetail(activityId: String) = "history_detail/$activityId"
}

/**
 * L'app si apre direttamente sulla mappa con la posizione corrente
 * (MAIN_MAP): caricare un percorso GPX è un'azione opzionale disponibile da
 * lì, non un passaggio obbligato prima di vedere qualcosa.
 *
 * Ogni schermata diversa da MAIN_MAP ha una freccia indietro esplicita verso
 * la mappa principale (popBackStack), non solo il gesto di sistema —
 * richiesto esplicitamente (agosto 2026), vedi "Note operative" in
 * docs/PIANO_SVILUPPO.md.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.MAIN_MAP) {

        composable(Routes.MAIN_MAP) {
            MainMapScreen(
                onPlacesNearbyClick = { navController.navigate(Routes.PLACES) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
            )
        }

        composable(Routes.PLACES) {
            PlacesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            ActivityHistoryScreen(
                onBack = { navController.popBackStack() },
                onActivityClick = { activityId -> navController.navigate(Routes.historyDetail(activityId)) },
            )
        }

        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument(Routes.HISTORY_DETAIL_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString(Routes.HISTORY_DETAIL_ARG)
            if (activityId != null) {
                ActivityDetailScreen(
                    activityId = activityId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
