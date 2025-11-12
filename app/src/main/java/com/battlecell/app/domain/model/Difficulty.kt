package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty(
    val multiplier: Double,
    val displayName: String,
    val skillPointReward: Int
) {
    @SerialName("easy")
    EASY(
        multiplier = 1.0,
        displayName = "Easy",
        skillPointReward = 1
    ),

    @SerialName("normal")
    NORMAL(
        multiplier = 1.5,
        displayName = "Medium",
        skillPointReward = 3
    ),

    @SerialName("hard")
    HARD(
        multiplier = 2.25,
        displayName = "Difficult",
        skillPointReward = 10
    ),

    @SerialName("legendary")
    LEGENDARY(
        multiplier = 3.5,
        displayName = "Ultra",
        skillPointReward = 100
    )
}
