package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
data class CharacterAttributes(
    @SerialName("power") val power: Int = 1,
    @SerialName("agility") val agility: Int = 1,
    @SerialName("endurance") val endurance: Int = 1,
    @SerialName("focus") val focus: Int = 1
) {
    val combatRating: Int
        get() = (power * 2) + (agility * 2) + endurance + (focus / 2)

    fun increase(type: AttributeType, amount: Int): CharacterAttributes {
        val safeAmount = max(0, amount)
        return when (type) {
            AttributeType.POWER -> copy(power = power + safeAmount)
            AttributeType.AGILITY -> copy(agility = agility + safeAmount)
            AttributeType.ENDURANCE -> copy(endurance = endurance + safeAmount)
            AttributeType.FOCUS -> copy(focus = focus + safeAmount)
        }
    }

    fun mergeWith(other: CharacterAttributes): CharacterAttributes = CharacterAttributes(
        power = max(power, other.power),
        agility = max(agility, other.agility),
        endurance = max(endurance, other.endurance),
        focus = max(focus, other.focus)
    )
}
