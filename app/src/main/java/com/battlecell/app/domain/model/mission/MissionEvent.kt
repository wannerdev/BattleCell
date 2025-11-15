package com.battlecell.app.domain.model.mission

import com.battlecell.app.domain.model.EncounterArchetype

sealed interface MissionEvent {
    data object HornSounded : MissionEvent

    data class LegendaryTrainingWin(val gameId: String) : MissionEvent

    data class BattleVictory(
        val archetype: EncounterArchetype,
        val title: String
    ) : MissionEvent

    data object DragonSighted : MissionEvent
    data object SapphirePotionFound : MissionEvent
    data object DragonSlain : MissionEvent
}
