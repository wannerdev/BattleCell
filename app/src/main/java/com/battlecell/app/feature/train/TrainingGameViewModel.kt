package com.battlecell.app.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.model.AttributeType
import com.battlecell.app.domain.model.Difficulty
import com.battlecell.app.domain.model.TrainingGameDefinition
import com.battlecell.app.domain.model.TrainingGameType
import com.battlecell.app.domain.model.TrainingScoreEntry
import com.battlecell.app.domain.usecase.GetTrainingGamesUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

class TrainingGameViewModel(
    private val playerRepository: PlayerRepository,
    getTrainingGamesUseCase: GetTrainingGamesUseCase,
    private val gameId: String
) : ViewModel() {

    private val definitionFlow = MutableStateFlow<TrainingGameDefinition?>(null)
    private val resultFlow = MutableStateFlow<TrainingGameResult?>(null)
    private val selectedDifficultyFlow = MutableStateFlow(Difficulty.EASY)
    private val eventsChannel = Channel<TrainingGameEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<TrainingGameUiState> = combine(
        playerRepository.playerStream,
        definitionFlow,
        resultFlow,
        selectedDifficultyFlow
    ) { player, definition, result, difficulty ->
        TrainingGameUiState(
            definition = definition,
            character = player,
            lastResult = result,
            isSaving = false,
            selectedDifficulty = difficulty
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrainingGameUiState(isSaving = true)
    )

    init {
        val definition = getTrainingGamesUseCase().firstOrNull { it.id == gameId }
        definitionFlow.value = definition
        selectedDifficultyFlow.value = definition?.difficulty ?: Difficulty.EASY
        if (definition == null) {
            viewModelScope.launch {
                eventsChannel.send(TrainingGameEvent.Error("Unknown training scenario."))
            }
        }
    }

    fun consumeResult() {
        resultFlow.value = null
    }

    fun setDifficulty(difficulty: Difficulty) {
        selectedDifficultyFlow.value = difficulty
    }

    fun recordOutcome(
        elapsedMillis: Long,
        didWin: Boolean,
        score: Int = 0
    ) {
        val definition = definitionFlow.value ?: return
        val difficulty = selectedDifficultyFlow.value
        viewModelScope.launch {
            val player = playerRepository.playerStream.first() ?: run {
                eventsChannel.send(TrainingGameEvent.Error("Create a hero before training."))
                return@launch
            }

            if (!didWin) {
                val result = TrainingGameResult.Defeat
                resultFlow.value = result
                eventsChannel.send(TrainingGameEvent.Defeat)
                return@launch
            }

            val experienceGain = computeExperience(definition, elapsedMillis, score, didWin, difficulty)
            val attributeSigils = computeAttributeSigils(definition, elapsedMillis, score, didWin, difficulty)
            val bonusSigils = if (didWin) difficulty.skillPointReward else 0
            val totalSigils = attributeSigils + bonusSigils

            var updated = player.gainExperience(experienceGain)

            if (totalSigils > 0) {
                updated = updated.addVariantSkillPoints(definition.attributeReward, totalSigils)
            }

            val entry = TrainingScoreEntry(
                score = score,
                elapsedMillis = elapsedMillis,
                achievedAtEpoch = System.currentTimeMillis()
            )
            val comparator: (TrainingScoreEntry, TrainingScoreEntry) -> Boolean =
                { old, new ->
                    new.score > old.score ||
                        (new.score == old.score && new.elapsedMillis < old.elapsedMillis)
                }
            updated = updated.updateTrainingHighScore(
                gameId = definition.id,
                difficulty = difficulty,
                entry = entry,
                comparator = comparator
            )

            playerRepository.upsert(updated)

            val result = TrainingGameResult.Victory(
                attributeSigilGain = attributeSigils,
                experienceGain = experienceGain,
                attributeType = definition.attributeReward,
                bonusSigilGain = bonusSigils,
                difficulty = difficulty
            )
            resultFlow.value = result
            eventsChannel.send(TrainingGameEvent.Victory(result))
        }
    }

    private fun computeExperience(
        definition: TrainingGameDefinition,
        elapsedMillis: Long,
        score: Int,
        didWin: Boolean,
        difficulty: Difficulty
    ): Int {
        val base = definition.baseReward
        val raw = when (definition.gameType) {
            TrainingGameType.BUG_HUNT -> {
                if (definition.behavior.flickerEnabled) {
                    val bonus = max(0, (definition.behavior.totalDurationMillis - elapsedMillis).toInt())
                    base + (bonus / 40)
                } else {
                    base
                }
            }

            TrainingGameType.FLAPPY_FLIGHT -> {
                val duration = definition.behavior.totalDurationMillis.takeIf { it > 0 } ?: 45000
                val progress = (elapsedMillis.toFloat() / duration).coerceIn(0f, 1f)
                val bonus = (base * (0.6f + progress * 0.8f)).roundToInt()
                bonus + if (didWin) base / 4 else 0
            }

            TrainingGameType.DOODLE_JUMP -> {
                val performance = max(0, score / 20)
                base + performance + if (didWin) base / 3 else 0
            }

            TrainingGameType.SUBWAY_RUN -> {
                val runtimeBonus = max(0, (elapsedMillis / 700L).toInt())
                base + runtimeBonus + if (didWin) base / 5 else 0
            }
        }
        return (raw * difficulty.experienceFactor()).roundToInt()
    }

    private fun computeAttributeSigils(
        definition: TrainingGameDefinition,
        elapsedMillis: Long,
        score: Int,
        didWin: Boolean,
        difficulty: Difficulty
    ): Int {
        val raw = when (definition.gameType) {
            TrainingGameType.BUG_HUNT -> {
                if (definition.behavior.flickerEnabled) {
                    val remaining = max(0L, definition.behavior.totalDurationMillis.toLong() - elapsedMillis)
                    val bonus = (remaining / 900.0).roundToInt()
                    max(1, bonus + 1)
                } else {
                    1
                }
            }

            TrainingGameType.FLAPPY_FLIGHT -> {
                val duration = definition.behavior.totalDurationMillis.takeIf { it > 0 } ?: 45000
                val tiers = (elapsedMillis / 15000L).coerceAtLeast(0)
                max(1, tiers.toInt() + if (didWin) 2 else 1)
            }

            TrainingGameType.DOODLE_JUMP -> {
                val target = definition.behavior.targetScore.takeIf { it > 0 } ?: 600
                val ratio = (score.toFloat() / target).coerceIn(0f, 1.5f)
                max(1, (1 + ratio * 2.2f).roundToInt())
            }

            TrainingGameType.SUBWAY_RUN -> {
                val stamina = (elapsedMillis / 20000L).coerceAtLeast(0)
                max(1, stamina.toInt() + if (didWin) 1 else 0)
            }
        }
        return max(1, (raw * difficulty.attributeFactor()).roundToInt())
    }

    companion object {
        fun provideFactory(
            playerRepository: PlayerRepository,
            getTrainingGamesUseCase: GetTrainingGamesUseCase,
            gameId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TrainingGameViewModel(
                    playerRepository = playerRepository,
                    getTrainingGamesUseCase = getTrainingGamesUseCase,
                    gameId = gameId
                ) as T
            }
        }
    }
}

private fun Difficulty.experienceFactor(): Double = when (this) {
    Difficulty.EASY -> 1.0
    Difficulty.NORMAL -> 1.25
    Difficulty.HARD -> 1.6
    Difficulty.LEGENDARY -> 2.1
}

private fun Difficulty.attributeFactor(): Double = when (this) {
    Difficulty.EASY -> 1.0
    Difficulty.NORMAL -> 1.15
    Difficulty.HARD -> 1.4
    Difficulty.LEGENDARY -> 1.75
}

data class TrainingGameUiState(
    val definition: TrainingGameDefinition? = null,
    val character: com.battlecell.app.domain.model.PlayerCharacter? = null,
    val lastResult: TrainingGameResult? = null,
    val isSaving: Boolean = true,
    val selectedDifficulty: Difficulty = Difficulty.EASY
)

sealed interface TrainingGameResult {
    data class Victory(
        val attributeSigilGain: Int,
        val experienceGain: Int,
        val attributeType: AttributeType,
        val bonusSigilGain: Int,
        val difficulty: Difficulty
    ) : TrainingGameResult

    data object Defeat : TrainingGameResult
}

sealed interface TrainingGameEvent {
    data class Victory(val result: TrainingGameResult.Victory) : TrainingGameEvent
    data object Defeat : TrainingGameEvent
    data class Error(val message: String) : TrainingGameEvent
}
