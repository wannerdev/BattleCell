package com.battlecell.app.feature.war

import com.battlecell.app.domain.model.PlayerCharacter
import com.battlecell.app.domain.model.mission.MissionStatus

data class WarCouncilUiState(
    val character: PlayerCharacter? = null,
    val missions: List<MissionDetailItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class MissionDetailItem(
    val id: String,
    val title: String,
    val description: String,
    val condition: String,
    val reward: String?,
    val status: MissionStatus,
    val progress: Int,
    val target: Int,
    val notes: String?
) {
    val progressFraction: Float
        get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
}
