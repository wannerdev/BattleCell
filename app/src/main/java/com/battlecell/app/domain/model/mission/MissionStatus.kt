package com.battlecell.app.domain.model.mission

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MissionStatus {
    @SerialName("active")
    ACTIVE,

    @SerialName("deactivated")
    DEACTIVATED,

    @SerialName("done")
    DONE,

    @SerialName("failed")
    FAILED
}
