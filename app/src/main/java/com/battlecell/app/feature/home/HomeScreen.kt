package com.battlecell.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.battlecell.app.R
import com.battlecell.app.domain.model.PlayerCharacter
import com.battlecell.app.domain.model.mission.MissionStatus

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onNavigateToWarCouncil: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(horizontal = 20.dp, vertical = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(id = R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            uiState.character?.let { character ->
                HeroSummaryCard(character = character)
                MissionPreviewCard(
                    missions = uiState.missions,
                    onNavigateToWarCouncil = onNavigateToWarCouncil
                )
                QuestBoardCard()
            }
            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun HeroSummaryCard(character: PlayerCharacter) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.home_level_label, character.level),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn(
                    label = stringResource(id = R.string.home_stat_power),
                    value = character.attributes.power.toString()
                )
                StatColumn(
                    label = stringResource(id = R.string.home_stat_agility),
                    value = character.attributes.agility.toString()
                )
                StatColumn(
                    label = stringResource(id = R.string.home_stat_endurance),
                    value = character.attributes.endurance.toString()
                )
                StatColumn(
                    label = stringResource(id = R.string.home_stat_focus),
                    value = character.attributes.focus.toString()
                )
            }
        }
    }
}

@Composable
private fun MissionPreviewCard(
    missions: List<HomeMissionItem>,
    onNavigateToWarCouncil: () -> Unit
) {
    val prioritized = missions.sortedWith(
        compareBy<HomeMissionItem> { missionStatusPriority(it.status) }
            .thenBy { it.title }
    )
    val showcase = prioritized.take(3)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onNavigateToWarCouncil),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(id = R.string.home_missions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.SportsMartialArts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = stringResource(id = R.string.home_missions_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showcase.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.home_missions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                showcase.forEach { mission ->
                    MissionPreviewRow(mission = mission)
                }
                if (missions.size > showcase.size) {
                    Text(
                        text = stringResource(id = R.string.home_missions_more, missions.size - showcase.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MissionPreviewRow(mission: HomeMissionItem) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mission.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = missionStatusLabel(mission.status),
                style = MaterialTheme.typography.labelMedium,
                color = missionStatusColor(mission.status)
            )
        }
        Text(
            text = mission.condition,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = mission.progressFraction,
            modifier = Modifier.fillMaxWidth(),
            color = missionStatusColor(mission.status),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun QuestBoardCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.home_orders_title),
                style = MaterialTheme.typography.titleMedium
            )
            QuestLine(
                icon = Icons.Default.SportsMartialArts,
                title = stringResource(id = R.string.home_orders_training_title),
                detail = stringResource(id = R.string.home_orders_training_detail)
            )
            QuestLine(
                icon = Icons.Default.BluetoothSearching,
                title = stringResource(id = R.string.home_orders_search_title),
                detail = stringResource(id = R.string.home_orders_search_detail)
            )
            QuestLine(
                icon = Icons.Default.Person,
                title = stringResource(id = R.string.home_orders_profile_title),
                detail = stringResource(id = R.string.home_orders_profile_detail)
            )
        }
    }
}

@Composable
private fun QuestLine(
    icon: ImageVector,
    title: String,
    detail: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun missionStatusPriority(status: MissionStatus): Int = when (status) {
    MissionStatus.ACTIVE -> 0
    MissionStatus.DONE -> 1
    MissionStatus.FAILED -> 2
    MissionStatus.DEACTIVATED -> 3
}

@Composable
private fun missionStatusColor(status: MissionStatus) = when (status) {
    MissionStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    MissionStatus.DONE -> MaterialTheme.colorScheme.tertiary
    MissionStatus.FAILED -> MaterialTheme.colorScheme.error
    MissionStatus.DEACTIVATED -> MaterialTheme.colorScheme.outline
}

@Composable
private fun missionStatusLabel(status: MissionStatus): String = when (status) {
    MissionStatus.ACTIVE -> stringResource(id = R.string.mission_status_active)
    MissionStatus.DONE -> stringResource(id = R.string.mission_status_done)
    MissionStatus.FAILED -> stringResource(id = R.string.mission_status_failed)
    MissionStatus.DEACTIVATED -> stringResource(id = R.string.mission_status_locked)
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
