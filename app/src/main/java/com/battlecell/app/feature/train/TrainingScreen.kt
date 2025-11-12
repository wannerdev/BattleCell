package com.battlecell.app.feature.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battlecell.app.R
import com.battlecell.app.domain.model.Difficulty
import com.battlecell.app.domain.model.PlayerCharacter
import com.battlecell.app.domain.model.TrainingGameDefinition
import com.battlecell.app.domain.model.TrainingScoreEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrainingRoute(
    viewModel: TrainingViewModel,
    onGameSelected: (TrainingGameDefinition) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val highScoreTarget = remember { mutableStateOf<TrainingGameDefinition?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.training_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            uiState.character?.let { character ->
                Text(
                    text = stringResource(
                        id = R.string.training_recommendation,
                        character.attributes.agility + character.attributes.power
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.games, key = { it.id }) { game ->
                    TrainingGameCard(
                        definition = game,
                        onPlay = { onGameSelected(game) },
                        onViewHighScores = { highScoreTarget.value = game }
                    )
                }
            }
            val activeDefinition = highScoreTarget.value
            if (activeDefinition != null) {
                HighScoreDialog(
                    definition = activeDefinition,
                    character = uiState.character,
                    onDismiss = { highScoreTarget.value = null }
                )
            }
        }
    }
}

@Composable
private fun TrainingGameCard(
    definition: TrainingGameDefinition,
    onPlay: () -> Unit,
    onViewHighScores: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                        .padding(12.dp)
                ) {
                    SkillGlyphIcon(
                        attributeType = definition.attributeReward,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = definition.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            id = R.string.training_reward_label,
                            definition.displayReward,
                            definition.attributeReward.name.lowercase().replaceFirstChar { it.titlecase() }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = definition.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(text = stringResource(id = R.string.training_play_button))
                }
                TextButton(onClick = onViewHighScores) {
                    Text(text = stringResource(id = R.string.training_view_high_scores))
                }
            }
        }
    }
}

@Composable
private fun HighScoreDialog(
    definition: TrainingGameDefinition,
    character: PlayerCharacter?,
    onDismiss: () -> Unit
) {
    val scores = character?.trainingHighScores?.get(definition.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.dialog_close))
            }
        },
        title = {
            Text(
                text = stringResource(id = R.string.training_high_scores_title, definition.title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Difficulty.values().forEach { difficulty ->
                    HighScoreRow(
                        difficulty = difficulty,
                        entry = scores?.bestFor(difficulty)
                    )
                }
            }
        }
    )
}

@Composable
private fun HighScoreRow(
    difficulty: Difficulty,
    entry: TrainingScoreEntry?
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = difficulty.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (entry == null || entry.score <= 0) {
            Text(
                text = stringResource(id = R.string.training_high_scores_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val achieved = formatAchieved(entry.achievedAtEpoch)
            Text(
                text = "Score ${entry.score} • ${formatElapsed(entry.elapsedMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (achieved.isNotEmpty()) {
                Text(
                    text = achieved,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatElapsed(elapsedMillis: Long): String =
    String.format(Locale.getDefault(), "%.2fs", elapsedMillis / 1000f)

private fun formatAchieved(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val formatter = SimpleDateFormat("MMM d • HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
