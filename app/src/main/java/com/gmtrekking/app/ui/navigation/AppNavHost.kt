package com.gmtrekking.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gmtrekking.app.ui.screens.home.HomeScreen
import com.gmtrekking.app.ui.screens.importtrail.ImportTrailScreen
import com.gmtrekking.app.ui.screens.places.PlacesScreen
import com.gmtrekking.app.ui.screens.trailnavigation.TrailNavigationScreen

private object Routes {
    const val HOME = "home"
    const val IMPORT_TRAIL = "import_trail"
    const val TRAIL_NAVIGATION = "trail_navigation"
    const val PLACES = "places"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onImportGpxClick = { navController.navigate(Routes.IMPORT_TRAIL) },
                onPlacesNearbyClick = { navController.navigate(Routes.PLACES) },
            )
        }

        composable(Routes.IMPORT_TRAIL) {
            ImportTrailScreen(
                onTrackLoaded = {
                    navController.navigate(Routes.TRAIL_NAVIGATION) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }

        composable(Routes.TRAIL_NAVIGATION) {
            TrailNavigationScreen()
        }

        composable(Routes.PLACES) {
            PlacesScreen()
        }
    }
}
