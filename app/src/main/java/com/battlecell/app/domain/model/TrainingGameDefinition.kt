package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrainingGameDefinition(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("attribute_reward") val attributeReward: AttributeType,
    @SerialName("difficulty") val difficulty: Difficulty,
    @SerialName("base_reward") val baseReward: Int,
    @SerialName("icon") val icon: String,
    @SerialName("game_type") val gameType: TrainingGameType = TrainingGameType.BUG_HUNT,
    @SerialName("behavior") val behavior: TrainingGameBehavior
) {
    val displayReward: Int
        get() = (baseReward * difficulty.multiplier).toInt()
}
