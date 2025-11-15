package com.battlecell.app.domain.model.mission

import com.battlecell.app.domain.model.PlayerCharacter

sealed interface MissionPrecondition {
    fun isSatisfied(player: PlayerCharacter): Boolean

    data class MissionCompleted(val missionId: String) : MissionPrecondition {
        override fun isSatisfied(player: PlayerCharacter): Boolean =
            player.missions[missionId]?.status == MissionStatus.DONE
    }

    data class RankAtLeast(val minimumRank: Int) : MissionPrecondition {
        override fun isSatisfied(player: PlayerCharacter): Boolean =
            player.level >= minimumRank
    }
}
