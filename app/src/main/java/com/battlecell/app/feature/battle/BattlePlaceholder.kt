package com.battlecell.app.feature.battle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battlecell.app.R
import com.battlecell.app.domain.model.EncounterProfile
import com.battlecell.app.domain.model.PlayerCharacter

@Composable
fun BattleRoute(
    viewModel: BattleViewModel,
    onExit: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                uiState.isLoading -> BattleLoading()
                uiState.player == null || uiState.opponent == null -> BattleError(onExit)
                else -> BattleContent(
                    uiState = uiState,
                    onEngage = viewModel::beginEngagement,
                    onPullChains = viewModel::pullChains,
                    onBeginRoulette = viewModel::startRoulette,
                    onReset = viewModel::resetBattle,
                    onExit = onExit
                )
            }
        }
    }
}

@Composable
private fun BattleLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun BattleError(onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onExit,
        confirmButton = {
            TextButton(onClick = onExit) {
                Text(text = "Close")
            }
        },
        title = { Text(text = "Opponent unavailable") },
        text = { Text(text = "We could not find the requested challenger.") }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BattleContent(
    uiState: BattleViewModel.BattleUiState,
    onEngage: () -> Unit,
    onPullChains: () -> Unit,
    onBeginRoulette: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Clash of Banners",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ParticipantCard(
                modifier = Modifier.weight(1f),
                title = "Your Hero",
                character = uiState.player!!,
                isPlayer = true
            )
            ParticipantCard(
                modifier = Modifier.weight(1f),
                title = uiState.opponent!!.displayName,
                character = uiState.opponent,
                isPlayer = false
            )
        }

          EngagementSummary(
              comparison = uiState.comparison,
              rewardPreview = uiState.rewardPreview
          )

        when (val result = uiState.result) {
            is BattleViewModel.BattleResult.Victory -> VictoryBanner(result, onReset, onExit)
            is BattleViewModel.BattleResult.Defeat -> DefeatBanner(result, onReset, onExit)
            null -> when (uiState.comparison) {
                  BattleViewModel.StrengthComparison.PLAYER_ADVANTAGE -> AdvantageActions(onEngage, uiState.isProcessing, uiState.rewardPreview)
                BattleViewModel.StrengthComparison.NPC_ADVANTAGE -> DisadvantageNotice(onExit)
                BattleViewModel.StrengthComparison.TIE -> RouletteSection(
                    rouletteState = uiState.roulette,
                    onBegin = onBeginRoulette,
                    onPullChains = onPullChains,
                    isProcessing = uiState.isProcessing,
                      rewardPreview = uiState.rewardPreview
                )
                BattleViewModel.StrengthComparison.UNDECIDED -> {}
            }
        }

        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text(text = "Return to scouting")
        }
    }
}

@Composable
private fun ParticipantCard(
    modifier: Modifier = Modifier,
    title: String,
    character: PlayerCharacter,
    isPlayer: Boolean
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isPlayer) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Rank ${character.level} • Status ${character.statusPoints}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Power ${character.attributes.power}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Agility ${character.attributes.agility}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ParticipantCard(
    modifier: Modifier = Modifier,
    title: String,
    character: EncounterProfile,
    isPlayer: Boolean
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isPlayer) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Strength ${character.attributes.power}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Endurance ${character.attributes.endurance}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EngagementSummary(
    comparison: BattleViewModel.StrengthComparison,
    rewardPreview: BattleViewModel.BattleRewards
) {
    val payout = rewardSummary(rewardPreview)
    val message = when (comparison) {
        BattleViewModel.StrengthComparison.PLAYER_ADVANTAGE ->
            "Your gauntlet outweighs theirs. Press the attack for $payout."

        BattleViewModel.StrengthComparison.NPC_ADVANTAGE ->
            "The rival's strength eclipses yours. A frontal assault will likely fail."

        BattleViewModel.StrengthComparison.TIE ->
            "Strengths are matched. Invoke the chain duel for a chance at $payout."

        BattleViewModel.StrengthComparison.UNDECIDED -> "Sizing up the challenger..."
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun AdvantageActions(
    onEngage: () -> Unit,
    isProcessing: Boolean,
    rewardPreview: BattleViewModel.BattleRewards
) {
    Button(
        onClick = onEngage,
        enabled = !isProcessing,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = "Overpower for ${rewardSummary(rewardPreview)}")
    }
}

@Composable
private fun DisadvantageNotice(onExit: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Overmatched",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Retreat and gather more power before challenging this banner again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(onClick = onExit) {
                Text(text = "Withdraw")
            }
        }
    }
}

@Composable
private fun RouletteSection(
    rouletteState: BattleViewModel.RouletteState?,
    onBegin: () -> Unit,
    onPullChains: () -> Unit,
    isProcessing: Boolean,
    rewardPreview: BattleViewModel.BattleRewards
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .padding(0.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Chain Duel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Trade pulls on entangled chains. One misstep drops a fighter into the spike pit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider()
                if (rouletteState == null) {
                    Button(
                        onClick = onBegin,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Initiate duel (${rewardSummary(rewardPreview)})")
                    }
                } else {
                    RouletteArena(
                        state = rouletteState,
                        onPullChains = onPullChains,
                        isProcessing = isProcessing
                    )
                }
            }
        }
    }
}

@Composable
private fun RouletteArena(
    state: BattleViewModel.RouletteState,
    onPullChains: () -> Unit,
    isProcessing: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DuelIcon(
                label = "You",
                isActive = state.currentTurn == BattleViewModel.RouletteTurn.PLAYER,
                didFall = state.history.lastOrNull { it.turn == BattleViewModel.RouletteTurn.PLAYER }?.didFall == true
            )
            DuelIcon(
                label = "Rival",
                isActive = state.currentTurn == BattleViewModel.RouletteTurn.OPPONENT,
                didFall = state.history.lastOrNull { it.turn == BattleViewModel.RouletteTurn.OPPONENT }?.didFall == true
            )
        }

        if (state.currentTurn == BattleViewModel.RouletteTurn.PLAYER) {
            Button(
                onClick = onPullChains,
                enabled = !state.resolving && !isProcessing,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.resolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = "Pull the chains")
            }
        } else {
            Text(
                text = "The rival considers their pull...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.history.isNotEmpty()) {
            Text(
                text = "Chain history",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.history) { event ->
                    RouletteHistoryRow(event = event)
                }
            }
        }
    }
}

@Composable
private fun DuelIcon(
    label: String,
    isActive: Boolean,
    didFall: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val color = when {
                didFall -> MaterialTheme.colorScheme.error
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = color,
                shape = CircleShape
            ) {}
            Icon(
                painter = painterResource(id = R.drawable.ic_ruin_chain),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun RouletteHistoryRow(event: BattleViewModel.RouletteTurnResult) {
    val label = if (event.turn == BattleViewModel.RouletteTurn.PLAYER) "You pulled" else "Rival pulled"
    val outcome = if (event.didFall) "Fell" else "Still standing"
    Text(
        text = "$label • $outcome",
        style = MaterialTheme.typography.bodySmall,
        color = if (event.didFall) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun rewardSummary(rewards: BattleViewModel.BattleRewards): String =
    "+${rewards.statusReward} status points • +${rewards.experienceReward} XP"

@Composable
private fun VictoryBanner(
    result: BattleViewModel.BattleResult.Victory,
    onReset: () -> Unit,
    onExit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Victory secured!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Status points +${result.statusReward} • XP +${result.experienceReward}. Rank ${result.newLevel}, total status ${result.newStatusTotal}, total XP ${result.newExperienceTotal}.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Challenge again")
                }
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Return")
                }
            }
        }
    }
}

@Composable
private fun DefeatBanner(
    result: BattleViewModel.BattleResult.Defeat,
    onReset: () -> Unit,
    onExit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Defeat",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = result.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Try again")
                }
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Withdraw")
                }
            }
        }
    }
}
