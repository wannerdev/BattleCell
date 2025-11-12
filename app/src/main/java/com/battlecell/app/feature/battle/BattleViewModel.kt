package com.battlecell.app.feature.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.battlecell.app.data.repository.EncounterRepository
import com.battlecell.app.data.repository.PlayerRepository
import com.battlecell.app.domain.model.EncounterProfile
import com.battlecell.app.domain.model.PlayerCharacter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random

class BattleViewModel(
    private val playerRepository: PlayerRepository,
    private val encounterRepository: EncounterRepository,
    private val opponentId: String
) : ViewModel() {

    private val rouletteState = MutableStateFlow<RouletteState?>(null)
    private val resultState = MutableStateFlow<BattleResult?>(null)
    private val processingState = MutableStateFlow(false)
    private val toastMessage = MutableStateFlow<String?>(null)

    private val opponentFlow = encounterRepository.encounterStream
        .map { encounters -> encounters.firstOrNull { it.id == opponentId } }

    val uiState: StateFlow<BattleUiState> = combine(
        playerRepository.playerStream,
        opponentFlow,
        rouletteState,
        resultState,
        processingState,
        toastMessage
    ) { tuple: Array<Any?> ->
        val player = tuple[0] as PlayerCharacter?
        val opponent = tuple[1] as EncounterProfile?
        val roulette = tuple[2] as RouletteState?
        val result = tuple[3] as BattleResult?
        val isProcessing = tuple[4] as Boolean
        val message = tuple[5] as String?
        val comparison = computeComparison(player, opponent)
        BattleUiState(
            player = player,
            opponent = opponent,
            comparison = comparison,
            roulette = roulette,
            result = result,
            isProcessing = isProcessing,
            message = message,
            statusRewardPreview = previewReward(comparison, player, opponent)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BattleUiState()
    )

    fun consumeMessage() {
        toastMessage.value = null
    }

    fun beginEngagement() {
        val state = uiState.value
        val player = state.player ?: return
        val opponent = state.opponent ?: return
        when (state.comparison) {
            StrengthComparison.PLAYER_ADVANTAGE -> resolveVictory(player, opponent, previewReward(state.comparison, player, opponent))
            StrengthComparison.NPC_ADVANTAGE -> finalizeDefeat(player, "The rival's strength overwhelmed you.")
            StrengthComparison.TIE -> startRoulette()
            StrengthComparison.UNDECIDED -> {}
        }
    }

    fun startRoulette() {
        val player = uiState.value.player ?: return
        val opponent = uiState.value.opponent ?: return
        if (computeComparison(player, opponent) != StrengthComparison.TIE) return
        rouletteState.value = RouletteState(
            currentTurn = RouletteTurn.PLAYER,
            history = emptyList(),
            resolving = false
        )
    }

    fun pullChains() {
        val player = uiState.value.player ?: return
        val opponent = uiState.value.opponent ?: return
        val comparison = computeComparison(player, opponent)
        if (comparison != StrengthComparison.TIE) return

        val current = rouletteState.value ?: return
        if (current.currentTurn != RouletteTurn.PLAYER || current.resolving) return

        val reward = previewReward(comparison, player, opponent)
        viewModelScope.launch {
            rouletteState.value = current.copy(resolving = true)
            delay(320)
            val opponentFalls = Random.nextFloat() < 0.55f
            val updatedHistory = current.history + RouletteTurnResult(RouletteTurn.PLAYER, opponentFalls)
            if (opponentFalls) {
                rouletteState.value = current.copy(history = updatedHistory, resolving = false)
                resolveVictory(player, opponent, reward)
            } else {
                val nextState = current.copy(
                    history = updatedHistory,
                    currentTurn = RouletteTurn.OPPONENT,
                    resolving = true
                )
                rouletteState.value = nextState
                resolveOpponentTurn(nextState, player, opponent)
            }
        }
    }

    private fun resolveOpponentTurn(
        state: RouletteState,
        player: PlayerCharacter,
        opponent: EncounterProfile
    ) {
        viewModelScope.launch {
            delay(420)
            val playerFalls = Random.nextFloat() < 0.45f
            val newHistory = state.history + RouletteTurnResult(RouletteTurn.OPPONENT, playerFalls)
            if (playerFalls) {
                rouletteState.value = state.copy(history = newHistory, resolving = false)
                finalizeDefeat(player, "The rival pulled the lever and you plunged into the spikes.")
            } else {
                rouletteState.value = state.copy(
                    history = newHistory,
                    currentTurn = RouletteTurn.PLAYER,
                    resolving = false
                )
            }
        }
    }

    private fun resolveVictory(
        player: PlayerCharacter,
        opponent: EncounterProfile,
        reward: Int
    ) {
        if (processingState.value) return
        viewModelScope.launch {
            processingState.value = true
            val updated = player
                .recordVictory()
                .gainStatusPoints(reward)
            playerRepository.upsert(updated)
            resultState.value = BattleResult.Victory(
                statusReward = reward,
                newLevel = updated.level,
                newStatusTotal = updated.statusPoints
            )
            rouletteState.value = null
            processingState.value = false
            toastMessage.value = "Victory! Status sigils +$reward"
        }
    }

    private fun finalizeDefeat(
        player: PlayerCharacter,
        message: String
    ) {
        if (processingState.value) return
        viewModelScope.launch {
            processingState.value = true
            val updated = player.recordDefeat()
            playerRepository.upsert(updated)
            resultState.value = BattleResult.Defeat(reason = message)
            rouletteState.value = null
            processingState.value = false
            toastMessage.value = message
        }
    }

    private fun computeComparison(
        player: PlayerCharacter?,
        opponent: EncounterProfile?
    ): StrengthComparison {
        if (player == null || opponent == null) return StrengthComparison.UNDECIDED
        val playerStrength = player.attributes.power
        val opponentStrength = opponent.attributes.power
        return when {
            playerStrength > opponentStrength -> StrengthComparison.PLAYER_ADVANTAGE
            playerStrength < opponentStrength -> StrengthComparison.NPC_ADVANTAGE
            else -> StrengthComparison.TIE
        }
    }

    private fun previewReward(
        comparison: StrengthComparison,
        player: PlayerCharacter?,
        opponent: EncounterProfile?
    ): Int {
        val playerStrength = player?.attributes?.power ?: return 0
        val opponentStrength = opponent?.attributes?.power ?: return 0
        return when (comparison) {
            StrengthComparison.PLAYER_ADVANTAGE -> max(3, (playerStrength - opponentStrength) + 2)
            StrengthComparison.TIE -> max(4, playerStrength / 2 + 2)
            StrengthComparison.NPC_ADVANTAGE -> 0
            StrengthComparison.UNDECIDED -> 0
        }
    }

    fun resetBattle() {
        resultState.value = null
        rouletteState.value = null
    }

    data class BattleUiState(
        val player: PlayerCharacter? = null,
        val opponent: EncounterProfile? = null,
        val comparison: StrengthComparison = StrengthComparison.UNDECIDED,
        val roulette: RouletteState? = null,
        val result: BattleResult? = null,
        val isProcessing: Boolean = false,
        val message: String? = null,
        val statusRewardPreview: Int = 0
    ) {
        val isLoading: Boolean
            get() = player == null || opponent == null
    }

    data class RouletteState(
        val currentTurn: RouletteTurn,
        val history: List<RouletteTurnResult>,
        val resolving: Boolean
    )

    enum class RouletteTurn { PLAYER, OPPONENT }

    data class RouletteTurnResult(
        val turn: RouletteTurn,
        val didFall: Boolean
    )

    sealed interface BattleResult {
        data class Victory(
            val statusReward: Int,
            val newLevel: Int,
            val newStatusTotal: Int
        ) : BattleResult

        data class Defeat(val reason: String) : BattleResult
    }

    enum class StrengthComparison {
        PLAYER_ADVANTAGE,
        NPC_ADVANTAGE,
        TIE,
        UNDECIDED
    }

    companion object {
        fun provideFactory(
            playerRepository: PlayerRepository,
            encounterRepository: EncounterRepository,
            opponentId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BattleViewModel(
                    playerRepository = playerRepository,
                    encounterRepository = encounterRepository,
                    opponentId = opponentId
                ) as T
            }
        }
    }
}
