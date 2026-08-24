package com.captainavi.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Selects a trace that is already persisted in Room for display on the Chart.
 * The selection is intentionally temporary; the trace itself remains in Log.
 */
object SavedTraceState {
    private val _selectedTripId = MutableStateFlow<String?>(null)
    val selectedTripId: StateFlow<String?> = _selectedTripId.asStateFlow()

    fun load(tripId: String) {
        _selectedTripId.value = tripId.takeIf(String::isNotBlank)
    }

    fun clear() {
        _selectedTripId.value = null
    }
}
