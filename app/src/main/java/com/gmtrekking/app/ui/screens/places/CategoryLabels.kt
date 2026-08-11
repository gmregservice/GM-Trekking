package com.gmtrekking.app.ui.screens.places

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gmtrekking.app.R
import com.gmtrekking.app.data.poi.PlaceCategory

/** Etichetta leggibile (in italiano, senza gergo) per ogni categoria di luogo utile. */
@Composable
fun PlaceCategory.displayLabel(): String = stringResource(
    when (this) {
        PlaceCategory.ALL -> R.string.category_all
        PlaceCategory.RESTAURANT -> R.string.category_restaurant
        PlaceCategory.BAR -> R.string.category_bar
        PlaceCategory.TRATTORIA -> R.string.category_trattoria
        PlaceCategory.HOTEL -> R.string.category_hotel
        PlaceCategory.HOSTEL -> R.string.category_hostel
        PlaceCategory.ALPINE_HUT -> R.string.category_alpine_hut
        PlaceCategory.CAMP_SITE -> R.string.category_camp_site
        PlaceCategory.GUEST_HOUSE -> R.string.category_guest_house
    }
)

/** Ordine in cui mostrare i chip di filtro nell'interfaccia. */
val placeCategoryFilterOrder = listOf(
    PlaceCategory.ALL,
    PlaceCategory.RESTAURANT,
    PlaceCategory.BAR,
    PlaceCategory.TRATTORIA,
    PlaceCategory.HOTEL,
    PlaceCategory.HOSTEL,
    PlaceCategory.ALPINE_HUT,
    PlaceCategory.CAMP_SITE,
    PlaceCategory.GUEST_HOUSE,
)
