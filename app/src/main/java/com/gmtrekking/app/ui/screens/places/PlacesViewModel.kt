package com.gmtrekking.app.ui.screens.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmtrekking.app.data.poi.PlaceCategory
import com.gmtrekking.app.data.poi.Poi
import com.gmtrekking.app.data.poi.PoiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlacesUiState(
    val isLoading: Boolean = false,
    val allPois: List<Poi> = emptyList(),
    val selectedCategory: PlaceCategory = PlaceCategory.ALL,
    val errorMessage: String? = null,
) {
    /** Elenco già filtrato per la categoria selezionata: quello che la UI deve mostrare. */
    val visiblePois: List<Poi>
        get() = if (selectedCategory == PlaceCategory.ALL) {
            allPois
        } else {
            allPois.filter { it.category == selectedCategory }
        }
}

/**
 * Recupera una volta i luoghi utili intorno a un punto (tutte le categorie in
 * un'unica chiamata a Overpass API) e lascia che il filtro per categoria nella
 * UI lavori in memoria, senza rifare la richiesta di rete ad ogni tap sui chip.
 */
class PlacesViewModel @JvmOverloads constructor(
    // @JvmOverloads è necessario perché il factory di default di viewModel()
    // in Compose crea l'istanza via reflection cercando un costruttore senza
    // argomenti: senza questa annotazione, il parametro con valore di default
    // non basta a generare quel costruttore a livello di bytecode.
    private val repository: PoiRepository = PoiRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlacesUiState())
    val uiState: StateFlow<PlacesUiState> = _uiState.asStateFlow()

    fun loadNearby(centerLat: Double, centerLon: Double, radiusMeters: Int = 1500) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val pois = repository.findNearby(centerLat, centerLon, radiusMeters)
                _uiState.value = _uiState.value.copy(isLoading = false, allPois = pois)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Non riesco a caricare i luoghi utili. Controlla la connessione e riprova.",
                )
            }
        }
    }

    fun selectCategory(category: PlaceCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }
}
