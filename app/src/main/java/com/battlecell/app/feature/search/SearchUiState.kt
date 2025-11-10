package com.battlecell.app.feature.search

import com.battlecell.app.domain.model.EncounterProfile

data class SearchUiState(
    val encounters: List<EncounterProfile> = emptyList(),
    val isScanning: Boolean = false,
    val message: String? = null
)
