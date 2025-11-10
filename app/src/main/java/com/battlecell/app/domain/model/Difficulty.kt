package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty(val multiplier: Double) {
    @SerialName("easy")
    EASY(1.0),

    @SerialName("normal")
    NORMAL(1.5),

    @SerialName("hard")
    HARD(2.25),

    @SerialName("legendary")
    LEGENDARY(3.5)
}
