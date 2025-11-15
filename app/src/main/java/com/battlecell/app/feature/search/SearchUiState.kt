package com.battlecell.app.feature.search

import com.battlecell.app.domain.model.EncounterProfile
import com.battlecell.app.domain.model.PlayerCharacter

data class SearchUiState(
    val encounters: List<EncounterProfile> = emptyList(),
    val isScanning: Boolean = false,
    val message: String? = null,
    val player: PlayerCharacter? = null
)
