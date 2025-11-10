package com.battlecell.app.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.model.AttributeType
import com.battlecell.app.domain.model.TrainingGameDefinition
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
        didWin: Boolean
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

            val experienceGain = computeExperience(definition, elapsedMillis)
            val attributeGain = computeAttributeGain(definition, elapsedMillis)

            var updated = player
                .gainExperience(experienceGain)
                .updateAttributes { attrs ->
                    attrs.increase(definition.attributeReward, attributeGain)
                }

            updated = updated.copy(
                skillPoints = updated.skillPoints + computeSkillPointBonus(definition, elapsedMillis)
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
        elapsedMillis: Long
    ): Int {
        val base = definition.displayReward
        return if (definition.behavior.flickerEnabled) {
            val bonus = max(0, (definition.behavior.totalDurationMillis - elapsedMillis).toInt())
            base + (bonus / 40)
        } else {
            base
        }
    }

    private fun computeAttributeGain(
        definition: TrainingGameDefinition,
        elapsedMillis: Long
    ): Int {
        return if (definition.behavior.flickerEnabled) {
            val remaining = max(0L, definition.behavior.totalDurationMillis.toLong() - elapsedMillis)
            max(1, (remaining / 900L).roundToInt() + 1)
        } else {
            1
        }
    }

    private fun computeSkillPointBonus(
        definition: TrainingGameDefinition,
        elapsedMillis: Long
    ): Int {
        if (!definition.behavior.flickerEnabled) return 0
        val remaining = max(0L, definition.behavior.totalDurationMillis.toLong() - elapsedMillis)
        return min(2, (remaining / 1500L).toInt())
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
