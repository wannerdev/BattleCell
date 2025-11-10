package com.battlecell.app.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.model.AttributeType
import com.battlecell.app.domain.model.TrainingGameDefinition
import com.battlecell.app.domain.model.TrainingGameType
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
import kotlin.math.min
import kotlin.math.roundToInt

class TrainingGameViewModel(
    private val playerRepository: PlayerRepository,
    getTrainingGamesUseCase: GetTrainingGamesUseCase,
    private val gameId: String
) : ViewModel() {

    private val definitionFlow = MutableStateFlow<TrainingGameDefinition?>(null)
    private val resultFlow = MutableStateFlow<TrainingGameResult?>(null)
    private val eventsChannel = Channel<TrainingGameEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<TrainingGameUiState> = combine(
        playerRepository.playerStream,
        definitionFlow,
        resultFlow
    ) { player, definition, result ->
        TrainingGameUiState(
            definition = definition,
            character = player,
            lastResult = result,
            isSaving = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrainingGameUiState(isSaving = true)
    )

    init {
        val definition = getTrainingGamesUseCase().firstOrNull { it.id == gameId }
        definitionFlow.value = definition
        if (definition == null) {
            viewModelScope.launch {
                eventsChannel.send(TrainingGameEvent.Error("Unknown training scenario."))
            }
        }
    }

    fun consumeResult() {
        resultFlow.value = null
    }

    fun recordOutcome(
        elapsedMillis: Long,
        didWin: Boolean,
        score: Int = 0
    ) {
        val definition = definitionFlow.value ?: return
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

            val experienceGain = computeExperience(definition, elapsedMillis, score, didWin)
            val attributeGain = computeAttributeGain(definition, elapsedMillis, score, didWin)

            var updated = player
                .gainExperience(experienceGain)
                .updateAttributes { attrs ->
                    attrs.increase(definition.attributeReward, attributeGain)
                }

            updated = updated.copy(
                skillPoints = updated.skillPoints + computeSkillPointBonus(definition, elapsedMillis, score, didWin)
            )

            playerRepository.upsert(updated)

            val result = TrainingGameResult.Victory(
                attributeGain = attributeGain,
                experienceGain = experienceGain,
                attributeType = definition.attributeReward
            )
            resultFlow.value = result
            eventsChannel.send(TrainingGameEvent.Victory(result))
        }
    }

    private fun computeExperience(
        definition: TrainingGameDefinition,
        elapsedMillis: Long,
        score: Int,
        didWin: Boolean
    ): Int {
        val base = definition.displayReward
        return when (definition.gameType) {
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
    }

    private fun computeAttributeGain(
        definition: TrainingGameDefinition,
        elapsedMillis: Long,
        score: Int,
        didWin: Boolean
    ): Int {
        return when (definition.gameType) {
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
    }

    private fun computeSkillPointBonus(
        definition: TrainingGameDefinition,
        elapsedMillis: Long,
        score: Int,
        didWin: Boolean
    ): Int {
        return when (definition.gameType) {
            TrainingGameType.BUG_HUNT -> {
                if (!definition.behavior.flickerEnabled) return 0
                val remaining = max(0L, definition.behavior.totalDurationMillis.toLong() - elapsedMillis)
                min(2, (remaining / 1500L).toInt())
            }

            TrainingGameType.FLAPPY_FLIGHT -> {
                val duration = definition.behavior.totalDurationMillis.takeIf { it > 0 } ?: 45000
                if (didWin && elapsedMillis >= duration * 0.8f) 1 else 0
            }

            TrainingGameType.DOODLE_JUMP -> {
                val target = definition.behavior.targetScore.takeIf { it > 0 } ?: 600
                when {
                    score >= target -> 2
                    score >= (target * 0.7f).toInt() -> 1
                    else -> 0
                }
            }

            TrainingGameType.SUBWAY_RUN -> {
                min(2, (elapsedMillis / 30000L).toInt())
            }
        }
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

data class TrainingGameUiState(
    val definition: TrainingGameDefinition? = null,
    val character: com.battlecell.app.domain.model.PlayerCharacter? = null,
    val lastResult: TrainingGameResult? = null,
    val isSaving: Boolean = true
)

sealed interface TrainingGameResult {
    data class Victory(
        val attributeGain: Int,
        val experienceGain: Int,
        val attributeType: AttributeType
    ) : TrainingGameResult

    data object Defeat : TrainingGameResult
}

sealed interface TrainingGameEvent {
    data class Victory(val result: TrainingGameResult.Victory) : TrainingGameEvent
    data object Defeat : TrainingGameEvent
    data class Error(val message: String) : TrainingGameEvent
}
