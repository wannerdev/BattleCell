package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max

@Serializable
data class PlayerCharacter(
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    @SerialName("name") val name: String = "",
    @SerialName("level") val level: Int = 1,
    @SerialName("experience") val experience: Int = 0,
    @SerialName("attributes") val attributes: CharacterAttributes = CharacterAttributes(),
    @SerialName("victories") val victories: Int = 0,
    @SerialName("defeats") val defeats: Int = 0,
    @SerialName("skill_points") val skillPoints: Int = 0,
    @SerialName("created_at") val createdAtEpoch: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAtEpoch: Long = System.currentTimeMillis()
) {
    val combatRating: Int
        get() = (level * 10) + experience / 50 + attributes.combatRating

    val totalBattles: Int
        get() = victories + defeats

    fun gainExperience(amount: Int): PlayerCharacter {
        val newExperience = max(0, experience + amount)
        var tempLevel = level
        var tempExperience = newExperience
        var tempSkillPoints = skillPoints

        while (tempExperience >= experienceToLevel(tempLevel)) {
            tempExperience -= experienceToLevel(tempLevel)
            tempLevel += 1
            tempSkillPoints += 3
        }

        return copy(
            level = tempLevel,
            experience = tempExperience,
            skillPoints = tempSkillPoints,
            updatedAtEpoch = System.currentTimeMillis()
        )
    }

    private fun experienceToLevel(level: Int): Int = 200 + (level * 50)

    fun spendSkillPoints(type: AttributeType, amount: Int): PlayerCharacter {
        val actualAmount = max(0, amount)
        if (actualAmount <= 0 || actualAmount > skillPoints) return this
        return copy(
            attributes = attributes.increase(type, actualAmount),
            skillPoints = skillPoints - actualAmount,
            updatedAtEpoch = System.currentTimeMillis()
        )
    }

    fun recordVictory(): PlayerCharacter = copy(
        victories = victories + 1,
        updatedAtEpoch = System.currentTimeMillis()
    )

    fun recordDefeat(): PlayerCharacter = copy(
        defeats = defeats + 1,
        updatedAtEpoch = System.currentTimeMillis()
    )

    fun rename(newName: String): PlayerCharacter = copy(
        name = newName,
        updatedAtEpoch = System.currentTimeMillis()
    )

    fun updateAttributes(block: (CharacterAttributes) -> CharacterAttributes): PlayerCharacter =
        copy(
            attributes = block(attributes),
            updatedAtEpoch = System.currentTimeMillis()
        )
}
