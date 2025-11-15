package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EncounterArchetype {
    @SerialName("outlaw")
    OUTLAW,

    @SerialName("knight")
    KNIGHT,

    @SerialName("paladin")
    PALADIN,

    @SerialName("dragon")
    DRAGON,

    @SerialName("player")
    PLAYER
}
