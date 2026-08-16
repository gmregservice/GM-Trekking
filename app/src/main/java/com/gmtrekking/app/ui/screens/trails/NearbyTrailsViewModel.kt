package com.gmtrekking.app.ui.screens.trails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmtrekking.app.data.trails.NearbyTrail
import com.gmtrekking.app.data.trails.TrailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NearbyTrailsUiState(
    val isLoading: Boolean = false,
    val trails: List<NearbyTrail> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Stessa architettura di PlacesViewModel.kt (ViewModel + StateFlow, gestione
 * errori con dettaglio tecnico in chiaro perché non c'è accesso a Logcat) —
 * qui per i sentieri vicini invece dei luoghi utili (punto 5 del piano).
 */
class NearbyTrailsViewModel @JvmOverloads constructor(
    private val repository: TrailRepository = TrailRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(NearbyTrailsUiState())
    val uiState: StateFlow<NearbyTrailsUiState> = _uiState.asStateFlow()

    fun loadNearby(centerLat: Double, centerLon: Double, radiusMeters: Int = 5000) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val trails = repository.findNearby(centerLat, centerLon, radiusMeters)
                _uiState.value = _uiState.value.copy(isLoading = false, trails = trails)
            } catch (t: Throwable) {
                val detail = "${t::class.simpleName}: ${t.message ?: "nessun dettaglio"}"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Non riesco a caricare i sentieri vicini. Controlla la connessione e riprova.\n\nDettaglio tecnico: $detail",
                )
            }
        }
    }
}
