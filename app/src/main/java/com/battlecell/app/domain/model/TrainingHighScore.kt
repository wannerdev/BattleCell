package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrainingScoreEntry(
    @SerialName("score") val score: Int = 0,
    @SerialName("elapsed_millis") val elapsedMillis: Long = 0L,
    @SerialName("achieved_at") val achievedAtEpoch: Long = 0L
)

@Serializable
data class TrainingGameScores(
    @SerialName("entries") val entries: Map<Difficulty, TrainingScoreEntry> = emptyMap()
) {
    fun bestFor(difficulty: Difficulty): TrainingScoreEntry? = entries[difficulty]

    fun withScore(
        difficulty: Difficulty,
        newEntry: TrainingScoreEntry,
        comparator: (old: TrainingScoreEntry, new: TrainingScoreEntry) -> Boolean
    ): TrainingGameScores {
        val current = entries[difficulty]
        val shouldReplace = current == null || comparator(current, newEntry)
        return if (!shouldReplace) {
            this
        } else {
            copy(entries = entries + (difficulty to newEntry))
        }
    }
}
