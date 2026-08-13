package com.gmtrekking.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gmtrekking.app.ui.screens.places.PlacesScreen
import com.gmtrekking.app.ui.screens.trailnavigation.MainMapScreen

private object Routes {
    const val MAIN_MAP = "main_map"
    const val PLACES = "places"
}

/**
 * L'app si apre direttamente sulla mappa con la posizione corrente
 * (MAIN_MAP): caricare un percorso GPX è un'azione opzionale disponibile da
 * lì, non un passaggio obbligato prima di vedere qualcosa.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.MAIN_MAP) {

        composable(Routes.MAIN_MAP) {
            MainMapScreen(
                onPlacesNearbyClick = { navController.navigate(Routes.PLACES) },
            )
        }

        composable(Routes.PLACES) {
            PlacesScreen()
        }
    }
}
