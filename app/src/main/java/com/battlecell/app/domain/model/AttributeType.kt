package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AttributeType {
    @SerialName("power")
    POWER,

    @SerialName("agility")
    AGILITY,

    @SerialName("endurance")
    ENDURANCE,

    @SerialName("focus")
    FOCUS
}
