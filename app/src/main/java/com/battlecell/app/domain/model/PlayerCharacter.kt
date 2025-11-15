package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

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
    @SerialName("skill_ledger") val skillLedger: SkillPointLedger = SkillPointLedger(),
    @SerialName("status_points") val statusPoints: Int = 0,
    @SerialName("training_high_scores") val trainingHighScores: Map<String, TrainingGameScores> = emptyMap(),
    @SerialName("created_at") val createdAtEpoch: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAtEpoch: Long = System.currentTimeMillis()
) {
    val combatRating: Int
        get() = (level * 10) + experience / 50 + attributes.combatRating

    val totalBattles: Int
        get() = victories + defeats

    fun gainExperience(amount: Int): PlayerCharacter {
        if (amount == 0) return this
        val newExperience = max(0, experience + amount)
        val newLevel = levelForExperience(newExperience)
        val levelDelta = newLevel - level
        val updatedSkillPoints = if (levelDelta > 0) {
            skillPoints + levelDelta * SKILL_POINTS_PER_LEVEL
        } else {
            skillPoints
        }
        return copy(
            experience = newExperience,
            level = newLevel,
            skillPoints = updatedSkillPoints,
            updatedAtEpoch = System.currentTimeMillis()
        )
    }

    fun spendSkillPoints(type: AttributeType, amount: Int): PlayerCharacter {
        val actualAmount = max(0, amount)
        if (actualAmount <= 0) return this
        var remaining = actualAmount
        var updatedLedger = skillLedger
        var updatedGeneral = skillPoints

        val ledgerAvailable = updatedLedger.get(type)
        if (ledgerAvailable > 0) {
            val takenFromLedger = minOf(ledgerAvailable, remaining)
            updatedLedger = updatedLedger.spend(type, takenFromLedger)
            remaining -= takenFromLedger
        }

        if (remaining > 0) {
            if (remaining > updatedGeneral) return this
            updatedGeneral -= remaining
            remaining = 0
        }

        if (remaining > 0) return this

        return copy(
            attributes = attributes.increase(type, actualAmount),
            skillPoints = updatedGeneral,
            skillLedger = updatedLedger,
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

    fun addVariantSkillPoints(type: AttributeType, amount: Int): PlayerCharacter {
        if (amount <= 0) return this
        return copy(
            skillLedger = skillLedger.add(type, amount),
            updatedAtEpoch = System.currentTimeMillis()
        )
    }

    fun variantSkillPoints(type: AttributeType): Int = skillLedger.get(type)

    fun updateTrainingHighScore(
        gameId: String,
        difficulty: Difficulty,
        entry: TrainingScoreEntry,
        comparator: (old: TrainingScoreEntry, new: TrainingScoreEntry) -> Boolean
    ): PlayerCharacter {
        val current = trainingHighScores[gameId] ?: TrainingGameScores()
        val updatedScores = current.withScore(difficulty, entry, comparator)
        if (updatedScores == current) return this
        return copy(
            trainingHighScores = trainingHighScores + (gameId to updatedScores),
            updatedAtEpoch = System.currentTimeMillis()
        )
    }

    fun gainStatusPoints(amount: Int): PlayerCharacter {
        if (amount <= 0) return this
        return copy(
            statusPoints = statusPoints + amount,
            updatedAtEpoch = System.currentTimeMillis()
        )
    }

    companion object {
        private const val SKILL_POINTS_PER_LEVEL = 2
        private const val BASE_EXPERIENCE_TO_LEVEL = 120
        private const val EXPERIENCE_MIN_STEP = 30
        private const val EXPERIENCE_GROWTH_MULTIPLIER = 1.35

        private fun levelForExperience(totalExperience: Int): Int {
            var remaining = totalExperience
            var level = 1
            var requirement = BASE_EXPERIENCE_TO_LEVEL
            while (remaining >= requirement) {
                remaining -= requirement
                level++
                requirement = nextRequirement(requirement)
            }
            return level
        }

        private fun nextRequirement(current: Int): Int {
            val grown = (current * EXPERIENCE_GROWTH_MULTIPLIER).roundToInt()
            return max(grown, current + EXPERIENCE_MIN_STEP)
        }
    }
}
