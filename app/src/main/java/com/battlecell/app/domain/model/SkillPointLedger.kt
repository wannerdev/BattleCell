package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
data class SkillPointLedger(
    @SerialName("power") val power: Int = 0,
    @SerialName("agility") val agility: Int = 0,
    @SerialName("endurance") val endurance: Int = 0,
    @SerialName("focus") val focus: Int = 0
) {
    fun get(type: AttributeType): Int = when (type) {
        AttributeType.POWER -> power
        AttributeType.AGILITY -> agility
        AttributeType.ENDURANCE -> endurance
        AttributeType.FOCUS -> focus
    }

    fun add(type: AttributeType, amount: Int): SkillPointLedger {
        if (amount <= 0) return this
        return when (type) {
            AttributeType.POWER -> copy(power = power + amount)
            AttributeType.AGILITY -> copy(agility = agility + amount)
            AttributeType.ENDURANCE -> copy(endurance = endurance + amount)
            AttributeType.FOCUS -> copy(focus = focus + amount)
        }
    }

    fun spend(type: AttributeType, amount: Int): SkillPointLedger {
        if (amount <= 0) return this
        return when (type) {
            AttributeType.POWER -> copy(power = max(0, power - amount))
            AttributeType.AGILITY -> copy(agility = max(0, agility - amount))
            AttributeType.ENDURANCE -> copy(endurance = max(0, endurance - amount))
            AttributeType.FOCUS -> copy(focus = max(0, focus - amount))
        }
    }
}
