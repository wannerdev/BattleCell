package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EncounterSource {
    @SerialName("wifi")
    WIFI,

    @SerialName("bluetooth")
    BLUETOOTH,

    @SerialName("npc")
    NPC,

    @SerialName("player_cache")
    PLAYER_CACHE
}
